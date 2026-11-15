package com.joelstanly.watertracker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

/** All app data, kept in SharedPreferences on the phone. */
public class Store {
    public static final int BOTTLE_ML = 1000;
    static final long MIN = 60_000L;
    static final long DAY = 24 * 60 * MIN;

    private final SharedPreferences p;

    public Store(Context c) {
        p = c.getApplicationContext().getSharedPreferences("water", Context.MODE_PRIVATE);
        if (!p.contains("installAt")) {
            p.edit().putLong("installAt", now()).apply();
        }
        rollover();
    }

    static long now() {
        return System.currentTimeMillis();
    }

    static String dayKey(long t) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(t));
    }

    /** Starts a new day: saves yesterday's total into history and resets today's numbers. */
    public void rollover() {
        String today = dayKey(now());
        if (!p.contains("day")) {
            p.edit().putString("day", today).apply();
            return;
        }
        String day = p.getString("day", today);
        if (day.equals(today)) return;
        JSONObject h = history();
        try {
            h.put(day, p.getInt("today", 0));
        } catch (JSONException ignored) {
        }
        trim(h);
        p.edit()
                .putString("history", h.toString())
                .putInt("today", 0)
                .putString("log", "[]")
                .putBoolean("workout", false)
                .putString("day", today)
                .apply();
    }

    private void trim(JSONObject h) {
        String oldest = dayKey(now() - 30 * DAY);
        Iterator<String> it = h.keys();
        java.util.List<String> remove = new java.util.ArrayList<>();
        while (it.hasNext()) {
            String k = it.next();
            if (k.compareTo(oldest) < 0) remove.add(k);
        }
        for (String k : remove) h.remove(k);
    }

    private JSONObject history() {
        try {
            return new JSONObject(p.getString("history", "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private JSONArray log() {
        try {
            return new JSONArray(p.getString("log", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    // ---- simple getters / setters ----
    public int level() { return p.getInt("level", BOTTLE_ML); }
    public int today() { return p.getInt("today", 0); }
    public long lastDrink() { return p.getLong("lastDrink", 0); }
    public long lastReminder() { return p.getLong("lastReminder", 0); }
    public long nextReminder() { return p.getLong("nextReminder", 0); }
    public boolean workout() { return p.getBoolean("workout", false); }
    public int goalOverride() { return p.getInt("goalOverride", 0); }
    public int wakeMin() { return p.getInt("wakeMin", 7 * 60 + 30); }
    public int sleepMin() { return p.getInt("sleepMin", 23 * 60); }
    public int intervalMin() { return p.getInt("interval", 60); }
    public boolean askedNotif() { return p.getBoolean("askedNotif", false); }

    public void setLastReminder(long t) { p.edit().putLong("lastReminder", t).apply(); }
    public void setNextReminder(long t) { p.edit().putLong("nextReminder", t).apply(); }
    public void setWorkout(boolean b) { p.edit().putBoolean("workout", b).apply(); }
    public void setAskedNotif() { p.edit().putBoolean("askedNotif", true).apply(); }

    public void saveSettings(int goal, int wake, int sleep, int interval) {
        p.edit()
                .putInt("goalOverride", goal)
                .putInt("wakeMin", wake)
                .putInt("sleepMin", sleep)
                .putInt("interval", interval)
                .apply();
    }

    /** Auto goal: 2.0 L in the first week, 2.5 L after that. +0.5 L on workout days. */
    public int baseGoal() {
        if (goalOverride() > 0) return goalOverride();
        long installAt = p.getLong("installAt", now());
        return (now() - installAt < 7 * DAY) ? 2000 : 2500;
    }

    public int goal() {
        return baseGoal() + (workout() ? 500 : 0);
    }

    public boolean goalReached() {
        return today() >= goal();
    }

    // ---- time helpers ----
    public long at(int dayOffset, int minutesOfDay) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now());
        c.add(Calendar.DAY_OF_YEAR, dayOffset);
        c.set(Calendar.HOUR_OF_DAY, minutesOfDay / 60);
        c.set(Calendar.MINUTE, minutesOfDay % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public long wakeToday() { return at(0, wakeMin()); }
    public long sleepToday() { return at(0, sleepMin()); }

    /** How much you "should" have drunk by now if you spread the goal over your waking hours. */
    public int expectedByNow() {
        long w = wakeToday(), s = sleepToday(), n = now();
        if (n <= w) return 0;
        double frac = Math.min(1.0, (double) (n - w) / (double) (s - w));
        return (int) Math.round(goal() * frac);
    }

    public boolean lastDrinkToday() {
        return lastDrink() > 0 && dayKey(lastDrink()).equals(dayKey(now()));
    }

    // ---- actions ----

    /**
     * You tapped the bottle at a new water level.
     * Lower than before = you drank the difference. Higher = you topped it up.
     * Returns ml drunk (0 if it was a top-up).
     */
    public int drinkTo(int newLevel) {
        rollover();
        newLevel = Math.max(0, Math.min(BOTTLE_ML, newLevel));
        int lvl = level();
        if (newLevel < lvl) {
            int amt = lvl - newLevel;
            record(amt, lvl, newLevel);
            return amt;
        }
        p.edit().putInt("level", newLevel).apply();
        return 0;
    }

    /** Finished whatever was left, then refilled to 1 L. Returns ml drunk. */
    public int finishAndRefill() {
        rollover();
        int lvl = level();
        if (lvl > 0) {
            record(lvl, lvl, BOTTLE_ML);
        } else {
            p.edit().putInt("level", BOTTLE_ML).apply();
        }
        return lvl;
    }

    private void record(int amt, int before, int after) {
        JSONArray log = log();
        try {
            JSONObject e = new JSONObject();
            e.put("t", now());
            e.put("ml", amt);
            e.put("before", before);
            log.put(e);
        } catch (JSONException ignored) {
        }
        p.edit()
                .putInt("level", after)
                .putInt("today", today() + amt)
                .putLong("lastDrink", now())
                .putString("log", log.toString())
                .apply();
    }

    /** Undo the last logged drink. Returns ml removed, or 0 if nothing to undo. */
    public int undo() {
        rollover();
        JSONArray log = log();
        if (log.length() == 0) return 0;
        try {
            JSONObject last = log.getJSONObject(log.length() - 1);
            log.remove(log.length() - 1);
            int ml = last.getInt("ml");
            int before = last.getInt("before");
            long prevDrink = 0;
            if (log.length() > 0) prevDrink = log.getJSONObject(log.length() - 1).getLong("t");
            SharedPreferences.Editor ed = p.edit()
                    .putInt("today", Math.max(0, today() - ml))
                    .putInt("level", before)
                    .putString("log", log.toString());
            if (prevDrink > 0) ed.putLong("lastDrink", prevDrink);
            ed.apply();
            return ml;
        } catch (JSONException e) {
            return 0;
        }
    }

    public int drinksToday() {
        return log().length();
    }

    /** Totals for the last 7 days, oldest first; the last one is today. */
    public int[] last7() {
        JSONObject h = history();
        int[] out = new int[7];
        for (int i = 0; i < 7; i++) {
            int offset = i - 6;
            if (offset == 0) {
                out[i] = today();
            } else {
                out[i] = h.optInt(dayKey(at(offset, 12 * 60)), 0);
            }
        }
        return out;
    }

    public String[] last7Labels() {
        String[] out = new String[7];
        SimpleDateFormat f = new SimpleDateFormat("EEE", Locale.ENGLISH);
        for (int i = 0; i < 7; i++) {
            int offset = i - 6;
            out[i] = offset == 0 ? "Today" : f.format(new Date(at(offset, 12 * 60)));
        }
        return out;
    }
}
