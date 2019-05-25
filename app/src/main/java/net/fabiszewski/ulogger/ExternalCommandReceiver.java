/*
 * Copyright (c) 2019 Bartek Fabiszewski
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

public class ExternalCommandReceiver extends BroadcastReceiver {

    private static final String START_LOGGER = "start logger";
    private static final String STOP_LOGGER = "stop logger";
    private static final String START_UPLOAD = "start upload";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean allowExternal = prefs.getBoolean(SettingsActivity.KEY_ALLOW_EXTERNAL, false);
        if (!allowExternal) {
            return;
        }
        if (intent != null) {
            String command = intent.getStringExtra("command");
            switch (command) {
                case START_LOGGER:
                    startLoggerService(context);
                    break;
                case STOP_LOGGER:
                    stopLogger(context);
                    break;
                case START_UPLOAD:
                    uploadData(context);
                    break;
            }
        }

    }


    /**
     * Start logger service
     * @param context Context
     */
    private void startLoggerService(Context context) {
        DbAccess db = DbAccess.getInstance();
        db.open(context);
        if (db.getTrackName() == null) {
            db.newAutoTrack();
        }
        db.close();
        Intent intent = new Intent(context, LoggerService.class);
        ContextCompat.startForegroundService(context, intent);
    }

    /**
     * Stop logger service
     * @param context Context
     */
    private void stopLogger(Context context) {
        Intent intent = new Intent(context, LoggerService.class);
        context.stopService(intent);
    }

    /**
     * Start logger service
     * @param context Context
     */
    private void uploadData(Context context) {
        DbAccess db = DbAccess.getInstance();
        db.open(context);
        if (db.needsSync()) {
            Intent intent = new Intent(context, WebSyncService.class);
            context.startService(intent);
        }
        db.close();
    }
}
