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
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.concurrent.ExecutorService;

import static android.app.Activity.RESULT_OK;
import static android.content.Intent.EXTRA_LOCAL_ONLY;
import static android.content.Intent.EXTRA_MIME_TYPES;
import static android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
import static java.util.concurrent.Executors.newCachedThreadPool;

public class WaypointFragment extends Fragment implements LoggerTask.LoggerTaskCallback, ImageTask.ImageTaskCallback {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_OPEN = 2;
    private static final int PERMISSION_WRITE = 1;
    private static final int PERMISSION_LOCATION = 2;
    private static final String KEY_URI = "keyPhotoUri";
    private static final String KEY_THUMB = "keyPhotoThumb";
    private static final String KEY_LOCATION = "keyLocation";

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

    private final ExecutorService executor = newCachedThreadPool();

    public WaypointFragment() {
    }

    static WaypointFragment newInstance() {
        return new WaypointFragment();
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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
        if (savedInstanceState.containsKey(KEY_URI)) {
            photoUri = savedInstanceState.getParcelable(KEY_URI);
            setThumbnail(photoThumb);
        }
        if (savedInstanceState.containsKey(KEY_THUMB)) {
            photoThumb = savedInstanceState.getParcelable(KEY_THUMB);
            setThumbnail(photoThumb);
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
        if (photoUri != null) {
            outState.putParcelable(KEY_URI, photoUri);
        }
        if (photoThumb != null) {
            outState.putParcelable(KEY_THUMB, photoThumb);
        }
        if (location != null) {
            outState.putParcelable(KEY_LOCATION, location);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!hasLocation()) {
            runLoggerTask();
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
     */
    private void runImageTask(@NonNull Uri uri) {
        if (imageTask == null || !imageTask.isRunning()) {
            clearImage();
            saveButton.setEnabled(false);
            imageTask = new ImageTask(uri, this);
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
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(requireContext().getPackageManager()) != null) {
            photoUri = ImageHelper.createImageUri(requireContext());
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            int flags = FLAG_GRANT_WRITE_URI_PERMISSION|FLAG_GRANT_READ_URI_PERMISSION|FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
            takePictureIntent.addFlags(flags);
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            showToast("You must accept permission for writing photo to external storage");
            ActivityCompat.requestPermissions(requireActivity(), new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE }, PERMISSION_WRITE);
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
                runLoggerTask();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_IMAGE_OPEN:
                    if (resultData != null && resultData.getData() != null) {
                        photoUri = resultData.getData();
                        runImageTask(photoUri);
                    }
                    break;

                case REQUEST_IMAGE_CAPTURE:
                    if (photoUri != null) {
                        ImageHelper.galleryAdd(requireContext(), photoUri);
                        runImageTask(photoUri);
                    }
                    break;
            }
        }
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
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = { "image/jpeg", "image/gif", "image/png", "image/x-ms-bmp" };
        intent.putExtra(EXTRA_MIME_TYPES, mimeTypes);
        intent.putExtra(EXTRA_LOCAL_ONLY, true);
        int flags = FLAG_GRANT_READ_URI_PERMISSION|FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
        intent.addFlags(flags);
        try {
            startActivityForResult(intent, REQUEST_IMAGE_OPEN);
        } catch (ActivityNotFoundException e) {
            showToast(getString(R.string.cannot_open_picker));
        }
    }

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
            Activity activity = getActivity();
            if (activity != null) {
                ActivityCompat.requestPermissions(activity, new String[]{ Manifest.permission.ACCESS_FINE_LOCATION }, PERMISSION_LOCATION);
            }
        }
        if ((reason & LoggerTask.E_DISABLED) != 0) {
            showToast(getString(R.string.location_disabled));
        }
    }

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
