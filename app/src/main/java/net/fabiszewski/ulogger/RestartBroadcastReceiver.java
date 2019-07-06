/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

/**
 * Receiver for app restart broadcast
 */

public class RestartBroadcastReceiver extends BroadcastReceiver {

    private static final String BOOT_COMPLETED = Intent.ACTION_BOOT_COMPLETED;
    private static final String QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON";
    private static final String MY_PACKAGE_REPLACED = Intent.ACTION_MY_PACKAGE_REPLACED;

    /**
     * Broadcast received on system boot completed.
     * Starts background logging service
     *
     * @param context Context
     * @param intent  Intent
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getAction() != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            switch (intent.getAction()) {
                case BOOT_COMPLETED:
                case QUICKBOOT_POWERON:
                    boolean autoStart = prefs.getBoolean(SettingsActivity.KEY_AUTO_START, false);
                    if (autoStart) {
                        startLoggerService(context);
                    }
                    break;
                case MY_PACKAGE_REPLACED:
                    boolean wasRunning = prefs.getBoolean(SettingsActivity.KEY_LOGGER_RUNNING, false);
                    if (wasRunning) {
                        startLoggerService(context);
                    }
                    break;
            }
        }

    }

    /**
     * Start logger service
     * @param context Context
     */
    private void startLoggerService(Context context) {
        DbAccess.newAutoTrack(context);
        Intent intent = new Intent(context, LoggerService.class);
        ContextCompat.startForegroundService(context, intent);
    }
}
