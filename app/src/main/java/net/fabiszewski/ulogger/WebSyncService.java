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
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

import org.json.JSONException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import static android.app.PendingIntent.FLAG_ONE_SHOT;

/**
 * Service synchronizing local database positions with remote server.
 *
 */

public class WebSyncService extends JobIntentService {

    private static final String TAG = WebSyncService.class.getSimpleName();
    public static final String BROADCAST_SYNC_FAILED = "net.fabiszewski.ulogger.broadcast.sync_failed";
    public static final String BROADCAST_SYNC_DONE = "net.fabiszewski.ulogger.broadcast.sync_done";

    private DbAccess db;
    private WebHelper web;
    private static PendingIntent pi = null;

    final private static int FIVE_MINUTES = 1000 * 60 * 5;

    static final int JOB_ID = 1001;

    /**
     * Convenience method for enqueuing work in to this service.
     */
    static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, WebSyncService.class, JOB_ID, work);
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
    protected void onHandleWork(@NonNull Intent intent) {
        if (Logger.DEBUG) { Log.d(TAG, "[websync start]"); }

        cancelPending();

        if (!WebHelper.isAuthorized) {
            try {
                web.authorize();
            } catch (WebAuthException|IOException|JSONException e) {
                handleError(e);
                return;
            }
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
     * set up new track on the server and get new id
     * @return Track id
     */
    private int getTrackId() {
        int trackId = db.getTrackId();
        if (trackId == 0) {
            String trackName = db.getTrackName();
            if (trackName == null) {
                handleError(new IllegalStateException("no track"));
                return trackId;
            }
            try {
                trackId = web.startTrack(trackName);
                db.setTrackId(trackId);
            } catch (IOException e) {
                if (Logger.DEBUG) { Log.d(TAG, "[websync io exception: " + e + "]"); }
                // schedule retry
                handleError(e);
            } catch (WebAuthException e) {
                if (Logger.DEBUG) { Log.d(TAG, "[websync auth exception: " + e + "]"); }
                WebHelper.deauthorize();
                try {
                    // reauthorize and retry
                    web.authorize();
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
        try (Cursor cursor = db.getUnsynced()) {
            while (cursor.moveToNext()) {
                int rowId = cursor.getInt(cursor.getColumnIndex(DbContract.Positions._ID));
                Map<String, String> params = cursorToMap(cursor);
                params.put(WebHelper.PARAM_TRACKID, String.valueOf(trackId));
                web.postPosition(params);
                db.setSynced(getApplicationContext(), rowId);
                Intent intent = new Intent(BROADCAST_SYNC_DONE);
                sendBroadcast(intent);
            }
        } catch (IOException e) {
            // handle web errors
            if (Logger.DEBUG) {
                Log.d(TAG, "[websync io exception: " + e + "]");
            }
            // schedule retry
            handleError(e);
        } catch (WebAuthException e) {
            if (Logger.DEBUG) {
                Log.d(TAG, "[websync auth exception: " + e + "]");
            }
            WebHelper.deauthorize();
            try {
                // reauthorize and retry
                web.authorize();
                doSync(trackId);
            } catch (WebAuthException | IOException | JSONException e2) {
                // schedule retry
                handleError(e2);
            }
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
        String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (e instanceof UnknownHostException) {
            message = getString(R.string.e_unknown_host, reason);
        } else if (e instanceof MalformedURLException || e instanceof URISyntaxException) {
            message = getString(R.string.e_bad_url, reason);
        } else if (e instanceof ConnectException || e instanceof NoRouteToHostException || e instanceof SocketTimeoutException) {
            message = getString(R.string.e_connect, reason);
        } else if (e instanceof IllegalStateException) {
            message = getString(R.string.e_illegal_state, reason);
        } else {
            message = reason;
        }
        if (Logger.DEBUG) { Log.d(TAG, "[websync retry: " + message + "]"); }

        db.setError(message);
        Intent intent = new Intent(BROADCAST_SYNC_FAILED);
        intent.putExtra("message", message);
        sendBroadcast(intent);
        // retry only if tracking is on
        if (LoggerService.isRunning()) {
            setPending();
        }
    }

    /**
     * Set pending alarm
     */
    private void setPending() {
        if (Logger.DEBUG) { Log.d(TAG, "[websync set alarm]"); }
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent syncIntent = new Intent(getApplicationContext(), WebSyncService.class);
        pi = PendingIntent.getService(this, 0, syncIntent, FLAG_ONE_SHOT);
        if (am != null) {
            am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + FIVE_MINUTES, pi);
        }
    }

    /**
     * Cancel pending alarm
     */
    private void cancelPending() {
        if (hasPending()) {
            if (Logger.DEBUG) { Log.d(TAG, "[websync cancel alarm]"); }
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                am.cancel(pi);
            }
            pi = null;
        }
    }

    /**
     * Is pending alarm set
     * @return True if has pending alarm set
     */
    private boolean hasPending() {
        return pi != null;
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
        if (DbAccess.hasProvider(cursor)) {
            params.put(WebHelper.PARAM_PROVIDER, DbAccess.getProvider(cursor));
        }
        if (DbAccess.hasComment(cursor)) {
            params.put(WebHelper.PARAM_COMMENT, DbAccess.getComment(cursor));
        }
        if (DbAccess.hasImageUri(cursor)) {
            params.put(WebHelper.PARAM_IMAGE, DbAccess.getImageUri(cursor));
        }
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
