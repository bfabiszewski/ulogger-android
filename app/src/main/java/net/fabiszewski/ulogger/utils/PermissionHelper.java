package net.fabiszewski.ulogger.utils;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import net.fabiszewski.ulogger.Logger;
import net.fabiszewski.ulogger.R;
import net.fabiszewski.ulogger.ui.Alert;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PermissionHelper {

    public interface PermissionRequester {
        void onPermissionGranted(@Nullable String requestCode);
        void onPermissionDenied(@Nullable String requestCode);
    }

    private static final String TAG = PermissionHelper.class.getSimpleName();
    @Nullable
    private String requestCode;
    @Nullable
    private ActivityResultLauncher<String[]> resultLauncher;
    @Nullable
    private Fragment fragment;
    @Nullable
    private Context context;
    boolean isLocationStageTwoNeeded = false;
    boolean isBackgroundLocationRationaleAccepted = false;

    /**
     * Constructor for simple usage, without requesting permissions
     * @param context Context
     */
    public PermissionHelper(@NonNull Context context) {
        this.context = context;
    }

    /**
     * Constructor for extended usage with requesting permissions
     * @param fragment Fragment
     * @param requester Permission requester
     */
    public PermissionHelper(@NonNull Fragment fragment, @NonNull PermissionRequester requester) {
        this.fragment = fragment;
        resultLauncher = fragment.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                results -> onPermissionsResult(requester, results));
    }

    /**
     * Invoked on activity result.
     * Calls success callback if any of the requested permissions is granted
     *
     * @param requester Requester
     * @param results Results
     */
    private void onPermissionsResult(@NonNull PermissionRequester requester, @NonNull Map<String, Boolean> results) {
        if (Logger.DEBUG) { Log.d(TAG, "[requestPermission: " + results.entrySet() + "]"); }
        boolean isGranted = false;
        for (Map.Entry<String, Boolean> result : results.entrySet()) {
            if (result.getValue()) {
                isGranted = true;
                break;
            }
        }
        boolean isStageTwoNeeded = isLocationStageTwoNeeded;
        isLocationStageTwoNeeded = false;
        if (isStageTwoNeeded && isGranted) {
            if (Logger.DEBUG) { Log.d(TAG, "[PermissionHelper: stage two needed]"); }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requestBackgroundLocationPermission(this.requestCode);
            }
        }
        else if (isGranted) {
            if (Logger.DEBUG) { Log.d(TAG, "[PermissionHelper: permission granted]"); }
            requester.onPermissionGranted(this.requestCode);

        } else {
            if (Logger.DEBUG) { Log.d(TAG, "[PermissionHelper: permission refused]"); }
            requester.onPermissionDenied(this.requestCode);
        }
    }

    @Nullable
    private Context getContext() {
        return fragment != null ? fragment.getContext() : context;
    }

    /**
     * @param requestCode Request code will be returned with callback
     */
    public void requestWriteExternalStoragePermission(@Nullable String requestCode) {
        requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, requestCode);
    }
    public void requestWriteExternalStoragePermission() {
        requestWriteExternalStoragePermission(null);
    }

    /**
     * @param requestCode Request code will be returned with callback
     */
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public void requestNotificationsPermission(@Nullable String requestCode) {
        requestPermission(Manifest.permission.POST_NOTIFICATIONS, requestCode);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public void requestNotificationsPermission() {
        requestNotificationsPermission(null);
    }

    /**
     * @param requestCode Request code will be returned with callback
     */
    public void requestFineLocationPermission(@Nullable String requestCode) {
        List<String> permissions = new ArrayList<>();
        permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // On Android 12+ coarse location permission must be also requested
            permissions.add(ACCESS_COARSE_LOCATION);
        }
        requestPermissions(permissions, requestCode);
    }

    public void requestFineLocationPermission() {
        requestFineLocationPermission(null);
    }

    /**
     * @param requestCode Request code will be returned with callback
     */
    public void requestCoarseLocationPermission(@Nullable String requestCode) {
        requestPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION, requestCode);
    }

    public void requestCoarseLocationPermission() {
        requestCoarseLocationPermission(null);
    }

    /**
     * @param requestCode Request code will be returned with callback
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    public void requestBackgroundLocationPermission(@Nullable String requestCode) {
        if (fragment == null || fragment.getActivity() == null || resultLauncher == null) {
            if (Logger.DEBUG) { Log.d(TAG, "[requestBackgroundLocationPermission: missing fragment context]"); }
            return;
        }

        List<String> permissions = new ArrayList<>();
        // Background location permission can only be granted when forward location is permitted
        if (hasForegroundLocationPermission()) {
            permissions.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        } else {
            isLocationStageTwoNeeded = true;
            if (Logger.DEBUG) { Log.d(TAG, "[forward location permission denied]"); }
            permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // On Android 12+ coarse location permission must be also requested
                permissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
            }
        }

        if (!isBackgroundLocationRationaleAccepted &&
                ActivityCompat.shouldShowRequestPermissionRationale(fragment.requireActivity(), android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            requestPermissionOnRationaleAccepted(permissions, requestCode);
        } else {
            requestPermissions(permissions, requestCode);
        }
    }

    /**
     * Show permission rationale dialog, on accept request permission
     * 
     * @param permissions Requested permissions
     * @param requestCode Request code which will be returned with callback
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    private void requestPermissionOnRationaleAccepted(@NonNull List<String> permissions, @Nullable String requestCode) {
        final Context ctx = getContext();
        if (ctx == null) {
            if (Logger.DEBUG) { Log.d(TAG, "[requestBackgroundLocationPermission: missing context]"); }
            return;
        }
        final CharSequence label = getBackgroundPermissionOptionLabel(ctx);
        Alert.showConfirm(
                ctx,
                ctx.getString(R.string.background_location_required),
                ctx.getString(R.string.background_location_rationale, label),
                (dialog, which) -> {
                    dialog.dismiss();
                    requestPermissions(permissions, requestCode);
                    isBackgroundLocationRationaleAccepted = true;
                }
        );
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    public void requestBackgroundLocationPermission() {
        requestBackgroundLocationPermission(null);
    }

    /**
     * @param permission Requested permission
     * @param requestCode Request code which will be returned with callback
     */
    public void requestPermission(@NonNull String permission, @Nullable String requestCode) {
        List<String> permissions = new ArrayList<>();
        permissions.add(permission);
        requestPermissions(permissions, requestCode);
    }

    /**
     * @param permissions Requested permissions
     * @param requestCode Request code which will be returned with callback
     */
    public void requestPermissions(@NonNull List<String> permissions, @Nullable String requestCode) {
        if (fragment != null && resultLauncher != null) {
            this.requestCode = requestCode;
            resultLauncher.launch(permissions.toArray(new String[0]));
        } else {
            if (Logger.DEBUG) { Log.d(TAG, "[requestPermissions: missing fragment context]"); }
        }
    }

    /**
     * Check if user granted given permission.
     *
     * @param permission Requested permission
     * @return True if has requested permission
     */
    public boolean hasPermission(@NonNull String permission) {
        final Context ctx = getContext();
        if (ctx == null) {
            if (Logger.DEBUG) { Log.d(TAG, "[hasPermission: missing context]"); }
            return false;
        }
        boolean ret = ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED;
        if (Logger.DEBUG) { Log.d(TAG, "[has " + permission + " permission: " + ret + "]"); }
        return ret;
    }

    /**
     * Check if user granted permission to write external storage.
     *
     * @return True if permission granted, false otherwise
     */
    public boolean hasWriteExternalStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        return true;
    }

    /**
     * Check if user granted permission to access location (coarse or fine).
     *
     * @return True if permission granted, false otherwise
     */
    boolean hasForegroundLocationPermission() {
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    /**
     * Check if user granted permission to access background location.
     *
     * @return True if permission granted, false otherwise
     */
    public boolean hasBackgroundLocationPermission() {
        boolean ret = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ret = hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
        return ret;
    }

    /**
     * Wrapper for getBackgroundPermissionOptionLabel() method
     * Will return translated label only when context string was also translated
     * @param ctx Context
     * @return Localized label
     */
    @SuppressLint("AppBundleLocaleChanges")
    @RequiresApi(api = Build.VERSION_CODES.R)
    @NonNull
    private CharSequence getBackgroundPermissionOptionLabel(@NonNull Context ctx) {
        CharSequence label = ctx.getPackageManager().getBackgroundPermissionOptionLabel();
        CharSequence defaultLabel = "Allow all the time";

        if ("en".equals(Locale.getDefault().getLanguage())) {
            //noinspection SizeReplaceableByIsEmpty
            return label.length() > 0 ? label : defaultLabel;
        }

        CharSequence translated = ctx.getString(R.string.background_location_rationale);
        Configuration config = new Configuration(ctx.getResources().getConfiguration());
        config.setLocale(Locale.ENGLISH);
        CharSequence defaultText = ctx.createConfigurationContext(config).getText(R.string.background_location_rationale);

        return CharSequence.compare(translated, defaultText) == 0 ? defaultLabel : label;
    }
}
