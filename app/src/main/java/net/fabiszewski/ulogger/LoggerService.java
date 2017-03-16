/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import static android.location.LocationProvider.AVAILABLE;
import static android.location.LocationProvider.OUT_OF_SERVICE;
import static android.location.LocationProvider.TEMPORARILY_UNAVAILABLE;

/**
 * Background service logging positions to database
 * and synchronizing with remote server.
 *
 */

public class LoggerService extends Service {

    private static final String TAG = LoggerService.class.getSimpleName();
    public static final String BROADCAST_LOCATION_UPDATED = "net.fabiszewski.ulogger.broadcast.location_updated";
    private boolean liveSync = false;
    private Intent syncIntent;

    private static boolean isRunning = false;
    private LoggerThread thread;
    private Looper looper;
    private LocationManager locManager;
    private LocationListener locListener;
    private DbAccess db;
    private SharedPreferences prefs;
    private int maxAccuracy;
    private float minDistance;
    private long minTimeMillis;
    // max time tolerance is half min time, but not more that 5 min
    final private long minTimeTolerance = Math.min(minTimeMillis / 2, 5 * 60 * 1000);
    final private long maxTimeMillis = minTimeMillis + minTimeTolerance;

    private static Location lastLocation = null;
    private static long lastUpdateRealtime = 0;


    /**
     * Basic initializations.
     */
    @Override
    public void onCreate() {
        Log.d(TAG, "[onCreate]");
        thread = new LoggerThread();
        syncIntent = new Intent(getApplicationContext(), WebSyncService.class);

        isRunning = true;

        thread.start();
        looper = thread.getLooper();

        db = DbAccess.getInstance();
        db.open(this);
        locManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locListener = new mLocationListener();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        minTimeMillis = Long.parseLong(prefs.getString("prefMinTime", getString(R.string.pref_mintime_default))) * 1000;
        minDistance = Float.parseFloat(prefs.getString("prefMinDistance", getString(R.string.pref_mindistance_default)));
        maxAccuracy = Integer.parseInt(prefs.getString("prefMinAccuracy", getString(R.string.pref_minaccuracy_default)));

        boolean useGps = prefs.getBoolean("prefUseGps", true);
        boolean useNet = prefs.getBoolean("prefUseNet", true);

        if (canAccessLocation()) {
            if (useNet && providerExists(LocationManager.NETWORK_PROVIDER)) {
                //noinspection MissingPermission
                locManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTimeMillis, minDistance, locListener, looper);
            }
            if (useGps && providerExists(LocationManager.GPS_PROVIDER)) {
                //noinspection MissingPermission
                locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTimeMillis, minDistance, locListener, looper);
            }
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

        Log.d(TAG, "[onStartCommand]");

        // start websync service if needed
        liveSync = prefs.getBoolean("prefLiveSync", false);
        if (liveSync && db.needsSync()) {
            startService(syncIntent);
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

    private boolean providerExists(String provider) {
        return locManager.getAllProviders().contains(provider);
    }

    /**
     * Service cleanup
     */
    @Override
    public void onDestroy() {

        Log.d(TAG, "[onDestroy]");

        if (canAccessLocation()) {
            //noinspection MissingPermission
            locManager.removeUpdates(locListener);
        }
        if (db != null) {
            db.close();
        }

        isRunning = false;

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
     * Return realtime of last update in milliseconds
     *
     * @return Time or zero if not set
     */
    public static long lastUpdateRealtime() {
        return lastUpdateRealtime;
    }

    /**
     * Main service thread class handling location updates.
     */
    private class LoggerThread extends HandlerThread {

        private final String TAG = LoggerThread.class.getSimpleName();
        private final int NOTIFICATION_ID = (int) (System.currentTimeMillis() / 1000L);
        private final NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        LoggerThread() {
            super("LoggerThread");
        }

        @Override
        public void interrupt() {
            Log.d(TAG, "[interrupt]");
            cleanup();
        }

        @Override
        public void finalize() throws Throwable {
            Log.d(TAG, "[finalize]");
            cleanup();
            super.finalize();
        }

        private void cleanup() {
            mNotificationManager.cancel(NOTIFICATION_ID);
        }

        @Override
        public void run() {
            Log.d(TAG, "[run]");
            showNotification(mNotificationManager, NOTIFICATION_ID);
            super.run();
        }
    }

    /**
     * Show notification
     *
     * @param mNotificationManager Notification manager
     * @param mId Notification Id
     */
    private void showNotification(NotificationManager mNotificationManager, int mId) {
        Log.d(TAG, "[showNotification " + mId + "]");

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_stat_notify_24dp)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(String.format(getString(R.string.is_running), getString(R.string.app_name)));
        Intent resultIntent = new Intent(this, MainActivity.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);
        mNotificationManager.notify(mId, mBuilder.build());
    }

    /**
     * Location listener class
     */
    private class mLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location loc) {

