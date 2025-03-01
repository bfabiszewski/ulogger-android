/*
 * Copyright (c) 2024 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class BroadcastHelper {

    /**
     * Send broadcast message
     * @param context Context
     * @param broadcast Broadcast message
     */
    public static void sendBroadcast(@NonNull Context context, @NonNull String broadcast) {
        sendBroadcast(context, broadcast, null);
    }

    /**
     * Send broadcast message with optional extras
     * @param context Context
     * @param broadcast Broadcast message
     * @param extras Extras bundle
     */
    public static void sendBroadcast(@NonNull Context context, @NonNull String broadcast, @Nullable Bundle extras) {
        Intent intent = new Intent(broadcast);
        if (extras != null) {
            intent.putExtras(extras);
        }
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }
}
