/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.location.Location;
import android.location.LocationManager;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WaypointActivity extends AppCompatActivity implements LoggerTask.ILoggerTask {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_OPEN = 2;
    private static final int PERMISSION_WRITE = 1;
    private static final int PERMISSION_LOCATION = 2;
    private static final int ACTION_PHOTO = 0;
    private static final int ACTION_LIBRARY = 1;
    private static final String KEY_URI = "keyPhotoUri";
    private static final String KEY_LOCATION = "keyLocation";

    private static final String TAG = WaypointActivity.class.getSimpleName();
    private static final String JPEG_MIME = "image/jpg";

    private TextView locationTextView;
    private TextView locationDetailsTextView;
    private EditText commentEditText;
    private Button saveButton;
    private ImageView thumbnailImageView;
    private SwipeRefreshLayout swipe;

    private LoggerTask loggerTask;

    private Location location = null;
    private Uri photoUri = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waypoint);
        Toolbar toolbar = findViewById(R.id.wa_toolbar);
        setSupportActionBar(toolbar);
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }
        locationTextView = findViewById(R.id.waypointLocation);
        locationDetailsTextView = findViewById(R.id.waypointLocationDetails);
        commentEditText = findViewById(R.id.waypointComment);
        saveButton = findViewById(R.id.waypointButton);
        thumbnailImageView = findViewById(R.id.waypointThumbnail);
        swipe = findViewById(R.id.waypointSwipeLayout);
        swipe.setOnRefreshListener(this::reload);
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            thumbnailImageView.setVisibility(View.INVISIBLE);
        }

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }
    }

    private void restoreState(Bundle savedInstanceState) {
        if (savedInstanceState.containsKey(KEY_URI)) {
            photoUri = savedInstanceState.getParcelable(KEY_URI);
            setThumbnail();
        }
        if (savedInstanceState.containsKey(KEY_LOCATION)) {
            location = savedInstanceState.getParcelable(KEY_LOCATION);
            setLocationText();
        }
    }

    private void reload() {
        location = null;
        cancelTask();
        runTask();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (photoUri != null) {
            outState.putParcelable(KEY_URI, photoUri);
        }
        if (location != null) {
            outState.putParcelable(KEY_LOCATION, location);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!hasLocation()) {
            runTask();
        }
    }

    /**
     * Start logger service
     */
    private void runTask() {
        if (loggerTask == null || loggerTask.getStatus() != LoggerTask.Status.RUNNING) {
            saveButton.setEnabled(false);
            location = null;
            locationTextView.setText("");
            locationDetailsTextView.setText("");
            loggerTask = new LoggerTask(this);
            loggerTask.execute();
            setRefreshing(true);
        }
    }

    /**
     * Stop logger service
     */
    private void cancelTask() {
        if (loggerTask.getStatus() == LoggerTask.Status.RUNNING) {
            setRefreshing(false);
            loggerTask.cancel(true);
        }
    }

    private void setRefreshing(boolean refreshing) {
        swipe.setRefreshing(refreshing);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTask();
    }

    private void setLocationText() {
        LocationFormatter formatter = new LocationFormatter(location);
        locationTextView.setText(String.format("%s\n—\n%s", formatter.getLongitudeDMS(), formatter.getLatitudeDMS()));
        String[] providers = getResources().getStringArray(R.array.providersEntries);
        String provider;
        if (LocationManager.GPS_PROVIDER.equals(location.getProvider())) {
            // FIXME: This will fail when providers array change, use string resource in array?
            provider = providers[0];
        } else if (LocationManager.NETWORK_PROVIDER.equals(location.getProvider())) {
            provider = providers[1];
        } else {
            provider = location.getProvider();
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            locationDetailsTextView.setText(String.format(Locale.getDefault(),
                    "%d m a.s.l. • %d%% accuracy (%s)", (int) location.getAltitude(),
                    (int) location.getAccuracy(), provider));
        } else {
            locationDetailsTextView.setText(String.format(Locale.getDefault(),
                    "%d±%d m a.s.l. • %d%% accuracy (%s)", (int) location.getAltitude(),
                    (int) location.getVerticalAccuracyMeters(), (int) location.getAccuracy(), provider));
        }
    }

    public void saveWaypoint(View view) {
        if (hasLocation()) {
            String comment = commentEditText.getText().toString();
            String uri = (photoUri == null) ? null : photoUri.toString();
            DbAccess db = DbAccess.getInstance();
            db.open(this);
            db.writeLocation(location, comment, uri);
            db.close();
            photoUri = null;
            if (Logger.DEBUG) { Log.d(TAG, "[saveWaypoint: " + location + ", " + comment + "," + uri + "]"); }
        }
        finish();
    }

    private boolean hasLocation() {
        return location != null;
    }

    public void takePhoto() {
        if (!hasPermissions()) {
            return;
        }
        requestImageCapture();
    }

    private void requestImageCapture() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            createImageUri();
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            int flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
                takePictureIntent.addFlags(flags);
            }
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    private boolean hasPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            showToast("You must accept permission for writing photo to external storage");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_WRITE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_WRITE) {
            if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                requestImageCapture();
            }
        } else if (requestCode == PERMISSION_LOCATION) {
            if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                runTask();
            } else {
                finish();
            }
        }
    }

    private void createImageUri() {
        if (photoUri == null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.TITLE, getFileName());
            values.put(MediaStore.Images.Media.MIME_TYPE, JPEG_MIME);
            Uri collection;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            } else {
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            }
            photoUri = getContentResolver().insert(collection, values);
        }
    }

    @NonNull
    private static String getFileName() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return "JPEG_" + timeStamp + "_";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            setThumbnail();
        } else if (requestCode == REQUEST_IMAGE_OPEN && resultCode == RESULT_OK) {
            if (resultData != null) {
                photoUri = resultData.getData();
                try {
                    getContentResolver().takePersistableUriPermission(photoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    setThumbnail();
                } catch (SecurityException e) {
                    photoUri = null;
                    showToast("Failed to acquire persistable read permission for the image");
                }
            }
        }
    }

    private void setThumbnail() {
        try {
            int sizeDp = (int) getResources().getDimension(R.dimen.thumbnail_size);
            int sizePx = sizeDp * (int) Resources.getSystem().getDisplayMetrics().density;

            Bitmap bitmap;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                bitmap = getBitmapLegacy();
            } else {
                bitmap = getBitmap();
            }
            Bitmap thumbBitmap = ThumbnailUtils.extractThumbnail(bitmap, sizePx, sizePx);
            bitmap.recycle();
            try {
                int orientation = getOrientation(this, photoUri);
                if (orientation != 0) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(orientation);
                    thumbBitmap = Bitmap.createBitmap(thumbBitmap, 0, 0, thumbBitmap.getWidth(), thumbBitmap.getHeight(), matrix, true);
                }
            }
            catch (Exception e) {
                if (Logger.DEBUG) { Log.d(TAG, "[setThumbnail exception: " + e + "]"); }
            }
            thumbnailImageView.setImageBitmap(thumbBitmap);
        } catch (IOException e) {
            if (Logger.DEBUG) { Log.d(TAG, "[setThumbnail exception: " + e + "]"); }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    @NonNull
    private Bitmap getBitmap() throws IOException {
        Bitmap bitmap;
        ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), photoUri);
        bitmap = ImageDecoder.decodeBitmap(src);
        return bitmap;
    }

    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    private Bitmap getBitmapLegacy() throws IOException {
        Bitmap bitmap;
        bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), photoUri);
        return bitmap;
    }

    private static int getOrientation(Context context, Uri uri) {
        int orientation = getOrientationMediaStore(context, uri);
        if (orientation == 0) {
            orientation = getOrientationExif(context, uri);
        }
        return orientation;
    }

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

    private static int getOrientationMediaStore(Context context, Uri photoUri) {
        int orientation = 0;
        String[] projection = {"orientation"};
        try (Cursor cursor = context.getContentResolver().query(photoUri, projection, null, null, null)){
            if (cursor != null && cursor.moveToFirst()) {
                orientation = cursor.getInt(0);
            }
        } catch (Exception ignored) {}
        if (Logger.DEBUG) { Log.d(TAG, "[getOrientationMediaStore: " + orientation + "]"); }
        return orientation;
    }
