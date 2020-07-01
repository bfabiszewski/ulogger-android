/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.app.Activity;
import android.location.Location;
import android.location.LocationListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;


/**
 * Task to get location according to user preferences criteria
 */
class LoggerTask extends AsyncTask<Void, Void, Location> implements LocationListener {

    private static final String TAG = LoggerTask.class.getSimpleName();
    private static final int E_OK = 0;
    static final int E_PERMISSION = 1;
    static final int E_DISABLED = 2;
    private static final int TIMEOUT_MS = 30 * 1000;

    private final WeakReference<LoggerTaskCallback> weakCallback;
    private final LocationHelper locationHelper;
    private Location location;
    private volatile boolean waiting;

    private int error = LocationHelper.LoggerException.E_OK;

    LoggerTask(LoggerTaskCallback callback) {
        weakCallback = new WeakReference<>(callback);
        locationHelper = LocationHelper.getInstance(callback.getActivity());
    }

    @Override
    protected Location doInBackground(Void... voids) {
        if (Logger.DEBUG) { Log.d(TAG, "[doInBackground]"); }
        Activity activity = getActivity();
        if (activity == null) {
            return null;
        }
        if (!locationHelper.canAccessLocation()) {
            error = E_PERMISSION;
            return null;
        }
        try {
            locationHelper.requestSingleUpdate(this, Looper.getMainLooper());
            loop();
            locationHelper.removeUpdates(this);
            return location;
        } catch (LocationHelper.LoggerException e) {
            error = e.getCode();
        }
        return null;
    }

    /**
     * Loop on worker thread
     */
    private void loop() {
        waiting = true;
        final long startTime = System.currentTimeMillis();
        while (waiting) {
            if (System.currentTimeMillis() - startTime > TIMEOUT_MS) {
                if (Logger.DEBUG) { Log.d(TAG, "[loop timeout]"); }
                break;
            }
            if (isCancelled()) {
                if (Logger.DEBUG) { Log.d(TAG, "[loop cancelled]"); }
                break;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                break;
            }
        }
    }

    /**
     * Quit loop
     */
    private void quitLoop() {
        waiting = false;
    }

    @Override
    protected void onPostExecute(Location location) {
        super.onPostExecute(location);
        LoggerTaskCallback callback = weakCallback.get();
        if (callback != null) {
            if (error == E_OK && location != null) {
                callback.onLoggerTaskCompleted(location);
            } else {
                callback.onLoggerTaskFailure(error);
            }
        }
    }


    @Nullable
    private Activity getActivity() {
        LoggerTaskCallback callback = weakCallback.get();
        if (callback != null) {
            return callback.getActivity();
        }
        return null;
    }

    /**
     * Restart request for location updates
     *
     */
    private void restartUpdates() throws LocationHelper.LoggerException {
        if (Logger.DEBUG) { Log.d(TAG, "[location updates restart]"); }

        locationHelper.removeUpdates(this);
        locationHelper.requestSingleUpdate(this, Looper.getMainLooper());
    }


    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (Logger.DEBUG) { Log.d(TAG, "[location changed: " + location + "]"); }
        if (hasRequiredAccuracy(location)) {
            this.location = location;
            quitLoop();
        }
    }


    /**
     * Has received location requested accuracy
     * @param location Location
     * @return True if skipped
     */
    private boolean hasRequiredAccuracy(Location location) {

        if (!locationHelper.hasRequiredAccuracy(location)) {
            try {
                restartUpdates();
            } catch (LocationHelper.LoggerException e) {
                quitLoop();
            }
            return false;
        }
        return true;
    }

    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
        if (Logger.DEBUG) { Log.d(TAG, "[onProviderEnabled: " + provider + "]"); }
        try {
            restartUpdates();
        } catch (LocationHelper.LoggerException e) {
            quitLoop();
        }
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        if (Logger.DEBUG) { Log.d(TAG, "[onProviderDisabled: " + provider + "]"); }
        try {
            restartUpdates();
        } catch (LocationHelper.LoggerException e) {
            quitLoop();
        }
    }

    interface LoggerTaskCallback {
        void onLoggerTaskCompleted(Location location);
        void onLoggerTaskFailure(int reason);
        Activity getActivity();
    }
}
