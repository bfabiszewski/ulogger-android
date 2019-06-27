/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.exifinterface.media.ExifInterface;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

class ImageHelper {
    private static final String TAG = ImageHelper.class.getSimpleName();
    private static final String MEDIA_ORIENTATION = "orientation";
    private static final String JPEG_MIME = "image/jpg";


    /**
     * Get orientation
     * First try MediaStore, then Exif
     * @param context Context
     * @param uri Image URI
     * @return Orientation in degrees
     */
    private static int getOrientation(Context context, Uri uri) {
        int orientation = getOrientationMediaStore(context, uri);
        if (orientation == 0) {
            orientation = getOrientationExif(context, uri);
        }
        return orientation;
    }

    /**
     * Get orientation data from EXIF
     * @param context Context
     * @param uri Image URI
     * @return Orientation in degrees
     */
    private static int getOrientationExif(Context context, Uri uri) {
        int orientation = 0;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in != null) {
                ExifInterface exif = new ExifInterface(in);
                int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
                    orientation = 90;
                } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
                    orientation = 180;
                } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
                    orientation = 270;
                }
            }
        } catch (Exception ignored) {}
        if (Logger.DEBUG) { Log.d(TAG, "[getOrientationExif: " + orientation + "]"); }
        return orientation;
    }

    /**
     * Get orientation data from MediaStore
     * @param context Context
     * @param uri Image URI
     * @return Orientation in degrees
     */
    private static int getOrientationMediaStore(Context context, Uri photoUri) {
        int orientation = 0;
        String[] projection = {MEDIA_ORIENTATION};
        try (Cursor cursor = context.getContentResolver().query(photoUri, projection, null, null, null)){
            if (cursor != null && cursor.moveToFirst()) {
                orientation = cursor.getInt(0);
            }
        } catch (Exception ignored) {}
        if (Logger.DEBUG) { Log.d(TAG, "[getOrientationMediaStore: " + orientation + "]"); }
        return orientation;
    }

    /**
     * Get bitmap from URI
     * @param context Context
     * @param uri URI
     * @return Bitmap
     * @throws IOException Exception
     */
    @RequiresApi(api = Build.VERSION_CODES.P)
    @NonNull
    private static Bitmap getBitmap(Context context, Uri uri) throws IOException {
        Bitmap bitmap;
        ImageDecoder.Source src = ImageDecoder.createSource(context.getContentResolver(), uri);
        bitmap = ImageDecoder.decodeBitmap(src);
        return bitmap;
    }
    /**
     * Get bitmap from URI
     * @param context Context
     * @param uri URI
     * @return Bitmap
     * @throws IOException Exception
     */
    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    private static Bitmap getBitmapLegacy(Context context, Uri uri) throws IOException {
        Bitmap bitmap;
        bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
        return bitmap;
    }

    @NonNull
    private static String getFileName() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return "ulogger_" + timeStamp + "_";
    }

    /**
     * Create image uri in media collection
     * @param context Context
     * @return URI
     */
    static Uri createImageUri(Context context) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, getFileName());
        values.put(MediaStore.Images.Media.MIME_TYPE, JPEG_MIME);
        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }
        return context.getContentResolver().insert(collection, values);
    }

    static Bitmap getThumbnail(Context context, Uri uri) throws IOException {
        int sizeDp = (int) context.getResources().getDimension(R.dimen.thumbnail_size);
        int sizePx = sizeDp * (int) Resources.getSystem().getDisplayMetrics().density;

        Bitmap bitmap;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            bitmap = ImageHelper.getBitmapLegacy(context, uri);
        } else {
            bitmap = ImageHelper.getBitmap(context, uri);
        }
        Bitmap thumbBitmap = ThumbnailUtils.extractThumbnail(bitmap, sizePx, sizePx);
        bitmap.recycle();
        thumbBitmap = fixImageOrientation(context, uri, thumbBitmap);
        return thumbBitmap;
    }

    private static Bitmap fixImageOrientation(Context context, Uri uri, Bitmap bitmap) {
        try {
            int orientation = ImageHelper.getOrientation(context, uri);
            if (orientation != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(orientation);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }
        } catch (Exception e) {
            if (Logger.DEBUG) { Log.d(TAG, "[setThumbnail exception: " + e + "]"); }
        }
        return bitmap;
    }
}
