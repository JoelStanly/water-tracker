package com.joelstanly.watertracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {
    static final String ACTION_REMIND = "com.joelstanly.watertracker.REMIND";
    static final String ACTION_SNOOZE = "com.joelstanly.watertracker.SNOOZE";

    @Override
    public void onReceive(Context c, Intent intent) {
        Store s = new Store(c);
        long now = Store.now();
        String action = intent.getAction();

        if (ACTION_SNOOZE.equals(action)) {
            Reminders.cancelNotification(c);
            // Pretend the last reminder was just early enough that the next one lands in 15 min.
            s.setLastReminder(now - s.intervalMin() * Store.MIN + 15 * Store.MIN);
        } else if (ACTION_REMIND.equals(action)) {
            boolean awake = now >= s.wakeToday() && now < s.sleepToday();
            if (awake && !s.goalReached()) {
                Reminders.show(c, s);
            }
            s.setLastReminder(now);
        }
        Reminders.schedule(c);
    }
}
