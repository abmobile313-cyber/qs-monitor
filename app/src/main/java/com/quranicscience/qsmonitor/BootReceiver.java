package com.quranicscience.qsmonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Phone restart hone par monitor khud dobara chalu ho jata hai. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (Prefs.get(c).getBoolean(Prefs.KEY_ENABLED, false)) {
            try {
                MonitorService.start(c);
            } catch (Exception ignored) { }
        }
    }
}
