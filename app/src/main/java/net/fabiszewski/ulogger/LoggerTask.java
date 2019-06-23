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
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import java.lang.ref.WeakReference;

class LoggerTask extends AsyncTask<Void, Void, Location> implements LocationListener {

    private static final String TAG = LoggerTask.class.getSimpleName();
    private static final int E_OK = 0;
    static final int E_PERMISSION = 1;
    static final int E_DISABLED = 2;

    private final WeakReference<Activity> weakActivity;
    private LocationManager locManager;
    private Location location;
    private Looper looper;
    private int maxAccuracy;
    private boolean useGps;
    private boolean useNet;

    private int error = E_OK;

    LoggerTask(Activity activity) {
        weakActivity = new WeakReference<>(activity);
        locManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        getPreferences(activity);
        if (!canAccessLocation(activity)) {
            error = E_PERMISSION;
        }
    }

    @Override
    protected Location doInBackground(Void... voids) {
        if (!isCancelled() && error == E_OK) {
            Looper.prepare();
            looper = Looper.myLooper();
            if (requestLocationUpdates()) {
                Looper.loop();
                return location;
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(Location location) {
        super.onPostExecute(location);
        Activity activity = weakActivity.get();
        if (activity instanceof ILoggerTask) {
            if (error == E_OK && location != null) {
                ((ILoggerTask) activity).onLoggerTaskCompleted(location);
            } else {
                ((ILoggerTask) activity).onLoggerTaskFailure(error);
            }
        }
    }

    @Override
    protected void onCancelled() {
        exit();
    }

    /**
     * Reread preferences
     */
    private void getPreferences(Activity activity) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        maxAccuracy = Integer.parseInt(prefs.getString(SettingsActivity.KEY_MIN_ACCURACY, activity.getString(R.string.pref_minaccuracy_default)));
        useGps = prefs.getBoolean(SettingsActivity.KEY_USE_GPS, providerExists(LocationManager.GPS_PROVIDER));
        useNet = prefs.getBoolean(SettingsActivity.KEY_USE_NET, providerExists(LocationManager.NETWORK_PROVIDER));
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
     * Request location updates
     * @return True if succeeded from at least one provider
     */
    private boolean requestLocationUpdates() {
        boolean hasUpdates = false;
        if (useNet) {
            try {
                locManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, this, null);
                if (locManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    hasUpdates = true;
                } else {
                    error |= E_DISABLED;
                }
            } catch (SecurityException e) {
                error |= E_PERMISSION;
            }
        }
        if (useGps) {
            try {
                locManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, null);
                if (locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    hasUpdates = true;
                } else {
                    error |= E_DISABLED;
                }
            } catch (SecurityException e) {
                error |= E_PERMISSION;
            }
        }
        if (hasUpdates) {
            error = E_OK;
        }
        return hasUpdates;
    }

    private void exit() {
        locManager.removeUpdates(this);
        if (looper != null) {
            looper.quit();
        }
    }

    /**
     * Should the location be logged or skipped
     * @param location Location
     * @return True if skipped
     */
    private boolean skipLocation(Location location) {
        // accuracy radius too high
        if (location.hasAccuracy() && location.getAccuracy() > maxAccuracy) {
            if (Logger.DEBUG) { Log.d(TAG, "[location accuracy above limit: " + location.getAccuracy() + " > " + maxAccuracy + "]"); }
            if (!restartUpdates()) {
                exit();
            }
            return true;
        }
        return false;
    }

    /**
     * Restart request for location updates
     *
     * @return True if succeeded, false otherwise (eg. disabled all providers)
     */
    private boolean restartUpdates() {
        if (Logger.DEBUG) { Log.d(TAG, "[location updates restart]"); }

        locManager.removeUpdates(this);
        return requestLocationUpdates();
    }

    /**
     * Check if user granted permission to access location.
     *
     * @param context Context
     * @return True if permission granted, false otherwise
     */
    private static boolean canAccessLocation(Context context) {
        return (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (Logger.DEBUG) { Log.d(TAG, "[location changed: " + location + "]"); }

        if (!skipLocation(location)) {
            this.location = location;
            exit();
        }
    }

    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override
    public void onProviderEnabled(String provider) {
        restartUpdates();
    }

    @Override
    public void onProviderDisabled(String provider) {
        restartUpdates();
    }

    public interface ILoggerTask {
        void onLoggerTaskCompleted(Location location);
        void onLoggerTaskFailure(int reason);
    }
}
