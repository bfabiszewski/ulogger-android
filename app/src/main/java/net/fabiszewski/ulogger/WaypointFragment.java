/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import static android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
import static androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions;
import static androidx.activity.result.contract.ActivityResultContracts.RequestPermission;
import static androidx.activity.result.contract.ActivityResultContracts.TakePicture;
import static java.util.concurrent.Executors.newCachedThreadPool;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class WaypointFragment extends Fragment implements LoggerTask.LoggerTaskCallback, ImageTask.ImageTaskCallback {

    private static final String KEY_URI = "keyPhotoUri";
    private static final String KEY_LOCATION = "keyLocation";
    private static final String KEY_WAITING = "keyWaiting";

    private static final String TAG = WaypointFragment.class.getSimpleName();

    private TextView locationNotFoundTextView;
    private TextView locationTextView;
    private TextView locationDetailsTextView;
    private EditText commentEditText;
    private Button saveButton;
    private ImageView thumbnailImageView;
    private SwipeRefreshLayout swipe;

    private LoggerTask loggerTask;
    private ImageTask imageTask;

    private Location location = null;
    private Uri photoUri = null;
    private Bitmap photoThumb = null;
    private boolean isWaitingForCamera = false;

    private final ExecutorService executor = newCachedThreadPool();

    /**
     * Request location permission(s), on granted run logger task
     */
    final ActivityResultLauncher<String[]> requestLocationPermission = registerForActivityResult(new RequestMultiplePermissions(), results -> {
        if (Logger.DEBUG) { Log.d(TAG, "[requestLocationPermission: " + results.entrySet() + "]"); }
        boolean isGranted = false;
        for (Map.Entry<String, Boolean> result : results.entrySet()) {
            if (result.getValue()) {
                isGranted = true;
            }
        }
        if (isGranted) {
            if (Logger.DEBUG) { Log.d(TAG, "[LocationPermission: granted]"); }
            runLoggerTask();
        } else {
            if (Logger.DEBUG) { Log.d(TAG, "[LocationPermission: refused]"); }
            finish();
        }
    });

    /**
     * Request write permission, on granted take picture
     */
    final ActivityResultLauncher<String> requestWritePermission = registerForActivityResult(new RequestPermission(), isGranted -> {
        if (Logger.DEBUG) { Log.d(TAG, "[requestWritePermission: " + isGranted + "]"); }
        if (isGranted) {
            requestImageCapture();
        }
    });

    /**
     * Take picture, then run image task
     */
    final ActivityResultLauncher<Uri> takePicture = registerForActivityResult(new TakePicture() {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @NonNull Uri input) {
            isWaitingForCamera = true;
            int flags = FLAG_GRANT_WRITE_URI_PERMISSION|FLAG_GRANT_READ_URI_PERMISSION|FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
            return super.createIntent(context, input).addFlags(flags);
        }
    }, isSaved -> {
        if (Logger.DEBUG) { Log.d(TAG, "[TakePicture result]"); }
        if (isSaved) {
            if (photoUri != null) {
                ImageHelper.galleryAdd(requireContext(), photoUri);
                runImageTask(photoUri);
            }
        } else {
            clearImage();
        }
        isWaitingForCamera = false;
    });

    /**
     * Open image file, then run image task
     */
    final ActivityResultLauncher<String[]> openPicture = registerForActivityResult(new OpenLocalDocument(), uri -> {
        if (uri != null) {
            photoUri = uri;
            runImageTask(photoUri);
        }
    });

    public WaypointFragment() { }

    static WaypointFragment newInstance() {
        return new WaypointFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Logger.DEBUG) { Log.d(TAG, "[onCreate]"); }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (Logger.DEBUG) { Log.d(TAG, "[onCreateView]"); }
        View layout = inflater.inflate(R.layout.fragment_waypoint, container, false);

        locationNotFoundTextView = layout.findViewById(R.id.waypointLocationNotFound);
        locationTextView = layout.findViewById(R.id.waypointLocation);
        locationDetailsTextView = layout.findViewById(R.id.waypointLocationDetails);
        commentEditText = layout.findViewById(R.id.waypointComment);
        saveButton = layout.findViewById(R.id.waypointButton);
        thumbnailImageView = layout.findViewById(R.id.waypointThumbnail);
        swipe = (SwipeRefreshLayout) layout;
        swipe.setOnRefreshListener(this::reloadTask);

        saveButton.setOnClickListener(this::saveWaypoint);
        thumbnailImageView.setOnClickListener(this::addImage);
        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }
        return layout;
    }

    private void restoreState(Bundle savedInstanceState) {
        if (Logger.DEBUG) { Log.d(TAG, "[restoreState]"); }
        if (savedInstanceState.containsKey(KEY_WAITING)) {
            isWaitingForCamera = true;
        }
        if (savedInstanceState.containsKey(KEY_URI)) {
            photoUri = savedInstanceState.getParcelable(KEY_URI);
        }
        if (savedInstanceState.containsKey(KEY_LOCATION)) {
            location = savedInstanceState.getParcelable(KEY_LOCATION);
            setLocationText();
            saveButton.setEnabled(true);
        }
    }

    private void reloadTask() {
        cancelLoggerTask();
        runLoggerTask();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (Logger.DEBUG) { Log.d(TAG, "[onSaveInstanceState]"); }
        if (photoUri != null) {
            outState.putParcelable(KEY_URI, photoUri);
        }
        if (location != null) {
            outState.putParcelable(KEY_LOCATION, location);
        }
        if (isWaitingForCamera) {
            outState.putBoolean(KEY_WAITING, true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Logger.DEBUG) { Log.d(TAG, "[onResume]"); }
        if (!hasLocation()) {
            runLoggerTask();
        }
        if (photoUri != null && photoThumb == null && !isWaitingForCamera) {
            runThumbnailTask(photoUri);
        }
    }

    /**
     * Start logger task
     */
    private void runLoggerTask() {
        if (loggerTask == null || !loggerTask.isRunning()) {
            saveButton.setEnabled(false);
            location = null;
            clearLocationText();
            loggerTask = new LoggerTask(this);
            executor.execute(loggerTask);
            setRefreshing(true);
        }
    }

    /**
     * Stop logger task
     */
    private void cancelLoggerTask() {
        if (Logger.DEBUG) { Log.d(TAG, "[cancelLoggerTask]"); }
        if (loggerTask != null && loggerTask.isRunning()) {
            if (Logger.DEBUG) { Log.d(TAG, "[cancelLoggerTask effective]"); }
            loggerTask.cancel();
            loggerTask = null;
            if (imageTask == null || !imageTask.isRunning()) {
                setRefreshing(false);
            }
        }
    }

    private void cancelImageTask() {
        if (Logger.DEBUG) { Log.d(TAG, "[cancelImageTask]"); }
        if (imageTask != null && imageTask.isRunning()) {
            if (Logger.DEBUG) { Log.d(TAG, "[cancelImageTask effective]"); }
            imageTask.cancel();
            imageTask = null;
            if (loggerTask == null || !loggerTask.isRunning()) {
                setRefreshing(false);
            }
        }
    }

    /**
     * Start image task
     * Transforms image if needed and generates thumbnail for URI
     * @param uri URI
     */
    private void runImageTask(@NonNull Uri uri) {
        if (imageTask == null || !imageTask.isRunning()) {
            if (Logger.DEBUG) { Log.d(TAG, "[runImageTask]"); }
            clearImage();
            saveButton.setEnabled(false);
            imageTask = new ImageTask(uri, this);
            executor.execute(imageTask);
            setRefreshing(true);
        }
    }

    /**
     * Start thumbnail task
     * Generates thumbnail for URI
     * @param uri URI
     */
    private void runThumbnailTask(@NonNull Uri uri) {
        if (imageTask == null || !imageTask.isRunning()) {
            if (Logger.DEBUG) { Log.d(TAG, "[runThumbnailTask]"); }
            imageTask = new ImageTask(uri, this, true);
            executor.execute(imageTask);
            setRefreshing(true);
        }
    }

    private void setRefreshing(boolean refreshing) {
        swipe.setRefreshing(refreshing);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (Logger.DEBUG) { Log.d(TAG, "[onDetach]"); }
        cancelLoggerTask();
        cancelImageTask();
    }

    @Override
    public void onDestroy() {
        ImageHelper.clearImageCache(requireContext());
        super.onDestroy();
    }

    /**
     * Display location details
     */
    private void setLocationText() {
        LocationFormatter formatter = new LocationFormatter(location);
        locationNotFoundTextView.setVisibility(View.GONE);
        locationTextView.setText(String.format("%s\n—\n%s", formatter.getLongitudeDMS(), formatter.getLatitudeDMS()));
        locationTextView.setVisibility(View.VISIBLE);
        locationDetailsTextView.setText(formatter.getDetails(requireContext()));
    }


    private void clearLocationText() {
        locationNotFoundTextView.setVisibility(View.GONE);
        locationTextView.setVisibility(View.VISIBLE);
        locationTextView.setText("");
        locationDetailsTextView.setText("");
    }

    /**
     * Save waypoint action
     * @param view View
     */
    private void saveWaypoint(View view) {
        if (hasLocation()) {
            if (photoUri != null) {
                photoUri = ImageHelper.moveCachedToAppStorage(view.getContext(), photoUri);
            }
            String comment = commentEditText.getText().toString();
            String uri = (photoUri == null) ? null : photoUri.toString();
            DbAccess.writeLocation(view.getContext(), location, comment, uri);
            photoUri = null;
            if (Logger.DEBUG) { Log.d(TAG, "[saveWaypoint: " + location + ", " + comment + ", " + uri + "]"); }
        }
        finish();
    }

    /**
     * Go back to main fragment
     */
    private void finish() {
        requireActivity().getSupportFragmentManager().popBackStackImmediate();
    }

    private boolean hasLocation() {
        return location != null;
    }

    private void takePhoto() {
        if (!hasStoragePermission()) {
            return;
        }
        requestImageCapture();
    }

    private void requestImageCapture() {
        photoUri = ImageHelper.createImageUri(requireContext());
        takePicture.launch(photoUri);
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            showToast("You must accept permission for writing photo to external storage");
            requestWritePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return false;
        }
        return true;
    }

    /**
     * Set thumbnail on ImageView
     * @param thumbnail Thumbnail bitmap, default placeholder if null
     */
    private void setThumbnail(@Nullable Bitmap thumbnail) {
        if (thumbnail == null) {
            thumbnailImageView.setImageResource(R.drawable.ic_photo_camera_gray_24dp);
        } else {
            thumbnailImageView.setImageBitmap(thumbnail);
        }
    }

    /**
     * Display toast message
     * FIXME: duplicated method
     * @param text Message
     */
    private void showToast(CharSequence text) {
        Context context = getContext();
        if (context != null) {
            Toast toast = Toast.makeText(requireContext(), text, Toast.LENGTH_LONG);
            toast.show();
        }
    }

    /**
     * Add image action
     * @param view View
     */
    private void addImage(View view) {
        clearImage();
        if (requireContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
            View dialogView = View.inflate(getContext(), R.layout.image_dialog, null);
            builder.setView(dialogView);
            builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.dismiss());

            TextView photoTextView = dialogView.findViewById(R.id.action_photo);
            TextView libraryTextView = dialogView.findViewById(R.id.action_library);

            final AlertDialog dialog = builder.create();
            photoTextView.setOnClickListener(v -> {
                takePhoto();
                dialog.dismiss();
            });
            libraryTextView.setOnClickListener(v -> {
                pickImage();
                dialog.dismiss();
            });
            dialog.show();
        } else {
            pickImage();
        }

    }

    /**
     * Show file picker
     */
    private void pickImage() {
        try {
            String[] mimeTypes = { "image/jpeg", "image/gif", "image/png", "image/x-ms-bmp" };
            openPicture.launch(mimeTypes);
        } catch (ActivityNotFoundException e) {
            showToast(getString(R.string.cannot_open_picker));
        }
    }

    /**
     * Update state on location received
     * @param location Current location
     */
    @Override
    public void onLoggerTaskCompleted(Location location) {
        if (Logger.DEBUG) { Log.d(TAG, "[onLoggerTaskCompleted: " + location + "]"); }
        this.location = location;
        if (imageTask == null || !imageTask.isRunning()) {
            setRefreshing(false);
        }
        setLocationText();
        saveButton.setEnabled(true);
    }

    /**
     * Take actions on location request failure
     * @param reason Bit encoded failure reason
     */
    @Override
    public void onLoggerTaskFailure(int reason) {
        if (Logger.DEBUG) { Log.d(TAG, "[onLoggerTaskFailure: " + reason + "]"); }
        if (imageTask == null || !imageTask.isRunning()) {
            setRefreshing(false);
        }
        locationTextView.setVisibility(View.GONE);
        locationNotFoundTextView.setVisibility(View.VISIBLE);
        if ((reason & LoggerTask.E_PERMISSION) != 0) {
            showToast(getString(R.string.location_permission_denied));
            List<String> permissions = new ArrayList<>();
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // On Android 12+ coarse location permission must be also requested
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
            requestLocationPermission.launch(permissions.toArray(new String[0]));
        }
        if ((reason & LoggerTask.E_DISABLED) != 0) {
            showToast(getString(R.string.location_disabled));
        }
    }


    /**
     * Update state on image task completed
     * @param uri Image URI
     * @param thumbnail Image thumbnail
     */
    @Override
    public void onImageTaskCompleted(@NonNull Uri uri, @NonNull Bitmap thumbnail) {
        if (Logger.DEBUG) { Log.d(TAG, "[onImageTaskCompleted: " + uri + "]"); }
        photoUri = uri;
        photoThumb = thumbnail;
        setThumbnail(thumbnail);
        if (loggerTask == null || !loggerTask.isRunning()) {
            setRefreshing(false);
        }
        if (this.location != null) {
            saveButton.setEnabled(true);
        }
    }

    /**
     * Update state on image task failure
     * @param error Error message
     */
    @Override
    public void onImageTaskFailure(@NonNull String error) {
        if (Logger.DEBUG) { Log.d(TAG, "[onImageTaskFailure: " + error + "]"); }
        clearImage();
        if (loggerTask == null || !loggerTask.isRunning()) {
            setRefreshing(false);
        }
        String message = getString(R.string.image_task_failed);
        if (!error.isEmpty()) {
            message += ": " + error;
        }
        showToast(message);
        if (this.location != null) {
            saveButton.setEnabled(true);
        }
    }

    /**
     * Clear image cache and preview
     */
    private void clearImage() {
        if (photoUri != null) {
            ImageHelper.clearImageCache(requireContext());
            photoUri = null;
        }
        if (photoThumb != null) {
            photoThumb = null;
            setThumbnail(null);
        }
    }
}
