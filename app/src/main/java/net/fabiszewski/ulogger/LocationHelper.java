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
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

class LocationHelper {

    private static final String TAG = LocationHelper.class.getSimpleName();
    private static LocationHelper instance;
    private final Context context;
    private final LocationManager locationManager;

    private boolean liveSync = false;
    private int maxAccuracy;
    private float minDistance;
    private long minTimeMillis;
    // max time tolerance is half min time, but not more that 5 min
    final private long minTimeTolerance = Math.min(minTimeMillis / 2, 5 * 60 * 1000);
    final private long maxTimeMillis = minTimeMillis + minTimeTolerance;
    private boolean useGps;
    private boolean useNet;


    private LocationHelper(@NonNull Context context) {
        this.context = context.getApplicationContext();
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        updatePreferences();
    }

    /**
     * Get instance
     * @param context Context
     * @return LocationHelper instance
     */
    public static LocationHelper getInstance(@NonNull Context context) {
        synchronized(LocationHelper.class) {
            if (instance == null) {
                instance = new LocationHelper(context);
            }
            return instance;
        }
    }

    /**
     * Get preferences
     */
    void updatePreferences() {
        if (Logger.DEBUG) { Log.d(TAG, "[updatePreferences]"); }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        minTimeMillis = Long.parseLong(prefs.getString(SettingsActivity.KEY_MIN_TIME, context.getString(R.string.pref_mintime_default))) * 1000;
        minDistance = Float.parseFloat(prefs.getString(SettingsActivity.KEY_MIN_DISTANCE, context.getString(R.string.pref_mindistance_default)));
        maxAccuracy = Integer.parseInt(prefs.getString(SettingsActivity.KEY_MIN_ACCURACY, context.getString(R.string.pref_minaccuracy_default)));
        useGps = prefs.getBoolean(SettingsActivity.KEY_USE_GPS, providerExists(LocationManager.GPS_PROVIDER));
        useNet = prefs.getBoolean(SettingsActivity.KEY_USE_NET, providerExists(LocationManager.NETWORK_PROVIDER));
        liveSync = prefs.getBoolean(SettingsActivity.KEY_LIVE_SYNC, false);
    }

