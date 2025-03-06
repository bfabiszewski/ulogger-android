/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger.tasks;

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

import net.fabiszewski.ulogger.Logger;
import net.fabiszewski.ulogger.R;
import net.fabiszewski.ulogger.ui.SettingsActivity;
import net.fabiszewski.ulogger.utils.ImageHelper;

import java.io.IOException;
import java.lang.ref.WeakReference;


/**
 * Task to downsample image
 */
public class ImageTask implements Runnable {

    private static final String TAG = ImageTask.class.getSimpleName();

    private final WeakReference<ImageTaskCallback> weakCallback;

    private String errorMessage = "";
    private final Uri uri;

    private boolean isRunning = false;
    private boolean isCancelled = false;
    private final boolean onlyThumbnail;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    public ImageTask(Uri uri, ImageTaskCallback callback) {
        this(uri, callback, false);
    }

    public ImageTask(Uri uri, ImageTaskCallback callback, boolean onlyThumbnail) {
        this.uri = uri;
        weakCallback = new WeakReference<>(callback);
        this.onlyThumbnail = onlyThumbnail;
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

    @Nullable
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

            if (onlyThumbnail) {
                savedUri = uri;
                thumbnail = ImageHelper.getThumbnail(activity, uri);
            } else {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
                int dstWidth = Integer.parseInt(prefs.getString(SettingsActivity.KEY_IMAGE_SIZE, activity.getString(R.string.pref_imagesize_default)));
                if (dstWidth == 0) {
                    savedUri = uri;
                    ImageHelper.getPersistablePermission(activity, uri);
                    thumbnail = ImageHelper.getThumbnail(activity, uri);
                } else {
                    Bitmap bitmap = ImageHelper.getResampledBitmap(activity, uri, dstWidth);
                    savedUri = ImageHelper.saveToCache(activity, bitmap);
                    thumbnail = ImageHelper.getThumbnail(activity, bitmap);
                    bitmap.recycle();
                }
            }
            result = new ImageTaskResult(savedUri, thumbnail);

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
    private void cleanUp(@Nullable ImageTaskResult result) {
        Activity activity = getActivity();
        if (result != null && activity != null) {
            ImageHelper.clearImageCache(activity.getApplicationContext());
        }
    }

    public interface ImageTaskCallback {
        void onImageTaskCompleted(@NonNull Uri uri, @NonNull Bitmap thumbnail);
        void onImageTaskFailure(@NonNull String error);
        Activity getActivity();
    }

    record ImageTaskResult(Uri savedUri, Bitmap thumbnail) {
            ImageTaskResult(@NonNull Uri savedUri, @NonNull Bitmap thumbnail) {
                this.savedUri = savedUri;
                this.thumbnail = thumbnail;
            }
        }
}