            Log.d(TAG, "[location changed: " + loc + "]");

            if (!skipLocation(loc)) {

                lastLocation = loc;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    lastUpdateRealtime = SystemClock.elapsedRealtime();
                } else {
                    lastUpdateRealtime = loc.getElapsedRealtimeNanos() / 1000000;
                }
                db.writeLocation(loc);
                Intent intent = new Intent(BROADCAST_LOCATION_UPDATED);
                sendBroadcast(intent);
                if (liveSync) {
                    startService(syncIntent);
                }
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
                Log.d(TAG, "[location accuracy above limit: " + loc.getAccuracy() + " > " + maxAccuracy + "]");
                // reset gps provider to get better accuracy even if time and distance criteria don't change
                if (loc.getProvider().equals(LocationManager.GPS_PROVIDER)) {
                    restartUpdates(LocationManager.GPS_PROVIDER);
                }
                return true;
            }
            // use network provider only if recent gps data is missing
            if (loc.getProvider().equals(LocationManager.NETWORK_PROVIDER) && lastLocation != null) {
                // we received update from gps provider not later than after maxTime period
                long elapsedMillis = SystemClock.elapsedRealtime() - lastUpdateRealtime;
                if (lastLocation.getProvider().equals(LocationManager.GPS_PROVIDER) && elapsedMillis < maxTimeMillis) {
                    // skip network provider
                    Log.d(TAG, "[location network provider skipped]");
                    return true;
                }
            }
            return false;
        }

        /**
         * Restart request for location updates for given provider
         * @param provider Location provider
         */
        private void restartUpdates(String provider) {

            Log.d(TAG, "[location restart provider " + provider + "]");

            if (providerExists(provider) && canAccessLocation()) {
                //noinspection MissingPermission
                locManager.removeUpdates(locListener);
                //noinspection MissingPermission
                locManager.requestLocationUpdates(provider, minTimeMillis, minDistance, locListener, looper);
            }

        }

        /**
         * Callback on provider disabled
         * @param provider Provider
         */
        @Override
        public void onProviderDisabled(String provider) {
            Log.d(TAG, "[location provider " + provider + " disabled]");
        }

        /**
         * Callback on provider enabled
         * @param provider Provider
         */
        @Override
        public void onProviderEnabled(String provider) {
            Log.d(TAG, "[location provider " + provider + " enabled]");
        }

        /**
         * Callback on provider status change
         * @param provider Provider
         * @param status Status
         * @param extras Extras
         */
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // todo remove debug output
            final String statusString;
            switch (status) {
                case OUT_OF_SERVICE:
                    statusString = "out of service";
                    break;
                case TEMPORARILY_UNAVAILABLE:
                    statusString = "temporarily unavailable";
                    break;
                case AVAILABLE:
                    statusString = "available";
                    break;
                default:
                    statusString = "unknown";
                    break;
            }
            Log.d(TAG, "[location status for " + provider + " changed: " + statusString + "]");
        }
    }
}
