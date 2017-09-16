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
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
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

    @Override
    public void onCreate() {
        super.onCreate();
        if (Logger.DEBUG) { Log.d(TAG, "[websync create]"); }

        web = new WebHelper(this);
        db = DbAccess.getInstance();
        db.open(this);
    }

    /**
     * Handle synchronization intent
     * @param intent Intent
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        if (Logger.DEBUG) { Log.d(TAG, "[websync start]"); }

        if (pi != null) {
            // cancel pending alarm
            if (Logger.DEBUG) { Log.d(TAG, "[websync cancel alarm]"); }
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            am.cancel(pi);
            pi = null;
        }

        if (!isAuthorized) {
            try {
                web.authorize();
            } catch (WebAuthException|IOException|JSONException e) {
                handleError(e);
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
                if (Logger.DEBUG) { Log.d(TAG, "[websync io exception: " + e + "]"); }
                // schedule retry
                handleError(e);
            } catch (WebAuthException e) {
                if (Logger.DEBUG) { Log.d(TAG, "[websync auth exception: " + e + "]"); }
                isAuthorized = false;
                try {
                    // reauthorize and retry
                    web.authorize();
                    isAuthorized = true;
                    trackId = web.startTrack(trackName);
                    db.setTrackId(trackId);
                } catch (WebAuthException|IOException|JSONException e2) {
                    // schedule retry
                    handleError(e2);
                }
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
        // suppress as it requires target api 19
        //noinspection TryFinallyCanBeTryWithResources
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
            if (Logger.DEBUG) { Log.d(TAG, "[websync io exception: " + e + "]"); }
            // schedule retry
            handleError(e);
        } catch (WebAuthException e) {
            if (Logger.DEBUG) { Log.d(TAG, "[websync auth exception: " + e + "]"); }
            isAuthorized = false;
            try {
                // reauthorize and retry
                web.authorize();
                isAuthorized = true;
                doSync(trackId);
            } catch (WebAuthException|IOException|JSONException e2) {
                // schedule retry
                handleError(e2);
            }
        } finally {
            cursor.close();
        }
    }

    /**
     * Actions performed in case of synchronization error.
     * Send broadcast to main activity, schedule retry if tracking is on.
     *
     * @param e Exception
     */
    private void handleError(Exception e) {
        String message;
        if (e instanceof UnknownHostException) {
            message = getString(R.string.e_unknown_host, e.getMessage());
        } else if (e instanceof MalformedURLException || e instanceof URISyntaxException) {
            message = getString(R.string.e_bad_url, e.getMessage());
        } else if (e instanceof ConnectException || e instanceof NoRouteToHostException) {
            message = getString(R.string.e_connect, e.getMessage());
        } else {
            message = e.getMessage();
        }
        if (Logger.DEBUG) { Log.d(TAG, "[websync retry: " + message + "]"); }

        db.setError(message);
        Intent intent = new Intent(BROADCAST_SYNC_FAILED);
        intent.putExtra("message", message);
        sendBroadcast(intent);
        // retry only if tracking is on
        if (LoggerService.isRunning()) {
            if (Logger.DEBUG) { Log.d(TAG, "[websync set alarm]"); }
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
        params.put(WebHelper.PARAM_TIME, DbAccess.getTime(cursor));
        params.put(WebHelper.PARAM_LAT, DbAccess.getLatitude(cursor));
        params.put(WebHelper.PARAM_LON, DbAccess.getLongitude(cursor));
        if (DbAccess.hasAltitude(cursor)) {
            params.put(WebHelper.PARAM_ALT, DbAccess.getAltitude(cursor));
        }
        if (DbAccess.hasSpeed(cursor)) {
            params.put(WebHelper.PARAM_SPEED, DbAccess.getSpeed(cursor));
        }
        if (DbAccess.hasBearing(cursor)) {
            params.put(WebHelper.PARAM_BEARING, DbAccess.getBearing(cursor));
        }
        if (DbAccess.hasAccuracy(cursor)) {
            params.put(WebHelper.PARAM_ACCURACY, DbAccess.getAccuracy(cursor));
        }
        params.put(WebHelper.PARAM_PROVIDER, DbAccess.getProvider(cursor));
        return params;
    }

    /**
     * Cleanup
     */
    @Override
    public void onDestroy() {
        if (Logger.DEBUG) { Log.d(TAG, "[websync stop]"); }
        if (db != null) {
            db.close();
        }
        super.onDestroy();
    }

}
