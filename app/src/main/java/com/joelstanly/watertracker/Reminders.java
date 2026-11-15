package com.joelstanly.watertracker;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Build;

import java.util.Locale;

/** Decides when the next reminder fires, and shows the notification. */
public class Reminders {
    static final String CHANNEL = "water_reminders";
    static final int NOTIF_ID = 7;

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Water reminders", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Reminds you to drink water through the day");
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }
    }

    private static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(ReminderReceiver.ACTION_REMIND);
        return PendingIntent.getBroadcast(c, 1, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Next reminder = one interval after your last drink (or last reminder),
     * only during waking hours, and none once today's goal is reached.
     */
    static long nextTime(Store s) {
        long now = Store.now();
        long interval = s.intervalMin() * Store.MIN;
        long wake = s.wakeToday();
        long sleep = s.sleepToday();
        long tomorrowFirst = s.at(1, s.wakeMin()) + 30 * Store.MIN;

        if (s.goalReached()) return tomorrowFirst;

        long base = Math.max(Math.max(s.lastDrink(), s.lastReminder()), wake);
        long next = base + interval;
        if (next < now + Store.MIN) next = now + Store.MIN;
        if (next < wake) next = wake + 30 * Store.MIN;
        if (next > sleep - 10 * Store.MIN) return tomorrowFirst;
        return next;
    }

    static long schedule(Context c) {
        Store s = new Store(c);
        long next = nextTime(s);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = alarmIntent(c);
        am.cancel(pi);
        if (canExact(c)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        }
        s.setNextReminder(next);
        return next;
    }

    static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        return c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    static boolean notificationsAllowed(Context c) {
        if (Build.VERSION.SDK_INT < 33) return true;
        return c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static void cancelNotification(Context c) {
        c.getSystemService(NotificationManager.class).cancel(NOTIF_ID);
    }

    static void show(Context c, Store s) {
        if (!notificationsAllowed(c)) return;
        ensureChannel(c);
        long now = Store.now();
        long interval = s.intervalMin() * Store.MIN;
        int today = s.today();
        int goal = s.goal();
        int behind = s.expectedByNow() - today;

        String title;
        if (!s.lastDrinkToday() && today == 0) {
            title = "No water yet today 💧";
        } else {
            long since = now - s.lastDrink();
            if (since > 2 * interval) {
                title = "No water for " + duration(since) + " ⚠️";
            } else {
                title = "Time for a few sips 💧";
            }
        }

        String text;
        if (behind >= 100) {
            text = String.format(Locale.US,
                    "You're %d ml behind schedule (%s of %s). Have a few big sips.",
                    behind, liters(today), liters(goal));
        } else {
            text = String.format(Locale.US,
                    "On track: %s of %s. Keep it going.", liters(today), liters(goal));
        }

        PendingIntent open = PendingIntent.getActivity(c, 2,
                new Intent(c, MainActivity.class).setFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent snooze = PendingIntent.getBroadcast(c, 3,
                new Intent(c, ReminderReceiver.class).setAction(ReminderReceiver.ACTION_SNOOZE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_drop)
                .setColor(0xFF1E88E5)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(c, R.drawable.ic_drop), "Snooze 15 min", snooze).build())
                .build();
        c.getSystemService(NotificationManager.class).notify(NOTIF_ID, n);
    }

    static String liters(int ml) {
        return String.format(Locale.US, "%.2f L", ml / 1000.0);
    }

    static String duration(long ms) {
        long mins = ms / Store.MIN;
        if (mins < 60) return mins + " min";
        long h = mins / 60, m = mins % 60;
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }
}
