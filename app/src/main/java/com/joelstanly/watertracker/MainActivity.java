package com.joelstanly.watertracker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private Store s;
    private TextView totalTv, statusTv, lastTv, bannerTv;
    private ProgressBar bar;
    private BottleView bottle;
    private HistoryView history;
    private Switch workoutSw;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 30_000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        s = new Store(this);
        Reminders.ensureChannel(this);
        setContentView(buildUi());
        if (!Reminders.notificationsAllowed(this) && !s.askedNotif()) {
            askNotifications();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        s.rollover();
        Reminders.schedule(this);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        refresh();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String label, boolean primary) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(15);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        if (primary) {
            bg.setColor(0xFF1E88E5);
            btn.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.WHITE);
            bg.setStroke(dp(1.5f), 0xFF1E88E5);
            btn.setTextColor(0xFF1E88E5);
        }
        btn.setBackground(bg);
        btn.setPadding(dp(12), dp(10), dp(12), dp(10));
        return btn;
    }

    private View card(View... children) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(18));
        l.setBackground(bg);
        l.setElevation(dp(2));
        for (View v : children) l.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        l.setLayoutParams(lp);
        return l;
    }

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(24));
        sv.addView(root);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(26, 0xFF0D47A1, true);
        title.setText("Water Tracker");
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button settings = button("Settings", false);
        settings.setOnClickListener(v -> showSettings());
        header.addView(settings);
        root.addView(header);

        // Warning banner when reminders can't work properly
        bannerTv = text(14, 0xFF5D4037, false);
        bannerTv.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bb = new GradientDrawable();
        bb.setColor(0xFFFFE0B2);
        bb.setCornerRadius(dp(12));
        bannerTv.setBackground(bb);
        bannerTv.setOnClickListener(v -> fixReminders());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = dp(12);
        root.addView(bannerTv, blp);

        // Today's progress
        totalTv = text(32, 0xFF0D47A1, true);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(14)));
        statusTv = text(15, 0xFF37474F, false);
        statusTv.setPadding(0, dp(6), 0, 0);
        lastTv = text(13, 0xFF78909C, false);
        lastTv.setPadding(0, dp(4), 0, 0);
        root.addView(card(totalTv, bar, statusTv, lastTv));

        // Bottle
        TextView hint = text(14, 0xFF607D8B, false);
        hint.setText("After drinking, tap where the water is now");
        hint.setGravity(Gravity.CENTER);
        bottle = new BottleView(this);
        bottle.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(360)));
        bottle.setListener(this::onLevelPicked);

        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(10), 0, 0);
        Button undo = button("Undo", false);
        undo.setOnClickListener(v -> {
            int ml = s.undo();
            toast(ml > 0 ? "Removed " + ml + " ml" : "Nothing to undo");
            afterChange();
        });
        Button refill = button("Finished & refilled", true);
        refill.setOnClickListener(v -> {
            int ml = s.finishAndRefill();
            toast(ml > 0 ? "+" + ml + " ml 💧  Bottle refilled" : "Bottle refilled");
            afterChange();
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, -2, 1);
        l1.rightMargin = dp(6);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, -2, 1.6f);
        l2.leftMargin = dp(6);
        row.addView(undo, l1);
        row.addView(refill, l2);
        root.addView(card(hint, bottle, row));

        // Workout toggle
        workoutSw = new Switch(this);
        workoutSw.setText("Workout day (+0.5 L to today's goal)");
        workoutSw.setTextSize(15);
        workoutSw.setTextColor(0xFF37474F);
        root.addView(card(workoutSw));

        // History
        TextView ht = text(15, 0xFF37474F, true);
        ht.setText("Last 7 days (litres)");
        history = new HistoryView(this);
        history.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(150)));
        root.addView(card(ht, history));

        refresh();
        return sv;
    }

    private void onLevelPicked(int ml) {
        int before = s.level();
        int drank = s.drinkTo(ml);
        if (drank > 0) toast("+" + drank + " ml 💧");
        else if (ml > before) toast("Bottle topped up to " + ml + " ml");
        afterChange();
    }

    private void afterChange() {
        Reminders.cancelNotification(this);
        Reminders.schedule(this);
        refresh();
    }

    private void refresh() {
        if (totalTv == null) return;
        s.rollover();
        int today = s.today(), goal = s.goal();
        totalTv.setText(Reminders.liters(today) + " / " + Reminders.liters(goal));
        bar.setProgress(Math.min(1000, today * 1000 / Math.max(1, goal)));

        if (today >= goal) {
            statusTv.setText("Goal reached! 🎉 No more reminders today.");
        } else {
            int behind = s.expectedByNow() - today;
            if (behind >= 100) {
                statusTv.setText("You're " + behind + " ml behind schedule — have a few sips.");
            } else {
                statusTv.setText("On track 👍  " + (goal - today) + " ml to go.");
            }
        }

        StringBuilder sb = new StringBuilder();
        if (s.lastDrinkToday()) {
            sb.append("Last drink: ").append(ago(Store.now() - s.lastDrink()));
        } else {
            sb.append("No drinks logged yet today");
        }
        long next = s.nextReminder();
        if (next > Store.now()) {
            String fmt = Store.dayKey(next).equals(Store.dayKey(Store.now())) ? "HH:mm" : "EEE HH:mm";
            sb.append("  ·  Next reminder: ")
                    .append(new SimpleDateFormat(fmt, Locale.ENGLISH).format(new Date(next)));
        }
        lastTv.setText(sb.toString());

        bottle.setLevel(s.level());

        workoutSw.setOnCheckedChangeListener(null);
        workoutSw.setChecked(s.workout());
        workoutSw.setOnCheckedChangeListener((btn, on) -> {
            s.setWorkout(on);
            afterChange();
        });

        history.setData(s.last7(), s.last7Labels(), goal);

        String problem = reminderProblem();
        if (problem == null) {
            bannerTv.setVisibility(View.GONE);
        } else {
            bannerTv.setVisibility(View.VISIBLE);
            bannerTv.setText("⚠️ " + problem + "  Tap to fix.");
        }
    }

    private String ago(long ms) {
        long mins = ms / Store.MIN;
        if (mins < 1) return "just now";
        return Reminders.duration(ms) + " ago";
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ---- making sure reminders actually arrive ----

    private boolean batteryOptimized() {
        PowerManager pm = getSystemService(PowerManager.class);
        return !pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private String reminderProblem() {
        if (!Reminders.notificationsAllowed(this)) return "Notifications are off, so reminders can't show.";
        if (!Reminders.canExact(this)) return "Reminders may arrive late (alarms permission is off).";
        if (batteryOptimized()) return "Battery saver may delay or block reminders.";
        return null;
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            s.setAskedNotif();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private void fixReminders() {
        String pkg = getPackageName();
        try {
            if (!Reminders.notificationsAllowed(this)) {
                if (!s.askedNotif()) {
                    askNotifications();
                } else {
                    Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, pkg);
                    startActivity(i);
                }
            } else if (!Reminders.canExact(this) && Build.VERSION.SDK_INT >= 31) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + pkg)));
            } else if (batteryOptimized()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + pkg)));
            }
        } catch (ActivityNotFoundException e) {
            toast("Couldn't open that setting on this phone");
        }
    }

    // ---- settings ----

    private static String hhmm(int minutes) {
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private static int parseTime(String t) {
        String[] parts = t.trim().split("[:.]");
        if (parts.length != 2) return -1;
        try {
            int h = Integer.parseInt(parts[0].trim());
            int m = Integer.parseInt(parts[1].trim());
            if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
            return h * 60 + m;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private EditText field(LinearLayout parent, String label, String value, int type) {
        TextView l = text(14, 0xFF37474F, false);
        l.setText(label);
        l.setPadding(0, dp(10), 0, 0);
        parent.addView(l);
        EditText e = new EditText(this);
        e.setText(value);
        e.setInputType(type);
        e.setSingleLine(true);
        parent.addView(e);
        return e;
    }

    private void showSettings() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(6), dp(22), 0);

        int num = InputType.TYPE_CLASS_NUMBER;
        int time = InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME;
        EditText goal = field(form,
                "Daily goal in ml (0 = automatic: 2000 first week, then 2500)",
                String.valueOf(s.goalOverride()), num);
        EditText wake = field(form, "Wake-up time (HH:MM)", hhmm(s.wakeMin()), time);
        EditText sleep = field(form, "Bedtime (HH:MM)", hhmm(s.sleepMin()), time);
        EditText interval = field(form, "Remind me every (minutes)", String.valueOf(s.intervalMin()), num);

        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    int g, iv;
                    try {
                        g = Integer.parseInt(goal.getText().toString().trim());
                        iv = Integer.parseInt(interval.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        toast("Please enter numbers for goal and interval");
                        return;
                    }
                    int wk = parseTime(wake.getText().toString());
                    int sl = parseTime(sleep.getText().toString());
                    if (g != 0 && (g < 500 || g > 6000)) { toast("Goal should be 0 or 500–6000 ml"); return; }
                    if (iv < 15 || iv > 240) { toast("Interval should be 15–240 minutes"); return; }
                    if (wk < 0 || sl < 0) { toast("Times should look like 07:30"); return; }
                    if (sl - wk < 120) { toast("Bedtime must be at least 2 hours after wake-up (same day)"); return; }
                    s.saveSettings(g, wk, sl, iv);
                    toast("Saved");
                    afterChange();
                })
                .show();
    }
}
