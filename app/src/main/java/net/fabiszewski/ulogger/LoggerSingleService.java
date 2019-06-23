/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import static net.fabiszewski.ulogger.LoggerService.BROADCAST_LOCATION_DISABLED;
import static net.fabiszewski.ulogger.LoggerService.BROADCAST_LOCATION_GPS_DISABLED;
import static net.fabiszewski.ulogger.LoggerService.BROADCAST_LOCATION_GPS_ENABLED;
import static net.fabiszewski.ulogger.LoggerService.BROADCAST_LOCATION_NETWORK_DISABLED;
import static net.fabiszewski.ulogger.LoggerService.BROADCAST_LOCATION_NETWORK_ENABLED;
import static net.fabiszewski.ulogger.LoggerService.BROADCAST_LOCATION_PERMISSION_DENIED;

/**
 * Background service logging positions to database
 * and synchronizing with remote server.
 *
 */
public class LoggerSingleService extends Service {

    private static final String TAG = LoggerSingleService.class.getSimpleName();
    public static final String BROADCAST_LOCATION_SINGLE_STARTED = "net.fabiszewski.ulogger.broadcast.location_single_started";
    public static final String BROADCAST_LOCATION_SINGLE_UPDATED = "net.fabiszewski.ulogger.broadcast.location_single_updated";
    public static final String BROADCAST_LOCATION_SINGLE_STOPPED = "net.fabiszewski.ulogger.broadcast.location_single_stopped";
    private static final int TIMEOUT_IN_MS = 30 * 1000;
    public static final String EXTRA_LOCATION = "EXTRA_LOCATION";

    private static volatile boolean isRunning = false;
    private HandlerThread thread;
    private Looper looper;
    private LocationManager locManager;
    private LocationListener locListener;
    private int maxAccuracy;

    private boolean useGps;
    private boolean useNet;

    /**
     * Basic initializations.
     */
    @Override
    public void onCreate() {
        if (Logger.DEBUG) { Log.d(TAG, "[onCreate]"); }

        locManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locListener = new mLocationListener();

        // read user preferences
        updatePreferences();

        boolean hasLocationUpdates = requestLocationUpdates();

        if (hasLocationUpdates) {
            setRunning(true);
            sendBroadcast(BROADCAST_LOCATION_SINGLE_STARTED);

            thread = new HandlerThread("LoggerSingleThread");
            thread.start();
            looper = thread.getLooper();
            Handler handler = new Handler(looper);
            handler.postDelayed(this::stopSelf, TIMEOUT_IN_MS);
        }
    }

    /**
     * Start main thread, request location updates, start synchronization.
     *
     * @param intent Intent
     * @param flags Flags
     * @param startId Unique id
     * @return Always returns START_STICKY
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Logger.DEBUG) { Log.d(TAG, "[onStartCommand]"); }

        if (!isRunning) {
            // onCreate failed to start updates
            stopSelf();
        }

        return START_STICKY;
    }

    /**
     * Check if user granted permission to access location.
     *
     * @return True if permission granted, false otherwise
     */
    private boolean canAccessLocation() {
        return (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * Check if given provider exists on device
     * @param provider Provider
     * @return True if exists, false otherwise
     */
    private boolean providerExists(String provider) {
        return locManager.getAllProviders().contains(provider);
    }

    /**
     * Reread preferences
     */
    private void updatePreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        maxAccuracy = Integer.parseInt(prefs.getString(SettingsActivity.KEY_MIN_ACCURACY, getString(R.string.pref_minaccuracy_default)));
        useGps = prefs.getBoolean(SettingsActivity.KEY_USE_GPS, providerExists(LocationManager.GPS_PROVIDER));
        useNet = prefs.getBoolean(SettingsActivity.KEY_USE_NET, providerExists(LocationManager.NETWORK_PROVIDER));
    }

    /**
     * Restart request for location updates
     *
     * @return True if succeeded, false otherwise (eg. disabled all providers)
     */
    private boolean restartUpdates() {
        if (Logger.DEBUG) { Log.d(TAG, "[location updates restart]"); }

        locManager.removeUpdates(locListener);
        return requestLocationUpdates();
    }

    /**
     * Request location updates
     * @return True if succeeded from at least one provider
     */
    @SuppressWarnings({"MissingPermission"})
    private boolean requestLocationUpdates() {
        boolean hasLocationUpdates = false;
        if (canAccessLocation()) {
            if (useNet) {
                locManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locListener, looper);
                if (locManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    hasLocationUpdates = true;
                    if (Logger.DEBUG) { Log.d(TAG, "[Using net provider]"); }
                }
            }
            if (useGps) {
                locManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locListener, looper);
                if (locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    hasLocationUpdates = true;
                    if (Logger.DEBUG) { Log.d(TAG, "[Using gps provider]"); }
                }
            }
            if (!hasLocationUpdates) {
                // no location provider available
                sendBroadcast(BROADCAST_LOCATION_DISABLED);
                if (Logger.DEBUG) { Log.d(TAG, "[No available location updates]"); }
            }
        } else {
            // can't access location
            sendBroadcast(BROADCAST_LOCATION_PERMISSION_DENIED);
            if (Logger.DEBUG) { Log.d(TAG, "[Location permission denied]"); }
        }

