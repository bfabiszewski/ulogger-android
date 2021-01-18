/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.lang.ref.WeakReference;

import static net.fabiszewski.ulogger.ImageHelper.clearImageCache;
import static net.fabiszewski.ulogger.ImageHelper.getPersistablePermission;
import static net.fabiszewski.ulogger.ImageHelper.getResampledBitmap;
import static net.fabiszewski.ulogger.ImageHelper.getThumbnail;
import static net.fabiszewski.ulogger.ImageHelper.saveToCache;


/**
 * Task to downsample image
 */
class ImageTask implements Runnable {

    private static final String TAG = ImageTask.class.getSimpleName();

    private final WeakReference<ImageTaskCallback> weakCallback;

    private String errorMessage = "";
    private final Uri uri;

    private boolean isRunning = false;
    private boolean isCancelled = false;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    ImageTask(Uri uri, ImageTaskCallback callback) {
        this.uri = uri;
        weakCallback = new WeakReference<>(callback);
    }

    @Override
    public void run() {
        isRunning = true;
        ImageTaskResult result = doInBackground();
        if (isCancelled) {
            cleanUp(result);
        } else {
            uiHandler.post(() -> onPostExecute(result));
        }
        isRunning = false;
    }

    public void cancel() {
        if (Logger.DEBUG) { Log.d(TAG, "[task cancelled]"); }
        isCancelled = true;
    }

    public boolean isRunning() {
        return isRunning;
    }

    @WorkerThread
    private ImageTaskResult doInBackground() {
        if (Logger.DEBUG) { Log.d(TAG, "[doInBackground]"); }
        Activity activity = getActivity();
        if (activity == null) {
            return null;
        }
        ImageTaskResult result = null;
        try {
            Uri savedUri;
            Bitmap thumbnail;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            int dstWidth = Integer.parseInt(prefs.getString(SettingsActivity.KEY_IMAGE_SIZE, activity.getString(R.string.pref_imagesize_default)));
            if (dstWidth == 0) {
                savedUri = uri;
                getPersistablePermission(activity, uri);
                thumbnail = getThumbnail(activity, uri);
            } else {
                Bitmap bitmap = getResampledBitmap(activity, uri, dstWidth);
                savedUri = saveToCache(activity, bitmap);
                thumbnail = getThumbnail(activity, bitmap);
                bitmap.recycle();
            }
            if (savedUri != null && thumbnail != null) {
                result = new ImageTaskResult(savedUri, thumbnail);
            }
        } catch (IOException e) {
            if (e.getMessage() != null) {
                errorMessage = e.getMessage();
            }
        }
        return result;
    }

    @UiThread
    private void onPostExecute(@Nullable ImageTaskResult result) {
        ImageTaskCallback callback = weakCallback.get();
        if (callback != null && callback.getActivity() != null) {
            if (result == null) {
                callback.onImageTaskFailure(errorMessage);
            } else {
                callback.onImageTaskCompleted(result.savedUri, result.thumbnail);
            }
        }
    }

    @Nullable
    private Activity getActivity() {
        ImageTaskCallback callback = weakCallback.get();
        if (callback != null) {
            return callback.getActivity();
        }
        return null;
    }

    /**
     * Try to clean image cache
     * @param result Task result
     */
    private void cleanUp(ImageTaskResult result) {
        Activity activity = getActivity();
        if (result != null && activity != null) {
            clearImageCache(activity.getApplicationContext());
        }
    }

    interface ImageTaskCallback {
        void onImageTaskCompleted(@NonNull Uri uri, @NonNull Bitmap thumbnail);
        void onImageTaskFailure(@NonNull String error);
        Activity getActivity();
    }

    static class ImageTaskResult {
        final Uri savedUri;
        final Bitmap thumbnail;

        ImageTaskResult(@NonNull Uri savedUri, @NonNull Bitmap thumbnail) {
            this.savedUri = savedUri;
            this.thumbnail = thumbnail;
        }
    }
}