    /**
     * Check if user granted permission to access location.
     *
     * @param context Context
     * @return True if permission granted, false otherwise
     */
    boolean canAccessLocation() {
        boolean ret = (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
        if (Logger.DEBUG) { Log.d(TAG, "[canAccessLocation: " + ret + "]"); }
        return ret;
    }

    /**
     * Check if given provider exists on device
     * @param provider Provider
     * @return True if exists, false otherwise
     */
    private boolean providerExists(String provider) {
        boolean ret = locationManager.getAllProviders().contains(provider);
        if (Logger.DEBUG) { Log.d(TAG, "[providerExists " + provider + ": " + ret + "]"); }
        return ret;
    }

    /**
     * Request single location update
     * @param listener Listener
     * @param looper Looper
     * @throws LoggerException Exception on permission denied or all providers disabled
     */
    void requestSingleUpdate(@NonNull LocationListener listener, @Nullable Looper looper) throws LoggerException {
        int errorCode = LoggerException.E_DISABLED;
        if (useNet) {
            try {
                requestProviderUpdates(LocationManager.NETWORK_PROVIDER, listener, looper, true);
                errorCode = LoggerException.E_OK;
            } catch (LoggerException e) {
                errorCode = e.getCode();
            }
        }
        if (useGps) {
            try {
                requestProviderUpdates(LocationManager.GPS_PROVIDER, listener, looper, true);
                errorCode = LoggerException.E_OK;
            } catch (LoggerException e) {
                errorCode = e.getCode();
            }
        }
        if (errorCode != LoggerException.E_OK) {
            throw new LoggerException(errorCode);
        }
    }

    /**
     * Request location updates for user selected providers
     * @param listener Listener
     * @param looper Looper
     * @throws LoggerException Exception on permission denied or all providers disabled
     */
    void requestLocationUpdates(@NonNull LocationListener listener, @Nullable Looper looper) throws LoggerException {
        int errorCode = LoggerException.E_DISABLED;
        if (useNet) {
            try {
                requestProviderUpdates(LocationManager.NETWORK_PROVIDER, listener, looper, false);
                errorCode = LoggerException.E_OK;
            } catch (LoggerException e) {
                errorCode = e.getCode();
            }
        }
        if (useGps) {
            try {
                requestProviderUpdates(LocationManager.GPS_PROVIDER, listener, looper, false);
                errorCode = LoggerException.E_OK;
            } catch (LoggerException e) {
                errorCode = e.getCode();
            }
        }
        if (errorCode != LoggerException.E_OK) {
            throw new LoggerException(errorCode);
        }
    }

    /**
     * Request location updates for provider
     * @param provider Provider
     * @param listener Listener
     * @param looper Looper
     * @param singleShot Request single update if true
     * @throws LoggerException Exception on permission denied or all providers disabled
     */
    private void requestProviderUpdates(@NonNull String provider, @NonNull LocationListener listener, @Nullable Looper looper, boolean singleShot) throws LoggerException {
        try {
            if (locationManager.isProviderEnabled(provider)) {
                if (singleShot) {
                    locationManager.requestSingleUpdate(provider, listener, looper);
                } else {
                    locationManager.requestLocationUpdates(provider, minTimeMillis, minDistance, listener, looper);
                }
                if (Logger.DEBUG) { Log.d(TAG, "[requestProviderUpdates success: " + provider + " (" + singleShot + ")]"); }
            } else {
                if (Logger.DEBUG) { Log.d(TAG, "[requestProviderUpdates disabled: " + provider + " (" + singleShot + ")]"); }
                throw new LoggerException("Provider disabled", LoggerException.E_DISABLED);
            }
        } catch (SecurityException e) {
            if (Logger.DEBUG) { Log.d(TAG, "[requestProviderUpdates permission denied: " + provider + " (" + singleShot + ")]"); }
            throw new LoggerException("Permission denied", LoggerException.E_PERMISSION);
        }
    }

    /**
     * Remove all location updates for listener
     * @param listener Listener
     */
    void removeUpdates(LocationListener listener) {
        if (Logger.DEBUG) { Log.d(TAG, "[removeUpdates]"); }
        locationManager.removeUpdates(listener);
    }

    /**
     * Check location accuracy meets user criteria
     * @param location Location
     * @return True if location accuracy within limit
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean hasRequiredAccuracy(Location location) {
        boolean ret = location.hasAccuracy() && location.getAccuracy() <= maxAccuracy;
        if (Logger.DEBUG) { Log.d(TAG, "[hasRequiredAccuracy: " + ret + "]"); }
        return ret;
    }

    /**
     * Check location time meets user criteria when compared to current time
     * @param location Location
     * @return True if location time within limit
     */
    boolean hasRequiredTime(Location location) {
        long elapsedMillis = SystemClock.elapsedRealtime() - location.getElapsedRealtimeNanos() / 1000000;
        boolean ret = elapsedMillis <= maxTimeMillis;
        if (Logger.DEBUG) { Log.d(TAG, "[hasRequiredTime: " + ret + "]"); }
        return ret;
    }

    /**
     * Is location from GPS provider
     * @param location Location
     * @return True if is from GPS
     */
    static boolean isGps(Location location) {
        boolean ret = location.getProvider().equals(LocationManager.GPS_PROVIDER);
        if (Logger.DEBUG) { Log.d(TAG, "[isGps: " + ret + "]"); }
        return ret;
    }

    /**
     * Is location from Network provider
     * @param location Location
     * @return True if is from Network
     */
    static boolean isNetwork(Location location) {
        boolean ret = location.getProvider().equals(LocationManager.NETWORK_PROVIDER);
        if (Logger.DEBUG) { Log.d(TAG, "[isNetwork: " + ret + "]"); }
        return ret;
    }

    /**
     * Is live web synchronization on
     * @return True if on
     */
    boolean isLiveSync() {
        if (Logger.DEBUG) { Log.d(TAG, "[isLiveSync: " + liveSync + "]"); }
        return liveSync;
    }

    /**
     * Logger exceptions
     */
    class LoggerException extends Exception {

        private final int code;

        static final int E_OK = 0;
        static final int E_PERMISSION = 1;
        static final int E_DISABLED = 2;

        LoggerException(int code) {
            super();
            this.code = code;
        }
        
        LoggerException(String message, int code) {
            super(message);
            this.code = code;
        }

        int getCode() {
            return code;
        }
    }


}
