/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger.tasks;

import android.app.Activity;
import android.location.Location;
import android.location.LocationListener;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import net.fabiszewski.ulogger.Logger;
import net.fabiszewski.ulogger.utils.LocationHelper;

import java.lang.ref.WeakReference;


/**
 * Task to get location according to user preferences criteria
 */
public class LoggerTask implements LocationListener, Runnable {

    private static final String TAG = LoggerTask.class.getSimpleName();
    private static final int E_OK = 0;
    public static final int E_PERMISSION = 1;
    public static final int E_DISABLED = 2;
    private static final int TIMEOUT_MS = 30 * 1000;

    private final WeakReference<LoggerTaskCallback> weakCallback;
    private final LocationHelper locationHelper;
    private Location location;
    private volatile boolean waiting;
    private boolean isCancelled = false;
    private boolean isRunning = false;

    private CancellationSignal cancellationSignal = null;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();

    private int error = LocationHelper.LoggerException.E_OK;

    public LoggerTask(@NonNull LoggerTaskCallback callback) {
        weakCallback = new WeakReference<>(callback);
        locationHelper = LocationHelper.getInstance(callback.getActivity());
    }

    @Override
    public void run() {
        if (Logger.DEBUG) { Log.d(TAG, "[task run]"); }
        isRunning = true;
        locationHelper.updatePreferences();
        Location location = doInBackground();
        if (!isCancelled) {
            uiHandler.post(() -> onPostExecute(location));
        }
        isRunning = false;
    }

    public void cancel() {
        if (Logger.DEBUG) { Log.d(TAG, "[task cancelled]"); }
        isCancelled = true;
        quitLoop();
    }

    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Requests location update, waits in loop
     * @return Location or null if none
     */
    @Nullable
    @WorkerThread
    private Location doInBackground() {
        if (Logger.DEBUG) { Log.d(TAG, "[doInBackground]"); }
        Activity activity = getActivity();
        if (activity == null) {
            return null;
        }
        if (!locationHelper.canAccessLocation()) {
            error = E_PERMISSION;
            return null;
        }
        synchronized (lock) {
            waiting = true;
            final long startTime = System.currentTimeMillis();
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cancellationSignal = new CancellationSignal();
                }
                locationHelper.requestSingleUpdate(this, cancellationSignal);
                while (waiting) {
                    try {
                        lock.wait(TIMEOUT_MS);
                    } catch (InterruptedException e) {
                        if (Logger.DEBUG) { Log.d(TAG, "[loop interrupted]"); }
                    }
                    if (System.currentTimeMillis() - startTime >= TIMEOUT_MS) {
                        if (Logger.DEBUG) { Log.d(TAG, "[loop timeout]"); }
                        waiting = false;
                    }
                }
                removeUpdates();
                return location;
            } catch (LocationHelper.LoggerException e) {
                error = e.getCode();
            }
        }
        return null;
    }

    /**
     * Remove updates
     * Uses locationManager or cancellation signal if supported
     */
    private void removeUpdates() {
        locationHelper.removeUpdates(this);
        if (cancellationSignal != null) {
            cancellationSignal.cancel();
        }
    }

    /**
     * Quit loop
     */
    private synchronized void quitLoop() {
        synchronized (lock) {
            waiting = false;
            lock.notifyAll();
        }
    }

    /**
     * Execute callback with location result
     * @param location Location
     */
    @UiThread
    private void onPostExecute(@Nullable Location location) {
        LoggerTaskCallback callback = weakCallback.get();
        if (callback != null && callback.getActivity() != null) {
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

        removeUpdates();
        locationHelper.requestSingleUpdate(this, cancellationSignal);
    }


    @UiThread
    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (Logger.DEBUG) { Log.d(TAG, "[location changed: " + location + "]"); }

        LocationHelper.handleRolloverBug(location);

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
    private boolean hasRequiredAccuracy(@NonNull Location location) {

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

    @UiThread
    @Override
    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    public void onStatusChanged(String provider, int status, Bundle extras) { }

    @UiThread
    @Override
    public void onProviderEnabled(@NonNull String provider) {
        if (Logger.DEBUG) { Log.d(TAG, "[onProviderEnabled: " + provider + "]"); }
        try {
            restartUpdates();
        } catch (LocationHelper.LoggerException e) {
            quitLoop();
        }
    }

    @UiThread
    @Override
    public void onProviderDisabled(@NonNull String provider) {
        if (Logger.DEBUG) { Log.d(TAG, "[onProviderDisabled: " + provider + "]"); }
        try {
            restartUpdates();
        } catch (LocationHelper.LoggerException e) {
            quitLoop();
        }
    }

    public interface LoggerTaskCallback {
        void onLoggerTaskCompleted(@NonNull Location location);
        void onLoggerTaskFailure(int reason);
        Activity getActivity();
    }
}
