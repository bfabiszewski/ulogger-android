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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import net.fabiszewski.ulogger.db.DbAccess;
import net.fabiszewski.ulogger.services.LoggerService;
import net.fabiszewski.ulogger.ui.SettingsActivity;

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
    public void onReceive(@NonNull Context context, @Nullable Intent intent) {
        if (intent != null && intent.getAction() != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean wasRunning = prefs.getBoolean(SettingsActivity.KEY_LOGGER_RUNNING, false);
            boolean autoStart = prefs.getBoolean(SettingsActivity.KEY_AUTO_START, false);
            switch (intent.getAction()) {
                case BOOT_COMPLETED, QUICKBOOT_POWERON -> {
                    if (autoStart || wasRunning) {
                        startLoggerService(context);
                    }
                }
                case MY_PACKAGE_REPLACED -> {
                    if (wasRunning) {
                        startLoggerService(context);
                    }
                }
            }
        }

    }

    /**
     * Start logger service
     * @param context Context
     */
    private void startLoggerService(@NonNull Context context) {
        DbAccess.newAutoTrack(context);
        Intent intent = new Intent(context, LoggerService.class);
        ContextCompat.startForegroundService(context, intent);
    }
}
