/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;

import org.json.JSONException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static android.app.PendingIntent.FLAG_ONE_SHOT;

/**
 * Service synchronizing local database positions with remote server.
 *
 */

public class WebSyncService extends IntentService {

    private static final String TAG = WebSyncService.class.getSimpleName();
    public static final String BROADCAST_SYNC_FAILED = "net.fabiszewski.ulogger.broadcast.sync_failed";
    public static final String BROADCAST_SYNC_DONE = "net.fabiszewski.ulogger.broadcast.sync_done";

    private DbAccess db;
    private WebHelper web;
    private static boolean isAuthorized = false;
    private static PendingIntent pi = null;

    final private static int FIVE_MINUTES = 1000 * 60 * 5;


    /**
     * Constructor
     */
    public WebSyncService() {
        super("WebSyncService");
    }

    /**
     * Handle synchronization intent
     * @param intent Intent
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "[websync start]");

        if (pi != null) {
            // cancel pending alarm
            Log.d(TAG, "[websync cancel alarm]");
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            am.cancel(pi);
            pi = null;
        }

        web = new WebHelper(this);
        db = DbAccess.getInstance();
        db.open(this);

        if (!isAuthorized) {
            try {
                web.authorize();
            } catch (WebAuthException|IOException|JSONException e) {
                handleError(e.getMessage());
                return;
            }

            isAuthorized = true;
        }

        // get track id
        int trackId = getTrackId();
        if (trackId > 0) {
            doSync(trackId);
        }
    }

    /**
     * Get track id
     * If the track hasn't been registered on server yet,
     * get set up new track on the server and get new id
     * @return Track id
     */
    private int getTrackId() {
        int trackId = db.getTrackId();
        if (trackId == 0) {
            String trackName = db.getTrackName();
            try {
                trackId = web.startTrack(trackName);
                db.setTrackId(trackId);
            } catch (IOException e) {
                Log.d(TAG, "[websync io exception: " + e + "]");
                // schedule retry
                handleError(e.getMessage());
            } catch (WebAuthException e) {
                Log.d(TAG, "[websync auth exception: " + e + "]");
                isAuthorized = false;
                // schedule retry
                handleError(e.getMessage());
            }
        }
        return trackId;
    }

    /**
     * Synchronize all positions in database.
     * Skips already synchronized, uploads new ones
     * @param trackId Current track id
     */
    private void doSync(int trackId) {
        // iterate over positions in db
        Cursor cursor = db.getUnsynced();
        try {
            while (cursor.moveToNext()) {
                int rowId = cursor.getInt(cursor.getColumnIndex(DbContract.Positions._ID));
                Map<String, String> params = cursorToMap(cursor);
                params.put(WebHelper.PARAM_TRACKID, String.valueOf(trackId));
                web.postPosition(params);
                db.setSynced(rowId);
                Intent intent = new Intent(BROADCAST_SYNC_DONE);
                sendBroadcast(intent);
            }
        } catch (IOException e) {
            // handle web errors
            Log.d(TAG, "[websync io exception: " + e + "]");
            // schedule retry
            handleError(e.getMessage());
        } catch (WebAuthException e) {
            Log.d(TAG, "[websync auth exception: " + e + "]");
            isAuthorized = false;
            // schedule retry
            handleError(e.getMessage());
        } finally {
            cursor.close();
        }
    }

    /**
     * Actions performed in case of synchronization error.
     * Send broadcast to main activity, schedule retry if tracking is on.
     *
     * @param message Error message
     */
    private void handleError(String message) {
        Log.d(TAG, "[websync retry: " + message + "]");
        db.setError(message);
        Intent intent = new Intent(BROADCAST_SYNC_FAILED);
        intent.putExtra("message", message);
        sendBroadcast(intent);
        // retry only if tracking is on
        if (LoggerService.isRunning()) {
            Log.d(TAG, "[websync set alarm]");
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent syncIntent = new Intent(getApplicationContext(), WebSyncService.class);
            pi = PendingIntent.getService(this, 0, syncIntent, FLAG_ONE_SHOT);
            am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + FIVE_MINUTES, pi);
        }
    }

    /**
     * Convert cursor to map of request parameters
     *
     * @param cursor Cursor
     * @return Map of parameters
     */
    private Map<String, String> cursorToMap(Cursor cursor) {
        Map<String, String> params = new HashMap<>();
        params.put(WebHelper.PARAM_TIME, cursor.getString(cursor.getColumnIndex(DbContract.Positions.COLUMN_TIME)));
        params.put(WebHelper.PARAM_LAT, cursor.getString(cursor.getColumnIndex(DbContract.Positions.COLUMN_LATITUDE)));
        params.put(WebHelper.PARAM_LON, cursor.getString(cursor.getColumnIndex(DbContract.Positions.COLUMN_LONGITUDE)));
        if (!cursor.isNull(cursor.getColumnIndex(DbContract.Positions.COLUMN_ALTITUDE))) {
            params.put(WebHelper.PARAM_ALT, cursor.getString(cursor.getColumnIndex(DbContract.Positions.COLUMN_ALTITUDE)));
        }
        if (!cursor.isNull(cursor.getColumnIndex(DbContract.Positions.COLUMN_SPEED))) {
            params.put(WebHelper.PARAM_SPEED, cursor.getString(cursor.getColumnIndex(DbContract.Positions.COLUMN_SPEED)));
        }
        if (!cursor.isNull(cursor.getColumnIndex(DbContract.Positions.COLUMN_BEARING))) {
            params.put(WebHelper.PARAM_BEARING, cursor.getString(cursor.getColumnIndex(DbContract.Positions.COLUMN_BEARING)));
        }
        if (!cursor.isNull(cursor.getColumnIndex(DbContract.Positions.COLUMN_ACCURACY))) {
            params.put(WebHelper.PARAM_ACCURACY, cursor.getString(cursor.getColumnIndex(DbContract.Positions.COLUMN_ACCURACY)));
        }
        params.put(WebHelper.PARAM_PROVIDER, cursor.getString(cursor.getColumnIndex(DbContract.Positions.COLUMN_PROVIDER)));
        return params;
    }

    /**
     * Cleanup
     */
    @Override
    public void onDestroy() {
        Log.d(TAG, "[websync stop]");
        if (db != null) {
            db.close();
        }
        super.onDestroy();
    }

}