        return hasLocationUpdates;
    }

    /**
     * Service cleanup
     */
    @Override
    public void onDestroy() {
        if (Logger.DEBUG) { Log.d(TAG, "[onDestroy]"); }

        if (canAccessLocation()) {
            locManager.removeUpdates(locListener);
        }

        setRunning(false);

        sendBroadcast(BROADCAST_LOCATION_SINGLE_STOPPED);

        if (thread != null) {
            thread.interrupt();
        }
        thread = null;

    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Check if logger service is running.
     *
     * @return True if running, false otherwise
     */
    public static boolean isRunning() {
        return isRunning;
    }


    /**
     * Set service running state
     * @param isRunning True if running, false otherwise
     */
    private void setRunning(boolean isRunning) {
        LoggerSingleService.isRunning = isRunning;
    }

    /**
     * Send broadcast message
     * @param broadcast Broadcast message
     */
    private void sendBroadcast(String broadcast) {
        Intent intent = new Intent(broadcast);
        sendBroadcast(intent);
    }

    /**
     * Location listener class
     */
    private class mLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location loc) {

            if (Logger.DEBUG) { Log.d(TAG, "[location changed: " + loc + "]"); }

            if (!skipLocation(loc)) {
                Intent intent = new Intent(BROADCAST_LOCATION_SINGLE_UPDATED);
                intent.putExtra(EXTRA_LOCATION, loc);
                sendBroadcast(intent);
                stopSelf();
            }
        }

        /**
         * Should the location be logged or skipped
         * @param loc Location
         * @return True if skipped
         */
        private boolean skipLocation(Location loc) {
            // accuracy radius too high
            if (loc.hasAccuracy() && loc.getAccuracy() > maxAccuracy) {
                if (Logger.DEBUG) { Log.d(TAG, "[location accuracy above limit: " + loc.getAccuracy() + " > " + maxAccuracy + "]"); }
                if (!restartUpdates()) {
                    stopSelf();
                }
                return true;
            }
            return false;
        }

        /**
         * Callback on provider disabled
         * @param provider Provider
         */
        @Override
        public void onProviderDisabled(String provider) {
            if (Logger.DEBUG) { Log.d(TAG, "[location provider " + provider + " disabled]"); }
            if (provider.equals(LocationManager.GPS_PROVIDER)) {
                sendBroadcast(BROADCAST_LOCATION_GPS_DISABLED);
            } else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
                sendBroadcast(BROADCAST_LOCATION_NETWORK_DISABLED);
            }
        }

        /**
         * Callback on provider enabled
         * @param provider Provider
         */
        @Override
        public void onProviderEnabled(String provider) {
            if (Logger.DEBUG) { Log.d(TAG, "[location provider " + provider + " enabled]"); }
            if (provider.equals(LocationManager.GPS_PROVIDER)) {
                sendBroadcast(BROADCAST_LOCATION_GPS_ENABLED);
            } else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
                sendBroadcast(BROADCAST_LOCATION_NETWORK_ENABLED);
            }
        }


        @SuppressWarnings({"deprecation", "RedundantSuppression"})
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }
    }
}
