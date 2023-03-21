package net.fabiszewski.ulogger;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

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
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PermissionHelper {

    public interface PermissionRequester {
        void onPermissionGranted(String requestCode);
        void onPermissionDenied(String requestCode);
    }

    private static final String TAG = PermissionHelper.class.getSimpleName();
    private String requestCode;

    final ActivityResultLauncher<String[]> resultLauncher;
    final Fragment fragment;
    boolean isLocationStageTwoNeeded = false;

    public PermissionHelper(Fragment fragment, PermissionRequester requester) {
        this.fragment = fragment;
        resultLauncher = fragment.registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), results -> {
            if (Logger.DEBUG) { Log.d(TAG, "[requestPermission: " + results.entrySet() + "]"); }
            boolean isGranted = false;
            for (Map.Entry<String, Boolean> result : results.entrySet()) {
                if (result.getValue()) {
                    isGranted = true;
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
        });
    }

    public void requestWritePermission(String requestCode) {
        requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, requestCode);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public void requestNotificationsPermission(String requestCode) {
        requestPermission(Manifest.permission.POST_NOTIFICATIONS, requestCode);
    }

    public void requestFineLocationPermission(String requestCode) {
        List<String> permissions = new ArrayList<>();
        permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // On Android 12+ coarse location permission must be also requested
            permissions.add(ACCESS_COARSE_LOCATION);
        }
        requestPermissions(permissions, requestCode);
    }

    public void requestCoarseLocationPermission(String requestCode) {
        requestPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION, requestCode);
    }


    @RequiresApi(api = Build.VERSION_CODES.R)
    public void requestBackgroundLocationPermission(String requestCode) {
        List<String> permissions = new ArrayList<>();
        // Background location permission can only be granted when forward location is permitted
        if (hasForwardLocationPermission()) {
            permissions.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        } else {
            if (Logger.DEBUG) { Log.d(TAG, "[forward location permission denied]"); }
            permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // On Android 12+ coarse location permission must be also requested
                permissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
            }
        }

        if (permissions.contains(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) &&
                ActivityCompat.shouldShowRequestPermissionRationale(fragment.requireActivity(), android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            final CharSequence label = getBackgroundPermissionOptionLabel(fragment.requireContext());
            Alert.showConfirm(
                    fragment.requireContext(),
                    fragment.getString(R.string.background_location_required),
                    fragment.getString(R.string.background_location_rationale, label),
                    (dialog, which) -> {
                        dialog.dismiss();
                        requestPermissions(permissions, requestCode);
                    }
            );
        } else {
            requestPermissions(permissions, requestCode);
        }

    }

    public void requestPermission(String permission, String requestCode) {
        List<String> permissions = new ArrayList<>();
        permissions.add(permission);
        requestPermissions(permissions, requestCode);
    }

    public void requestPermissions(List<String> permissions, String requestCode) {
        this.requestCode = requestCode;
        resultLauncher.launch(permissions.toArray(new String[0]));
    }

    public boolean hasPermission(String permission) {
        Context context = fragment.getContext();
        if (context == null) {
            if (Logger.DEBUG) { Log.d(TAG, "[hasPermission: null context]"); }
            return false;
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check if user granted permission to access location.
     *
     * @return True if permission granted, false otherwise
     */
    boolean hasForwardLocationPermission() {
        boolean ret = (ActivityCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) ||
                (ActivityCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED);
        if (Logger.DEBUG) { Log.d(TAG, "[hasForwardLocationPermission: " + ret + "]"); }
        return ret;
    }

    /**
     * Check if user granted permission to access background location.
     *
     * @return True if permission granted, false otherwise
     */
    boolean hasBackgroundLocationPermission() {
        boolean ret = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ret = (ActivityCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED);
        }
        if (Logger.DEBUG) { Log.d(TAG, "[hasBackgroundLocationPermission: " + ret + "]"); }
        return ret;
    }

    public static boolean isLocationPermission(@NonNull String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ACCESS_BACKGROUND_LOCATION.equals(permission)) {
            return true;
        }
        return ACCESS_COARSE_LOCATION.equals(permission) || ACCESS_FINE_LOCATION.equals(permission);
    }

    /**
     * Wrapper for getBackgroundPermissionOptionLabel() method
     * Will return translated label only when context string was also translated
     * @param context Context
     * @return Localized label
     */
    @SuppressLint("AppBundleLocaleChanges")
    @RequiresApi(api = Build.VERSION_CODES.R)
    private CharSequence getBackgroundPermissionOptionLabel(Context context) {
        CharSequence label = context.getPackageManager().getBackgroundPermissionOptionLabel();
        CharSequence defaultLabel = "Allow all the time";

        if (Locale.getDefault().getLanguage().equals("en")) {
            return label.length() > 0 ? label : defaultLabel;
        }

        CharSequence translated = context.getString(R.string.background_location_rationale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(Locale.ENGLISH);
        CharSequence defaultText = context.createConfigurationContext(config).getText(R.string.background_location_rationale);

        return translated.equals(defaultText) ? defaultLabel : label;
    }
}
