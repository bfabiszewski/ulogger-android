/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.preference.PreferenceManager;

import static net.fabiszewski.ulogger.LoggerTask.E_DISABLED;
import static net.fabiszewski.ulogger.LoggerTask.E_PERMISSION;
import static net.fabiszewski.ulogger.MainActivity.UPDATED_PREFS;

/**
 * Background service logging positions to database
 * and synchronizing with remote server.
 *
 */

public class LoggerService extends Service {

    private static final String TAG = LoggerService.class.getSimpleName();
    public static final String BROADCAST_LOCATION_DISABLED = "net.fabiszewski.ulogger.broadcast.location_disabled";
    public static final String BROADCAST_LOCATION_GPS_DISABLED = "net.fabiszewski.ulogger.broadcast.gps_disabled";
    public static final String BROADCAST_LOCATION_GPS_ENABLED = "net.fabiszewski.ulogger.broadcast.gps_enabled";
    public static final String BROADCAST_LOCATION_NETWORK_DISABLED = "net.fabiszewski.ulogger.broadcast.network_disabled";
    public static final String BROADCAST_LOCATION_NETWORK_ENABLED = "net.fabiszewski.ulogger.broadcast.network_enabled";
    public static final String BROADCAST_LOCATION_PERMISSION_DENIED = "net.fabiszewski.ulogger.broadcast.location_permission_denied";
    public static final String BROADCAST_LOCATION_STARTED = "net.fabiszewski.ulogger.broadcast.location_started";
    public static final String BROADCAST_LOCATION_STOPPED = "net.fabiszewski.ulogger.broadcast.location_stopped";
    public static final String BROADCAST_LOCATION_UPDATED = "net.fabiszewski.ulogger.broadcast.location_updated";

    private Intent syncIntent;
    private static volatile boolean isRunning = false;
    private HandlerThread thread;
    private Looper looper;
    private LocationHelper locationHelper;
    private LocationListener locationListener;
    private DbAccess db;

    private static Location lastLocation = null;

    private final int NOTIFICATION_ID = 1526756640;
    private NotificationManager notificationManager;

    /**
     * Basic initializations
     * Start location updates, web synchronization
     */
    @Override
    public void onCreate() {
        if (Logger.DEBUG) { Log.d(TAG, "[onCreate]"); }

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancelAll();
        }

        locationHelper = LocationHelper.getInstance(this);
        locationListener = new mLocationListener();

        thread = new HandlerThread("LoggerThread");
        thread.start();
        looper = thread.getLooper();


