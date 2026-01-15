/*
 * eirmd
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

/**
 * Small helper to read the current battery percentage without requiring extra permissions.
 * Returns 0..100 on success, or -1 if the value cannot be determined.
 */
public final class BatteryUtil {

    private BatteryUtil() { /* no instances */ }

    /**
     * Returns battery percent (0..100) or -1 if unknown.
     * Use applicationContext where possible to avoid leaking Activities.
     */
    public static int getBatteryPercent(Context context) {
        if (context == null) return -1;

        // Preferred API (fast, no permission)
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            int pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (pct >= 0) return pct;
        }

        // Fallback: sticky ACTION_BATTERY_CHANGED intent
        IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, iFilter);
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) {
                return (int) ((level / (float) scale) * 100);
            }
        }

        return -1;
    }
}
