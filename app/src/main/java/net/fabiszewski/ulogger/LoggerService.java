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
import android.app.Notification;
import android.app.NotificationChannel;
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
import android.support.annotation.RequiresApi;
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
    public static final String BROADCAST_LOCATION_STARTED = "net.fabiszewski.ulogger.broadcast.location_started";
    public static final String BROADCAST_LOCATION_STOPPED = "net.fabiszewski.ulogger.broadcast.location_stopped";
    public static final String BROADCAST_LOCATION_UPDATED = "net.fabiszewski.ulogger.broadcast.location_updated";
    public static final String BROADCAST_LOCATION_PERMISSION_DENIED = "net.fabiszewski.ulogger.broadcast.location_permission_denied";
    public static final String BROADCAST_LOCATION_NETWORK_DISABLED = "net.fabiszewski.ulogger.broadcast.network_disabled";
    public static final String BROADCAST_LOCATION_GPS_DISABLED = "net.fabiszewski.ulogger.broadcast.gps_disabled";
    public static final String BROADCAST_LOCATION_NETWORK_ENABLED = "net.fabiszewski.ulogger.broadcast.network_enabled";
    public static final String BROADCAST_LOCATION_GPS_ENABLED = "net.fabiszewski.ulogger.broadcast.gps_enabled";
    public static final String BROADCAST_LOCATION_DISABLED = "net.fabiszewski.ulogger.broadcast.location_disabled";
    private boolean liveSync = false;
    private Intent syncIntent;

    private static volatile boolean isRunning = false;
    private LoggerThread thread;
    private Looper looper;
    private LocationManager locManager;
    private LocationListener locListener;
    private DbAccess db;
    private int maxAccuracy;
    private float minDistance;
    private long minTimeMillis;
    // max time tolerance is half min time, but not more that 5 min
    final private long minTimeTolerance = Math.min(minTimeMillis / 2, 5 * 60 * 1000);
    final private long maxTimeMillis = minTimeMillis + minTimeTolerance;

    private static Location lastLocation = null;
    private static volatile long lastUpdateRealtime = 0;

    private final int NOTIFICATION_ID = 1526756640;
    private NotificationManager mNotificationManager;
    private boolean useGps;
    private boolean useNet;

    /**
     * Basic initializations.
     */
    @Override
    public void onCreate() {
        if (Logger.DEBUG) { Log.d(TAG, "[onCreate]"); }

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotificationManager != null) {
            mNotificationManager.cancelAll();
        }

        locManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locListener = new mLocationListener();

        // read user preferences
        updatePreferences();

        boolean hasLocationUpdates = requestLocationUpdates();

        if (hasLocationUpdates) {
            isRunning = true;
            sendBroadcast(BROADCAST_LOCATION_STARTED);

            syncIntent = new Intent(getApplicationContext(), WebSyncService.class);

            thread = new LoggerThread();
            thread.start();
            looper = thread.getLooper();

            db = DbAccess.getInstance();
            db.open(this);

            // start websync service if needed
            if (liveSync && db.needsSync()) {
                startService(syncIntent);
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
        if (Logger.DEBUG) { Log.d(TAG, "[onStartCommand]"); }

        final boolean prefsUpdated = (intent != null) && intent.getBooleanExtra(MainActivity.UPDATED_PREFS, false);
        if (prefsUpdated) {
            handlePrefsUpdated();
        } else if (isRunning) {
            // first start
            final Notification notification = showNotification(NOTIFICATION_ID);
            startForeground(NOTIFICATION_ID, notification);
        } else {
            // onCreate failed to start updates
            stopSelf();
        }

        return START_STICKY;
    }

    /**
     * When user updated preferences, restart location updates, stop service on failure
     */
    private void handlePrefsUpdated() {
        // restart updates
        updatePreferences();
        if (isRunning && !restartUpdates()) {
            // no valid providers after preferences update
            stopSelf();
        }
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
        minTimeMillis = Long.parseLong(prefs.getString("prefMinTime", getString(R.string.pref_mintime_default))) * 1000;
        minDistance = Float.parseFloat(prefs.getString("prefMinDistance", getString(R.string.pref_mindistance_default)));
        maxAccuracy = Integer.parseInt(prefs.getString("prefMinAccuracy", getString(R.string.pref_minaccuracy_default)));
        useGps = prefs.getBoolean("prefUseGps", providerExists(LocationManager.GPS_PROVIDER));
        useNet = prefs.getBoolean("prefUseNet", providerExists(LocationManager.NETWORK_PROVIDER));
        liveSync = prefs.getBoolean("prefLiveSync", false);
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
                //noinspection MissingPermission
                locManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTimeMillis, minDistance, locListener, looper);
                if (locManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    hasLocationUpdates = true;
                    if (Logger.DEBUG) { Log.d(TAG, "[Using net provider]"); }
                }
            }
            if (useGps) {
                //noinspection MissingPermission
                locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTimeMillis, minDistance, locListener, looper);
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
            //noinspection MissingPermission
            locManager.removeUpdates(locListener);
        }
        if (db != null) {
            db.close();
        }

        isRunning = false;

        mNotificationManager.cancel(NOTIFICATION_ID);
        sendBroadcast(BROADCAST_LOCATION_STOPPED);

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
     * Reset realtime of last update
     */
    public static void resetUpdateRealtime() {

        lastUpdateRealtime = 0;
    }

    /**
     * Main service thread class handling location updates.
     */
    private class LoggerThread extends HandlerThread {
        LoggerThread() {
            super("LoggerThread");
        }
        private final String TAG = LoggerThread.class.getSimpleName();

        @Override
        public void interrupt() {
            if (Logger.DEBUG) { Log.d(TAG, "[interrupt]"); }
        }

        @Override
        public void finalize() throws Throwable {
            if (Logger.DEBUG) { Log.d(TAG, "[finalize]"); }
            super.finalize();
        }

        @Override
        public void run() {
            if (Logger.DEBUG) { Log.d(TAG, "[run]"); }
            super.run();
        }
    }

    /**
     * Show notification
     *
     * @param mId Notification Id
     */
    private Notification showNotification(int mId) {
        if (Logger.DEBUG) { Log.d(TAG, "[showNotification " + mId + "]"); }

        final String channelId = String.valueOf(mId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(channelId);
        }
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ic_stat_notify_24dp)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(String.format(getString(R.string.is_running), getString(R.string.app_name)));
        Intent resultIntent = new Intent(this, MainActivity.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);
        Notification mNotification = mBuilder.build();
        mNotificationManager.notify(mId, mNotification);
        return mNotification;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel(String channelId) {
        NotificationChannel chan = new NotificationChannel(channelId, getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH);
        mNotificationManager.createNotificationChannel(chan);
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

                lastLocation = loc;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    lastUpdateRealtime = SystemClock.elapsedRealtime();
                } else {
                    lastUpdateRealtime = loc.getElapsedRealtimeNanos() / 1000000;
                }
                db.writeLocation(loc);
                sendBroadcast(BROADCAST_LOCATION_UPDATED);
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
                if (Logger.DEBUG) { Log.d(TAG, "[location accuracy above limit: " + loc.getAccuracy() + " > " + maxAccuracy + "]"); }
                // reset gps provider to get better accuracy even if time and distance criteria don't change
                if (loc.getProvider().equals(LocationManager.GPS_PROVIDER)) {
                    restartUpdates();
                }
                return true;
            }
            // use network provider only if recent gps data is missing
            if (loc.getProvider().equals(LocationManager.NETWORK_PROVIDER) && lastLocation != null) {
                // we received update from gps provider not later than after maxTime period
                long elapsedMillis = SystemClock.elapsedRealtime() - lastUpdateRealtime;
                if (lastLocation.getProvider().equals(LocationManager.GPS_PROVIDER) && elapsedMillis < maxTimeMillis) {
                    // skip network provider
                    if (Logger.DEBUG) { Log.d(TAG, "[location network provider skipped]"); }
                    return true;
                }
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

        /**
         * Callback on provider status change
         * @param provider Provider
         * @param status Status
         * @param extras Extras
         */
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (Logger.DEBUG) {
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
                if (Logger.DEBUG) { Log.d(TAG, "[location status for " + provider + " changed: " + statusString + "]"); }
            }
        }
    }
}
