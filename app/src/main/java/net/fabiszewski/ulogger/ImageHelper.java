/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

class ImageHelper {
    private static final String TAG = ImageHelper.class.getSimpleName();
    private static final String MEDIA_ORIENTATION = "orientation";
    private static final String JPEG_MIME = "image/jpg";
    private static final int ORIENTATION_90 = 90;
    private static final int ORIENTATION_180 = 180;
    private static final int ORIENTATION_270 = 270;
    private static final int ORIENTATION_NORMAL = 0;


    /**
     * Get orientation
     * First try MediaStore, then Exif
     *
     * @param context Context
     * @param uri     Image URI
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
     *
     * @param context Context
     * @param uri     Image URI
     * @return Orientation in degrees
     */
    private static int getOrientationExif(Context context, Uri uri) {
        int orientation = ORIENTATION_NORMAL;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in != null) {
                ExifInterface exif = new ExifInterface(in);
                int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
                    orientation = ORIENTATION_90;
                } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
                    orientation = ORIENTATION_180;
                } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
                    orientation = ORIENTATION_270;
                }
            }
        } catch (Exception ignored) {
        }
        if (Logger.DEBUG) { Log.d(TAG, "[getOrientationExif: " + orientation + "]"); }
        return orientation;
    }

    /**
     * Get orientation data from MediaStore
     *
     * @param context Context
     * @param uri     Image URI
     * @return Orientation in degrees
     */
    private static int getOrientationMediaStore(Context context, Uri photoUri) {
        int orientation = 0;
        String[] projection = {MEDIA_ORIENTATION};
        try (Cursor cursor = context.getContentResolver().query(photoUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                orientation = cursor.getInt(0);
            }
        } catch (Exception ignored) {
        }
        if (Logger.DEBUG) { Log.d(TAG, "[getOrientationMediaStore: " + orientation + "]"); }
        return orientation;
    }

    /**
     * Creates pseudounique name based on timestamp
     *
     * @return Name
     */
    @NonNull
    private static String getUniqueName() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return "ulogger_" + timeStamp;
    }

    /**
     * Create image uri in media collection
     *
     * @param context Context
     * @return URI
     */
    static Uri createImageUri(Context context) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, getUniqueName());
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
        ContentResolver cr = context.getContentResolver();
        try (InputStream is = cr.openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(is, null, null);
        }

        bitmap = ThumbnailUtils.extractThumbnail(bitmap, sizePx, sizePx);
        bitmap = fixImageOrientation(context, uri, bitmap);
        return bitmap;
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
            if (Logger.DEBUG) { Log.d(TAG, "[fixImageOrientation exception: " + e + "]" ); }
        }
        return bitmap;
    }

    static long getFileSize(Context context, Uri uri) {
        long fileSize = 0;
        try (Cursor cursor = context.getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                cursor.moveToFirst();
                fileSize = cursor.getLong(sizeIndex);
            }
        }
        return fileSize;
    }

    @Nullable
    static String getFileMime(Context context, Uri uri) {
        ContentResolver cr = context.getContentResolver();
        String fileMime = cr.getType(uri);
        if (fileMime == null) {
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            fileMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
        }
        return fileMime;
    }

    static Uri resampleIfNeeded(Context context, Uri uri) throws IOException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int dstWidth = Integer.parseInt(prefs.getString(SettingsActivity.KEY_IMAGE_SIZE, context.getString(R.string.pref_imagesize_default)));
        if (dstWidth == 0) {
            return uri;
        }
        ContentResolver cr = context.getContentResolver();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream is = cr.openInputStream(uri)) {
            BitmapFactory.decodeStream(is, null, options);
        }
        int srcWidth = Math.max(options.outWidth, options.outHeight);
        int scale = srcWidth / dstWidth;
        if (Logger.DEBUG) { Log.d(TAG, "[resampleIfNeeded scale: " + scale + "]"); }
        options = new BitmapFactory.Options();
        Bitmap bitmap = null;
        boolean retry = false;
        do {
            try {
                if (scale > 1) {
                    options.inScaled = true;
                    options.inSampleSize = 1;
                    options.inDensity = srcWidth;
                    options.inTargetDensity =  dstWidth * options.inSampleSize;
                }
                try (InputStream is = cr.openInputStream(uri)) {
                    bitmap = BitmapFactory.decodeStream(is, null, options);
                }
            } catch (OutOfMemoryError e) {
                if (Logger.DEBUG) { Log.d(TAG, "[resampleIfNeeded OutOfMemoryError]"); }

            }
            if (bitmap == null && scale > 1) {
                options.inSampleSize = scale;
                retry = !retry;
                if (Logger.DEBUG) { Log.d(TAG, "[resampleIfNeeded try sampling: " + retry + "]"); }
            }
        } while (retry);

        if (bitmap == null) {
            throw new IOException("Failed to decode image");
        }

        bitmap = fixImageOrientation(context, uri, bitmap);

        String filename = getUniqueName() + ".jpg";
        File outFile = new File(context.getCacheDir(), filename);
        try (FileOutputStream os = new FileOutputStream(outFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, os);
        }
        return Uri.fromFile(outFile);
    }

    static Uri moveToAppStorage(@NonNull Context context, @NonNull Uri inUri) {
        Uri outUri = null;
        String path = inUri.getPath();
        if (path != null) {
            if (!path.startsWith(context.getCacheDir().getPath())) {
                return inUri;
            }
            File inFile = new File(path);
            File outFile = new File(context.getFilesDir(), inFile.getName());
            if (inFile.renameTo(outFile)) {
                outUri = Uri.fromFile(outFile);
            } else {
                if (Logger.DEBUG) { Log.d(TAG, "[moveToAppStorage failed]"); }
            }
        }
        return outUri;
    }

    static void clearImageCache(@NonNull Context context) {
        File dir = context.getCacheDir();
        clearImages(dir);
    }

    static void clearTrackImages(@NonNull Context context) {
        File dir = context.getFilesDir();
        clearImages(dir);
    }

    private static void clearImages(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getPath().endsWith(".jpg") && file.delete()) {
                    if (Logger.DEBUG) { Log.d(TAG, "[clearImages deleted file " + file.getName() + "]"); }
                }
            }
        }
    }

    static void deleteImage(Context context, Uri uri) {
        String path = uri.getPath();
        if (path != null) {
            File file = new File(path);
            if (file.isFile() && file.getPath().startsWith(context.getFilesDir().getPath()) && file.delete()) {
                if (Logger.DEBUG) { Log.d(TAG, "[deleteImage deleted file " + file.getName() + "]"); }
            }
        }
    }
}
