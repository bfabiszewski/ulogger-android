/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Looper;
import android.os.OperationCanceledException;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class LocationHelper {

    private static final String TAG = LocationHelper.class.getSimpleName();
    private static LocationHelper instance;
    private final Context context;
    private final LocationManager locationManager;

    // millis 1999-08-21T23:59:42+00:00
    private static final long FIRST_ROLLOVER_TIMESTAMP = 935279982000L;
    // millis 2019-04-06T23:59:42+00:00
    private static final long SECOND_ROLLOVER_TIMESTAMP = 1554595182000L;
    // 1024 weeks in milliseconds
    private static final long ROLLOVER_MILLIS = 1024 * 7 * 24 * 60 * 60 * 1000L;

    private boolean liveSync = false;
    private int maxAccuracy;
    private float minDistance;
    private long minTimeMillis;
    private long maxTimeMillis;
    private final List<String> userProviders = new ArrayList<>();

    private final PermissionHelper permissionHelper;


    private LocationHelper(@NonNull Context context) {
        this.context = context.getApplicationContext();
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        permissionHelper = new PermissionHelper(context);
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
        // max time tolerance is half min time, but not more that 5 min
        long minTimeTolerance = Math.min(minTimeMillis / 2, 5 * 60 * 1000);
        maxTimeMillis = minTimeMillis + minTimeTolerance;
        minDistance = Float.parseFloat(prefs.getString(SettingsActivity.KEY_MIN_DISTANCE, context.getString(R.string.pref_mindistance_default)));
        maxAccuracy = Integer.parseInt(prefs.getString(SettingsActivity.KEY_MIN_ACCURACY, context.getString(R.string.pref_minaccuracy_default)));
        userProviders.clear();
        if (prefs.getBoolean(SettingsActivity.KEY_USE_GPS, providerExists(LocationManager.GPS_PROVIDER))) {
            userProviders.add(LocationManager.GPS_PROVIDER);
        }
        if (prefs.getBoolean(SettingsActivity.KEY_USE_NET, providerExists(LocationManager.NETWORK_PROVIDER))) {
            userProviders.add(LocationManager.NETWORK_PROVIDER);
        }
        liveSync = prefs.getBoolean(SettingsActivity.KEY_LIVE_SYNC, false);
    }

    /**
     * Check if user granted permission to access location.
     *
     * @return True if permission granted, false otherwise
     */
    boolean canAccessLocation() {
        return permissionHelper.hasForegroundLocationPermission();
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
     * @param cancellationSignal Cancellation signal
     * @throws LoggerException Exception on permission denied or all providers disabled
     */
    void requestSingleUpdate(@NonNull LocationListener listener, CancellationSignal cancellationSignal) throws LoggerException {
        requestAllProvidersUpdates(listener, Looper.getMainLooper(), true, cancellationSignal);
    }

    /**
     * Request location updates for user selected providers
     * @param listener Listener
     * @param looper Looper
     * @throws LoggerException Exception on all requested providers failure
     */
    void requestLocationUpdates(@NonNull LocationListener listener, @Nullable Looper looper) throws LoggerException {
        requestAllProvidersUpdates(listener, looper, false, null);
    }

    /**
     * Request location updates for user selected providers
     * @param listener Listener
     * @param looper Looper
     * @param singleShot Request single update if true
     * @param cancellationSignal Cancellation signal
     * @throws LoggerException Exception on all requested providers failure
     */
    private void requestAllProvidersUpdates(@NonNull LocationListener listener, @Nullable Looper looper, 
                                            boolean singleShot, CancellationSignal cancellationSignal) throws LoggerException {
        List<Integer> results = new ArrayList<>();
        for (String provider : userProviders) {
            try {
                requestProviderUpdates(provider, listener, looper, singleShot, cancellationSignal);
                results.add(LoggerException.E_OK);
            } catch (LoggerException e) {
                results.add(e.getCode());
            }
        }
        if (!results.contains(LoggerException.E_OK)) {
            int errorCode = results.isEmpty() ? LoggerException.E_DISABLED : results.get(0);
            throw new LoggerException(errorCode);
        }
    }

    /**
     * Request location updates for provider
     * @param provider Provider
     * @param listener Listener
     * @param looper Looper
     * @param singleShot Request single update if true
     * @param cancellationSignal Cancellation signal
     * @throws LoggerException Exception on permission denied or provider disabled
     */
    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    private void requestProviderUpdates(@NonNull String provider, @NonNull LocationListener listener, @Nullable Looper looper,
                                        boolean singleShot, @Nullable CancellationSignal cancellationSignal) throws LoggerException {
        if (Logger.DEBUG) { Log.d(TAG, "[requestProviderUpdates: " + provider + " (" + singleShot + ")]"); }
        try {
            if (!singleShot) {
                // request even if provider is disabled to allow users re-enable it later
                locationManager.requestLocationUpdates(provider, minTimeMillis, minDistance, listener, looper);
            } else if (locationManager.isProviderEnabled(provider)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    locationManager.getCurrentLocation(provider, cancellationSignal, context.getMainExecutor(), location -> {
                        if (Logger.DEBUG) { Log.d(TAG, "[getCurrentLocation location: " + location + ", provider: " + provider + "]"); }
                        if (location != null) {
                            listener.onLocationChanged(location);
                        }
                    });
                } else {
                    locationManager.requestSingleUpdate(provider, listener, looper);
                }
            }
            if (!locationManager.isProviderEnabled(provider)) {
                if (Logger.DEBUG) { Log.d(TAG, "[requestProviderUpdates disabled: " + provider + " (" + singleShot + ")]"); }
                throw new LoggerException("Provider disabled", LoggerException.E_DISABLED);
            }
        } catch (SecurityException e) {
            if (Logger.DEBUG) { Log.d(TAG, "[requestProviderUpdates permission denied: " + provider + " (" + singleShot + ")]"); }
            throw new LoggerException("Permission denied", LoggerException.E_PERMISSION);
        } catch (OperationCanceledException e) {
            if (Logger.DEBUG) { Log.d(TAG, "[requestProviderUpdates operation cancelled: " + provider + " (" + singleShot + ")]"); }
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
     * Is any of user location providers enabled
     * @return True if enabled
     */
    boolean hasEnabledProviders() {
        for (String provider : userProviders) {
            if (locationManager.isProviderEnabled(provider)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check location accuracy meets user criteria
     * @param location Location
     * @return True if location accuracy within limit
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean hasRequiredAccuracy(@NonNull Location location) {
        boolean ret = location.hasAccuracy() && location.getAccuracy() <= maxAccuracy;
        if (Logger.DEBUG) { Log.d(TAG, "[hasRequiredAccuracy: " + ret + "]"); }
        return ret;
    }

    /**
     * Check location distance meets user criteria
     * @param location Current location
     * @param lastLocation Previous location
     * @return True if location distance within limit
     */
    boolean hasRequiredDistance(@NonNull Location location, @Nullable Location lastLocation) {
        if (lastLocation == null) {
            return true;
        }
        float distance = location.distanceTo(lastLocation);
        boolean ret = distance >= minDistance;
        if (Logger.DEBUG) { Log.d(TAG, "[hasRequiredDistance: " + ret + "]"); }
        return ret;
    }

    /**
     * Check location time meets user criteria when compared to current time
     * @param location Location
     * @return True if location time within limit
     */
    boolean hasRequiredTime(@NonNull Location location) {
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
    static boolean isGps(@NonNull Location location) {
        boolean ret = Objects.equals(location.getProvider(), LocationManager.GPS_PROVIDER);
        if (Logger.DEBUG) { Log.d(TAG, "[isGps: " + ret + "]"); }
        return ret;
    }

    /**
     * Is location from Network provider
     * @param location Location
     * @return True if is from Network
     */
    static boolean isNetwork(@NonNull Location location) {
        boolean ret = Objects.equals(location.getProvider(), LocationManager.NETWORK_PROVIDER);
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
     * Fix GPS week count rollover bug if needed
     * <a href="https://galileognss.eu/gps-week-number-rollover-april-6-2019/">https://galileognss.eu/gps-week-number-rollover-april-6-2019/</a>
     * @param location Location
     */
    static void handleRolloverBug(@NonNull Location location) {
        long gpsTime = location.getTime();
        if (gpsTime > FIRST_ROLLOVER_TIMESTAMP && gpsTime < SECOND_ROLLOVER_TIMESTAMP) {
            if (Logger.DEBUG) { Log.d(TAG, "[Fixing GPS rollover bug: " + gpsTime + "]"); }
            location.setTime(gpsTime + ROLLOVER_MILLIS);
        }
    }

    /**
     * Logger exceptions
     */
    static class LoggerException extends Exception {

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
