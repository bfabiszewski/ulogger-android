/*
 * Copyright (c) 2021 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import static android.app.PendingIntent.FLAG_IMMUTABLE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

class NotificationHelper {

    private static final String TAG = NotificationHelper.class.getSimpleName();
    private final int NOTIFICATION_ID = 1526756640;
    private final NotificationManager notificationManager;
    private final Context context;

    /**
     * Constructor
     * @param ctx Context
     */
    NotificationHelper(Context ctx) {
        context = ctx;
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancelAll();
        }
    }

    /**
     * Show notification
     * @param mId Notification Id
     */
    Notification showNotification() {
        if (Logger.DEBUG) { Log.d(TAG, "[showNotification " + NOTIFICATION_ID + "]"); }

        final String channelId = String.valueOf(NOTIFICATION_ID);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(channelId);
        }
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.drawable.ic_stat_notify_24dp)
                        .setContentTitle(context.getString(R.string.app_name))
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setCategory(NotificationCompat.CATEGORY_SERVICE)
                        .setOnlyAlertOnce(true)
                        .setContentText(String.format(context.getString(R.string.is_running), context.getString(R.string.app_name)));
        Intent resultIntent = new Intent(context, MainActivity.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= FLAG_IMMUTABLE;
        }
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, flags);
        mBuilder.setContentIntent(resultPendingIntent);
        Notification mNotification = mBuilder.build();
        notificationManager.notify(NOTIFICATION_ID, mNotification);
        return mNotification;
    }

    /**
     * Create notification channel
     * @param channelId Channel Id
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel(String channelId) {
        NotificationChannel channel = new NotificationChannel(channelId, context.getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);
    }

    /**
     * Cancel notification
     */
    void cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID);
    }

    /**
     * Get notification ID
     * @return Notification ID
     */
    int getId() {
        return NOTIFICATION_ID;
    }
}