        // keep database open during whole service runtime
        db = DbAccess.getInstance();
        db.open(this);
    }

    /**
     * Request location updates, start web synchronization if needed
     * @return True on success, false otherwise
     */
    private boolean initializeLocationUpdates() {
        if (Logger.DEBUG) { Log.d(TAG, "[initializeLocationUpdates]"); }
        try {
            locationHelper.updatePreferences();
            locationHelper.requestLocationUpdates(locationListener, looper);
            setRunning(true);
            sendBroadcast(BROADCAST_LOCATION_STARTED);

            syncIntent = new Intent(getApplicationContext(), WebSyncService.class);

            if (locationHelper.isLiveSync() && DbAccess.needsSync(this)) {
                WebSyncService.enqueueWork(getApplicationContext(), syncIntent);
            }
            return true;
        } catch (LocationHelper.LoggerException e) {
            int errorCode = e.getCode();
            if (errorCode == E_DISABLED) {
                sendBroadcast(BROADCAST_LOCATION_DISABLED);
            } else if (errorCode == E_PERMISSION) {
                sendBroadcast(BROADCAST_LOCATION_PERMISSION_DENIED);
            }
        }
        return false;
    }

    /**
     * Start foreground service
     *
     * @param intent Intent
     * @param flags Flags
     * @param startId Unique id
     * @return Always returns START_STICKY
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Logger.DEBUG) { Log.d(TAG, "[onStartCommand]"); }

        if (intent != null && intent.getBooleanExtra(UPDATED_PREFS, false)) {
            handlePrefsUpdated();
        } else {
            final Notification notification = showNotification(NOTIFICATION_ID);
            startForeground(NOTIFICATION_ID, notification);
            if (!initializeLocationUpdates()) {
                setRunning(false);
                stopSelf();
            }
        }

        return START_STICKY;
    }

    /**
     * When user updated preferences, restart location updates, stop service on failure
     */
    private void handlePrefsUpdated() {
        locationHelper.updatePreferences();
        if (isRunning) {
            try {
                restartUpdates();
            } catch (LocationHelper.LoggerException e) {
                // no valid providers after preferences update
                stopSelf();
            }
        }
    }

    /**
     * Restart request for location updates
     * @throws LocationHelper.LoggerException Exception
     */
    private void restartUpdates() throws LocationHelper.LoggerException {
        if (Logger.DEBUG) { Log.d(TAG, "[location updates restart]"); }
        locationHelper.removeUpdates(locationListener);
        locationHelper.requestLocationUpdates(locationListener, looper);
    }

    /**
     * Service cleanup
     */
    @Override
    public void onDestroy() {
        if (Logger.DEBUG) { Log.d(TAG, "[onDestroy]"); }

        if (locationHelper.canAccessLocation()) {
            locationHelper.removeUpdates(locationListener);
        }
        if (db != null) {
            db.close();
        }

        setRunning(false);

        notificationManager.cancel(NOTIFICATION_ID);
        sendBroadcast(BROADCAST_LOCATION_STOPPED);

        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
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
        LoggerService.isRunning = isRunning;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(SettingsActivity.KEY_LOGGER_RUNNING, isRunning);
        editor.apply();
    }

    /**
     * Return realtime of last update in milliseconds
     * @return Time or zero if not set
     */
    public static long lastUpdateRealtime() {
        if (lastLocation != null) {
            return lastLocation.getElapsedRealtimeNanos() / 1000000;
        }
        return 0;
    }

    /**
     * Reset last location update
     */
    public static void resetLastLocation() {
        lastLocation = null;
    }

    /**
     * Show notification
     * @param mId Notification Id
     */
    @SuppressWarnings("SameParameterValue")
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
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_SERVICE)
                        .setContentText(String.format(getString(R.string.is_running), getString(R.string.app_name)));
        Intent resultIntent = new Intent(this, MainActivity.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);
        Notification mNotification = mBuilder.build();
        notificationManager.notify(mId, mNotification);
        return mNotification;
    }

    /**
     * Create notification channel
     * @param channelId Channel Id
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel(String channelId) {
        NotificationChannel chan = new NotificationChannel(channelId, getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH);
        notificationManager.createNotificationChannel(chan);
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

        /**
         * Callback on location update
         * @param location Location
         */
        @Override
        public void onLocationChanged(@NonNull Location location) {
            if (Logger.DEBUG) { Log.d(TAG, "[location changed: " + location + "]"); }

            LocationHelper.handleRolloverBug(location);

            if (meetsCriteria(location)) {
                lastLocation = location;
                DbAccess.writeLocation(LoggerService.this, location);
                sendBroadcast(BROADCAST_LOCATION_UPDATED);
                if (locationHelper.isLiveSync()) {
                    WebSyncService.enqueueWork(getApplicationContext(), syncIntent);
                }
            }
        }

        /**
         * Does location meets user criteria
         * @param location Location
         * @return True if matches
         */
        private boolean meetsCriteria(Location location) {
            // skip if distance is below user criterion
            if (!locationHelper.hasRequiredDistance(location, lastLocation)) {
                return false;
            }
            // accuracy radius too high
            if (!locationHelper.hasRequiredAccuracy(location)) {
                if (Logger.DEBUG) { Log.d(TAG, "[location accuracy above limit: " + location.getAccuracy() + "]"); }
                // reset gps provider to get better accuracy even if time and distance criteria don't change
                if (LocationHelper.isGps(location)) {
                    try {
                        restartUpdates();
                    } catch (LocationHelper.LoggerException e) {
                        e.printStackTrace();
                    }
                }
                return false;
            }
            // use network provider only if recent gps data is missing
            if (LocationHelper.isNetwork(location) && lastLocation != null) {
                // we received update from gps provider not later than maxTime period
                if (LocationHelper.isGps(lastLocation) && locationHelper.hasRequiredTime(lastLocation)) {
                    // skip network provider
                    if (Logger.DEBUG) { Log.d(TAG, "[location network provider skipped]"); }
                    return false;
                }
            }
            return true;
        }

        /**
         * Callback on provider disabled
         * @param provider Provider
         */
        @Override
        public void onProviderDisabled(@NonNull String provider) {
            if (Logger.DEBUG) { Log.d(TAG, "[location provider " + provider + " disabled]"); }
            if (provider.equals(LocationManager.GPS_PROVIDER)) {
                sendBroadcast(BROADCAST_LOCATION_GPS_DISABLED);
            } else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
                sendBroadcast(BROADCAST_LOCATION_NETWORK_DISABLED);
            }
            if (!locationHelper.hasEnabledProviders()) {
                sendBroadcast(BROADCAST_LOCATION_DISABLED);
            }
        }

        /**
         * Callback on provider enabled
         * @param provider Provider
         */
        @Override
        public void onProviderEnabled(@NonNull String provider) {
            if (Logger.DEBUG) { Log.d(TAG, "[location provider " + provider + " enabled]"); }
            if (provider.equals(LocationManager.GPS_PROVIDER)) {
                sendBroadcast(BROADCAST_LOCATION_GPS_ENABLED);
            } else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
                sendBroadcast(BROADCAST_LOCATION_NETWORK_ENABLED);
            }
        }


        @SuppressWarnings({"deprecation", "RedundantSuppression"})
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) { }
    }
}
