package com.joelstanly.watertracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Puts the reminder back after a phone restart or an app update. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Reminders.schedule(c);
    }
}
