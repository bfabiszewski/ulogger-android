/*
 * Copyright (c) 2021 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger.utils;

import static android.app.PendingIntent.FLAG_IMMUTABLE;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;

import net.fabiszewski.ulogger.Logger;
import net.fabiszewski.ulogger.R;
import net.fabiszewski.ulogger.ui.MainActivity;

public class NotificationHelper {

    private static final String TAG = NotificationHelper.class.getSimpleName();
    private static final int NOTIFICATION_LOGGER_ID = 1;
    private static final int NOTIFICATION_WEB_ID = 2;
    private final int notificationId;
    private final NotificationManagerCompat notificationManager;
    private final Context context;

    /**
     * Constructor
     * On APIs below 26 we must use separate ID for web service notifications,
     * because when web service terminates it also cancels notification for logger service
     * @param ctx Context
     * @param isWebService True for WebSyncService
     */
    public NotificationHelper(Context ctx, boolean isWebService) {
        context = ctx;
        notificationManager = NotificationManagerCompat.from(context.getApplicationContext());
        notificationManager.cancelAll();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && isWebService) {
            notificationId = NOTIFICATION_WEB_ID;
        } else {
            notificationId = NOTIFICATION_LOGGER_ID;
        }
    }

    /**
     * Constructor
     * @param ctx Context
     */
    public NotificationHelper(Context ctx) {
        this(ctx, false);
    }

    /**
     * Show notification
     */
    @NonNull
    public Notification showNotification() {
        if (Logger.DEBUG) { Log.d(TAG, "[showNotification " + notificationId + "]"); }
        int priority = NotificationCompat.PRIORITY_LOW;
        String notificationText = String.format(context.getString(R.string.is_running), context.getString(R.string.app_name));

        if (notificationId != NOTIFICATION_LOGGER_ID) {
            priority = NotificationCompat.PRIORITY_MIN;
            notificationText = String.format(context.getString(R.string.is_uploading), context.getString(R.string.app_name));
        }

        final String channelId = String.valueOf(notificationId);
        createNotificationChannel(channelId);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.drawable.ic_stat_notify_24dp)
                        .setContentTitle(context.getString(R.string.app_name))
                        .setPriority(priority)
                        .setCategory(NotificationCompat.CATEGORY_SERVICE)
                        .setOnlyAlertOnce(true)
                        .setContentText(notificationText);
        Intent resultIntent = new Intent(context, MainActivity.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= FLAG_IMMUTABLE;
        }
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, flags);
        builder.setContentIntent(resultPendingIntent);
        Notification notification = builder.build();
        try {
            notificationManager.notify(notificationId, notification);
        } catch (SecurityException e) {
            if (Logger.DEBUG) { Log.d(TAG, "[notification not allowed: " + e + "]"); }
        }
        return notification;
    }

    /**
     * Create notification channel
     * @param channelId Channel Id
     */
    private void createNotificationChannel(@NonNull String channelId) {
        final int importance = notificationId != NOTIFICATION_LOGGER_ID ? NotificationManagerCompat.IMPORTANCE_NONE : NotificationManagerCompat.IMPORTANCE_LOW;
        NotificationChannelCompat channel = new NotificationChannelCompat.Builder(channelId, importance)
                .setName(context.getString(R.string.app_name))
                .build();
        notificationManager.createNotificationChannel(channel);
    }

    /**
     * Cancel notification
     */
    public void cancelNotification() {
        notificationManager.cancel(notificationId);
    }

    /**
     * Get notification ID
     * @return Notification ID
     */
    public int getId() {
        return notificationId;
    }
}