//
//    private void galleryAddPic() {
//        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
//        mediaScanIntent.setData(photoUri);
//        this.sendBroadcast(mediaScanIntent);
//    }


    /**
     * Display toast message
     * FIXME: duplicated method
     * @param text Message
     */
    private void showToast(CharSequence text) {
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
        toast.show();
    }

    public void addImage(View view) {
        final CharSequence[] items = new CharSequence[2];
        items[ACTION_PHOTO] = "Take Photo";
        items[ACTION_LIBRARY] = "Choose from Library";
        AlertDialog.Builder builder = new AlertDialog.Builder(WaypointActivity.this);
        builder.setItems(items, (dialog, item) -> {
            if (item == ACTION_PHOTO) {
                takePhoto();
            } else if (item == ACTION_LIBRARY) {
                pickImage();
            }
        });
        builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_IMAGE_OPEN);
        } catch (ActivityNotFoundException e) {
            showToast(getString(R.string.cannot_open_picker));
        }
    }

    @Override
    public void onLoggerTaskCompleted(Location location) {
        this.location = location;
        setRefreshing(false);
        setLocationText();
        saveButton.setEnabled(true);
    }

    @Override
    public void onLoggerTaskFailure(int reason) {
        setRefreshing(false);
        locationTextView.setText("Couldn't get location");
        if ((reason & LoggerTask.E_PERMISSION) != 0) {
            showToast(getString(R.string.location_permission_denied));
        }
        if ((reason & LoggerTask.E_DISABLED) != 0) {
            showToast(getString(R.string.location_disabled));
        }
    }
}
