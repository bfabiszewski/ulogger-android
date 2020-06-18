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
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.lang.ref.WeakReference;

import static net.fabiszewski.ulogger.ImageHelper.getPersistablePermission;
import static net.fabiszewski.ulogger.ImageHelper.getResampledBitmap;
import static net.fabiszewski.ulogger.ImageHelper.getThumbnail;
import static net.fabiszewski.ulogger.ImageHelper.saveToCache;


/**
 * Task to downsample image
 */
class ImageTask extends AsyncTask<Uri, Void, ImageTask.ImageTaskResult> {

    private static final String TAG = ImageTask.class.getSimpleName();

    private final WeakReference<ImageTaskCallback> weakCallback;

    private String errorMessage = "Image resampling failed";

    ImageTask(ImageTaskCallback callback) {
        weakCallback = new WeakReference<>(callback);
    }

    @Override
    protected ImageTaskResult doInBackground(Uri... params) {
        if (Logger.DEBUG) { Log.d(TAG, "[doInBackground]"); }
        Activity activity = getActivity();
        if (activity == null || params.length != 1 || params[0] == null) {
            return null;
        }
        Uri inUri = params[0];
        ImageTaskResult result = null;
        try {
            Uri savedUri;
            Bitmap thumbnail;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            int dstWidth = Integer.parseInt(prefs.getString(SettingsActivity.KEY_IMAGE_SIZE, activity.getString(R.string.pref_imagesize_default)));
            if (dstWidth == 0) {
                savedUri = inUri;
                getPersistablePermission(activity, inUri);
                thumbnail = getThumbnail(activity, inUri);
            } else {
                Bitmap bitmap = getResampledBitmap(activity, inUri, dstWidth);
                savedUri = saveToCache(activity, bitmap);
                thumbnail = getThumbnail(activity, bitmap);
                bitmap.recycle();
            }
            if (savedUri != null && thumbnail != null) {
                result = new ImageTaskResult(savedUri, thumbnail);
            }
        } catch (IOException e) {
            if (e.getMessage() != null) {
                errorMessage += ": " + e.getMessage();
            }
        }
        return result;
    }

    @Override
    protected void onPostExecute(@Nullable ImageTaskResult result) {
        super.onPostExecute(result);
        ImageTaskCallback callback = weakCallback.get();
        if (callback != null) {
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
