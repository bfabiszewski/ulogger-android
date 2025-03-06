/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.util.Size;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import net.fabiszewski.ulogger.Logger;
import net.fabiszewski.ulogger.R;
import net.fabiszewski.ulogger.db.DbAccess;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class ImageHelper {
    private static final String TAG = ImageHelper.class.getSimpleName();
    private static final String MEDIA_ORIENTATION = "orientation";
    private static final int ORIENTATION_90 = 90;
    private static final int ORIENTATION_180 = 180;
    private static final int ORIENTATION_270 = 270;
    private static final int ORIENTATION_NORMAL = 0;
    private static final String EXT_JPG = ".jpg";


    /**
     * Get orientation
     * First try MediaStore, then Exif
     *
     * @param context Context
     * @param uri     Image URI
     * @return Orientation in degrees
     */
    private static int getOrientation(@NonNull Context context, @NonNull Uri uri) {
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
    private static int getOrientationExif(@NonNull Context context, @NonNull Uri uri) {
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
    private static int getOrientationMediaStore(@NonNull Context context, @NonNull Uri uri) {
        int orientation = 0;
        String[] projection = {MEDIA_ORIENTATION};
        try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
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
     * Creates media title based on track media count
     *
     * @return Name
     */
    @NonNull
    private static String getUniqueTitle(@NonNull Context context) {
        int imageNumber = DbAccess.countImages(context) + 1;
        String trackName = DbAccess.getTrackName(context);
        return String.format(Locale.ROOT,"%s ＃%d", trackName, imageNumber);
    }

    /**
     * Create image uri in media collection
     *
     * @param context Context
     * @return URI
     */
    @Nullable
    public static Uri createImageUri(@NonNull Context context) {
        ContentValues values = new ContentValues();
        long timeMillis = System.currentTimeMillis();
        values.put(MediaStore.Images.Media.TITLE, getUniqueTitle(context));
        values.put(MediaStore.Images.Media.DATE_ADDED, timeMillis / 1000);
        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.DATE_TAKEN, timeMillis);
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }
        Uri imageUri = context.getContentResolver().insert(collection, values);
        if (Logger.DEBUG) { Log.d(TAG, "[createImageUri: " + imageUri + "]" ); }
        return imageUri;
    }

    /**
     * Extract thumbnail from URI
     * @param context Context
     * @param uri URI
     * @return Thumbnail
     * @throws IOException IO exception on failure
     */
    @NonNull
    public static Bitmap getThumbnail(@NonNull Context context, @NonNull Uri uri) throws IOException {
        int sizePx = getThumbnailSize(context);
        Bitmap bitmap;
        ContentResolver cr = context.getContentResolver();
        if (Objects.equals(uri.getScheme(), "content") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bitmap = cr.loadThumbnail(uri, new Size(sizePx, sizePx), null);
        } else {
            try (InputStream is = cr.openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(is, null, null);
            }
            if (bitmap == null) {
                throw new IOException("Decoding stream failed");
            }
            bitmap = ThumbnailUtils.extractThumbnail(bitmap, sizePx, sizePx);
        }
        bitmap = fixImageOrientation(context, uri, bitmap);
        return bitmap;
    }

    /**
     * Extract thumbnail from bitmap
     * @param context Context
     * @param bitmap Bitmap
     * @return Thumbnail
     */
    @NonNull
    public static Bitmap getThumbnail(@NonNull Context context, @NonNull Bitmap bitmap) {
        int sizePx = getThumbnailSize(context);
        return ThumbnailUtils.extractThumbnail(bitmap, sizePx, sizePx);
    }

    /**
     * Get thumbnail size from resources
     * @param context Context
     * @return Size in pixels
     */
    private static int getThumbnailSize(@NonNull Context context) {
        int sizeDp = (int) context.getResources().getDimension(R.dimen.waypoint_thumbnail_size);
        int sizePx = sizeDp * (int) Resources.getSystem().getDisplayMetrics().density;
        if (Logger.DEBUG) { Log.d(TAG, "[getThumbnailSize: " + sizePx + "]" ); }
        return sizePx;
    }

    /**
     * Fix image orientation if needed
     * @param context Context
     * @param uri Image URI as source of orientation data (MediaStore or EXIF)
     * @param bitmap Bitmap to be rotated
     * @return Resulting bitmap
     */
    @NonNull
    private static Bitmap fixImageOrientation(@NonNull Context context, @NonNull Uri uri, @NonNull Bitmap bitmap) {
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

    /**
     * Get file size
     * @param context Context
     * @param uri File URI
     * @return Size or -1 if not known
     */
    static long getFileSize(@NonNull Context context, @NonNull Uri uri) {
        final ContentResolver cr = context.getContentResolver();

        String[] projection = {OpenableColumns.SIZE};
        try (Cursor cursor = cr.query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long size = cursor.getInt(0);
                if (size > 0) {
                    if (Logger.DEBUG) { Log.d(TAG, "[getFileSize (db): " + size + "]" ); }
                    return size;
                }

            }
        } catch (Exception ignored) { }

        try (ParcelFileDescriptor parcelFileDescriptor = cr.openFileDescriptor(uri, "r")) {
            if (parcelFileDescriptor != null) {
                long size = parcelFileDescriptor.getStatSize();
                if (Logger.DEBUG) { Log.d(TAG, "[getFileSize (fd): " + size + "]" ); }
                return size;
            }
        } catch (IOException ignored) { }

        return -1;
    }

    /**
     * Try to get file MIME type
     * @param context Context
     * @param uri File URI
     * @return MIME type or null if not known
     */
    @Nullable
    static String getFileMime(@NonNull Context context, @NonNull Uri uri) {
        ContentResolver cr = context.getContentResolver();
        String fileMime = cr.getType(uri);
        if (fileMime == null) {
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            fileMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase(Locale.ROOT));
            if (Logger.DEBUG) { Log.d(TAG, "[getFileMime (ext): " + fileMime + "]" ); }
        } else {
            fileMime = fileMime.replace("jpg", "jpeg");
            if (Logger.DEBUG) { Log.d(TAG, "[getFileMime (cr): " + fileMime + "]" ); }
        }
        return fileMime;
    }

    /**
     * Resample image to given size threshold
     * @param context Context
     * @param uri Image URI
     * @param dstWidth Maximum width/height
     * @return Resampled bitmap
     * @throws IOException IO exception on error
     */
    @NonNull
    public static Bitmap getResampledBitmap(@NonNull Context context, @NonNull Uri uri, int dstWidth) throws IOException {
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
                if (retry) {
                    throw new IOException("Out of memory");
                } else if (scale > 1) {
                    retry = true;
                    options.inSampleSize = scale;
                    if (Logger.DEBUG) { Log.d(TAG, "[resampleIfNeeded try sampling]"); }
                }
            }
        } while (retry);

        if (bitmap == null) {
            throw new IOException("Failed to decode image");
        }

        bitmap = fixImageOrientation(context, uri, bitmap);
        return bitmap;
    }

    /**
     * Save bitmap to app cache folder as jpeg
     * @param context Context
     * @param bitmap Bitmap
     * @return URI of saved image
     * @throws IOException IO exception on failure
     */
    @NonNull
    public static Uri saveToCache(@NonNull Context context, @NonNull Bitmap bitmap) throws IOException {
        String filename = getUniqueName() + EXT_JPG;
        File outFile = new File(context.getCacheDir(), filename);
        try (FileOutputStream os = new FileOutputStream(outFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, os);
        }
        return Uri.fromFile(outFile);
    }

    /**
     * Move cached image file to internal app folder
     * Ignore files that are not in cache folder
     * @param context Context
     * @param inUri Source URI
     * @return Destination URI
     */
    @Nullable
    public static Uri moveCachedToAppStorage(@NonNull Context context, @NonNull Uri inUri) {
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
                if (Logger.DEBUG) { Log.d(TAG, "[moveCachedToAppStorage failed]"); }
            }
        }
        return outUri;
    }

    /**
     * Clear images in cache folder
     * @param context Context
     */
    public static void clearImageCache(@NonNull Context context) {
        File dir = context.getCacheDir();
        clearImages(dir);
    }

    /**
     * Clear images in app folder
     * @param context Context
     */
    public static void clearTrackImages(@NonNull Context context) {
        File dir = context.getFilesDir();
        clearImages(dir);
    }

    /**
     * Clear image jpeg files in given folder
     * @param dir Folder
     */
    private static void clearImages(@NonNull File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getPath().endsWith(EXT_JPG) && file.delete()) {
                    if (Logger.DEBUG) { Log.d(TAG, "[clearImages deleted file " + file.getName() + "]"); }
                }
            }
        }
    }

    /**
     * Delete file only if it is located in app internal folder
     * @param context Context
     * @param uri File URI
     */
    public static void deleteLocalImage(@NonNull Context context, @NonNull Uri uri) {
        String path = uri.getPath();
        if (path != null) {
            File file = new File(path);
            if (file.isFile() && file.getPath().startsWith(context.getFilesDir().getPath()) && file.delete()) {
                if (Logger.DEBUG) { Log.d(TAG, "[deleteLocalImage deleted file " + file.getName() + "]"); }
            }
        }
    }

    /**
     * Add image to MediaStore
     * @param context Context
     * @param uri Image URI
     */
    public static void galleryAdd(@NonNull Context context, @NonNull Uri uri) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, getUniqueTitle(context));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
        }
        ContentResolver cr = context.getContentResolver();
        if (Logger.DEBUG) { Log.d(TAG, "[update " + uri + "]"); }
        cr.update(uri, values, null, null);
    }

    /**
     * Get persistable permission for URI
     * @param context Context
     * @param uri URI
     */
    public static void getPersistablePermission(@NonNull Context context, @NonNull Uri uri) {
        try {
            context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            if (Logger.DEBUG) { Log.d(TAG, "[getPersistablePermission failed for " + uri + "]"); }
        }
    }
}
