/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger.services;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_ONE_SHOT;
import static android.os.Process.THREAD_PRIORITY_BACKGROUND;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.fabiszewski.ulogger.utils.BroadcastHelper;
import net.fabiszewski.ulogger.db.DbAccess;
import net.fabiszewski.ulogger.db.DbContract;
import net.fabiszewski.ulogger.Logger;
import net.fabiszewski.ulogger.utils.NotificationHelper;
import net.fabiszewski.ulogger.R;
import net.fabiszewski.ulogger.WebAuthException;
import net.fabiszewski.ulogger.utils.WebHelper;

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

/**
 * Service synchronizing local database positions with remote server
 */
public class WebSyncService extends Service {

    private static final String TAG = WebSyncService.class.getSimpleName();
    public static final String BROADCAST_SYNC_FAILED = "net.fabiszewski.ulogger.broadcast.sync_failed";
    public static final String BROADCAST_SYNC_DONE = "net.fabiszewski.ulogger.broadcast.sync_done";

    private HandlerThread thread;
    private ServiceHandler serviceHandler;
    private DbAccess db;
    private WebHelper web;
    private static PendingIntent pi = null;

    final private static int FIVE_MINUTES = 1000 * 60 * 5;

    private NotificationHelper notificationHelper;

    /**
     * Basic initializations
     * Start looper to process uploads
     */
    @Override
    public void onCreate() {
        super.onCreate();
        if (Logger.DEBUG) { Log.d(TAG, "[onCreate]"); }

        web = new WebHelper(this);
        notificationHelper = new NotificationHelper(this, true);

        thread = new HandlerThread("WebSyncThread", THREAD_PRIORITY_BACKGROUND);
        thread.start();
        Looper looper = thread.getLooper();
        if (looper != null) {
            serviceHandler = new ServiceHandler(looper);
        }
        // keep database open during whole service runtime
        db = DbAccess.getInstance();
        db.open(this);
    }

    /**
     * Handler to do synchronization on background thread
     */
    private final class ServiceHandler extends Handler {
        public ServiceHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            cancelPending();

            if (!WebHelper.isAuthorized) {
                try {
                    web.authorize();
                } catch (WebAuthException | IOException | JSONException e) {
                    handleError(e);
                    stopSelf(msg.arg1);
                    return;
                }
            }

            // get track id
            int trackId = getTrackId();
            if (trackId > 0) {
                doSync(trackId);
            }

            stopSelf(msg.arg1);
        }
    }

    /**
     * Start foreground service
     *
     * @param intent Intent
     * @param flags Flags
     * @param startId Unique id
     * @return START_STICKY on success
     */
    @Override
    public int onStartCommand(@NonNull Intent intent, int flags, int startId) {
        if (Logger.DEBUG) { Log.d(TAG, "[onStartCommand]"); }

        if (serviceHandler == null) {
            if (Logger.DEBUG) { Log.d(TAG, "[Give up on serviceHandler not initialized]"); }
            stopSelf();
            return START_NOT_STICKY;
        }
        final Notification notification = notificationHelper.showNotification();
        startForeground(notificationHelper.getId(), notification);

        Message msg = serviceHandler.obtainMessage();
        msg.arg1 = startId;
        serviceHandler.sendMessage(msg);

        return START_STICKY;
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
                if (Logger.DEBUG) { Log.d(TAG, "[getTrackId: io exception: " + e + "]"); }
                // schedule retry
                handleError(e);
            } catch (WebAuthException e) {
                if (Logger.DEBUG) { Log.d(TAG, "[getTrackId: auth exception: " + e + "]"); }
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
        db.resetError();
        // iterate over positions in db
        try (Cursor cursor = db.getUnsynced()) {
            while (cursor.moveToNext()) {
                int rowId = cursor.getInt(cursor.getColumnIndexOrThrow(DbContract.Positions._ID));
                Map<String, String> params = cursorToMap(cursor);
                params.put(WebHelper.PARAM_TRACKID, String.valueOf(trackId));
                web.postPosition(params);
                db.setSynced(getApplicationContext(), rowId);
                BroadcastHelper.sendBroadcast(this, BROADCAST_SYNC_DONE);
            }
        } catch (IOException e) {
            // handle web errors
            if (Logger.DEBUG) {
                Log.d(TAG, "[doSync: io exception: " + e + "]");
            }
            // schedule retry
            handleError(e);
        } catch (WebAuthException e) {
            if (Logger.DEBUG) {
                Log.d(TAG, "[doSync: auth exception: " + e + "]");
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
        if (Logger.DEBUG) { Log.d(TAG, "[handleError: retry: " + message + "]"); }

        db.setError(message);

        Bundle extras = new Bundle();
        extras.putString("message", message);
        BroadcastHelper.sendBroadcast(this, BROADCAST_SYNC_FAILED, extras);

        // retry only if tracking is on
        if (LoggerService.isRunning()) {
            setPending();
        }
    }

    /**
     * Set pending alarm
     */
    private void setPending() {
        if (Logger.DEBUG) { Log.d(TAG, "[setPending alarm]"); }
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent syncIntent = new Intent(getApplicationContext(), WebSyncService.class);
        int flags = FLAG_ONE_SHOT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= FLAG_IMMUTABLE;
        }
        pi = PendingIntent.getService(this, 0, syncIntent, flags);
        if (am != null) {
            am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + FIVE_MINUTES, pi);
        }
    }

    /**
     * Cancel pending alarm
     */
    private void cancelPending() {
        if (hasPending()) {
            if (Logger.DEBUG) { Log.d(TAG, "[cancelPending alarm]"); }
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
        if (Logger.DEBUG) { Log.d(TAG, "[onDestroy]"); }
        if (db != null) {
            db.close();
        }
        notificationHelper.cancelNotification();

        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not implemented");
    }

}
