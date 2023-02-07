/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import org.json.JSONException;

import java.io.IOException;

public class SelfCheckFragment extends Fragment {

    private static final String TAG = SelfCheckFragment.class.getSimpleName();

    private SwitchCompat preciseLocationSwitch;
    private TextView approximateLocationLabel;
    private SwitchCompat approximateLocationSwitch;
    private TextView preciseLocationLabel;
    private View backgroundLocationLayout;
    private TextView backgroundLocationLabel;
    private SwitchCompat backgroundLocationSwitch;
    private View storageLayout;
    private TextView storageLabel;
    private SwitchCompat storageSwitch;
    private View notificationsLayout;
    private TextView notificationsLabel;
    private SwitchCompat notificationsSwitch;

    private SwitchCompat locationGpsSwitch;
    private SwitchCompat locationNetSwitch;
    private SwitchCompat serverConfiguredSwitch;
    private SwitchCompat serverReachableSwitch;
    private TextView serverReachableDetails;
    private TextView validAccountDetails;
    private SwitchCompat validAccountSwitch;
    private View batteryUsageLayout;
    private SwitchCompat batteryUsageSwitch;


    public SelfCheckFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (Logger.DEBUG) { Log.d(TAG, "[onCreateView]"); }
        View layout = inflater.inflate(R.layout.fragment_self_check, container, false);
        approximateLocationSwitch = layout.findViewById(R.id.permissionApproximateLocationResult);
        approximateLocationLabel = layout.findViewById(R.id.permissionApproximateLocationLabel);
        preciseLocationSwitch = layout.findViewById(R.id.permissionPreciseLocationResult);
        preciseLocationLabel = layout.findViewById(R.id.permissionPreciseLocationLabel);
        backgroundLocationLayout = layout.findViewById(R.id.permissionBackgroundLocationSelfCheck);
        backgroundLocationLabel = layout.findViewById(R.id.permissionBackgroundLocationLabel);
        backgroundLocationSwitch = layout.findViewById(R.id.permissionBackgroundLocationResult);
        storageLayout = layout.findViewById(R.id.permissionStorageSelfCheck);
        storageLabel = layout.findViewById(R.id.permissionStorageLabel);
        storageSwitch = layout.findViewById(R.id.permissionStorageResult);
        notificationsLayout = layout.findViewById(R.id.permissionNotificationsSelfCheck);
        notificationsLabel = layout.findViewById(R.id.permissionNotificationsLabel);
        notificationsSwitch = layout.findViewById(R.id.permissionNotificationsResult);
        locationGpsSwitch = layout.findViewById(R.id.providerGpsResult);
        locationNetSwitch = layout.findViewById(R.id.providerNetResult);
        serverConfiguredSwitch = layout.findViewById(R.id.serverConfiguredResult);
        serverReachableSwitch = layout.findViewById(R.id.serverReachableResult);
        serverReachableDetails = layout.findViewById(R.id.serverReachableDetails);
        validAccountDetails = layout.findViewById(R.id.validAccountDetails);
        validAccountSwitch = layout.findViewById(R.id.validAccountResult);
        batteryUsageLayout = layout.findViewById(R.id.batteryUnrestrictedSelfCheck);
        batteryUsageSwitch = layout.findViewById(R.id.batteryUnrestrictedUsageResult);

        selfCheck();

        return layout;
    }

    private void selfCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermissions();
        }

        checkProviders();

        checkServer();
    }

    private void checkServer() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String host = prefs.getString(SettingsActivity.KEY_HOST, "").replaceAll("/+$", "");

        serverConfiguredSwitch.setChecked(!host.isEmpty());
        serverReachableSwitch.setChecked(false);
        validAccountSwitch.setChecked(false);
        if (!host.isEmpty()) {
            Handler handler = new Handler(Looper.getMainLooper());

            new Thread(() -> {
                WebHelper webHelper = new WebHelper(requireContext());
                boolean isReachable = false;
                String details = null;
                try {
                    isReachable = webHelper.isReachable();
                } catch (IOException e) {
                    details = e.getLocalizedMessage();
                }
                boolean finalIsReachable = isReachable;
                String finalDetails = details;
                handler.post(() -> {
                    if (finalDetails != null) {
                        serverReachableDetails.setText(finalDetails);
                    }
                    serverReachableSwitch.setChecked(finalIsReachable);
                });
                if (isReachable) {
                    boolean isValidAccount = false;
                    try {
                        webHelper.checkAuthorization();
                        isValidAccount = true;
                    } catch (IOException | WebAuthException | JSONException e) {
                        details = e.getLocalizedMessage();
                    }
                    boolean finalIsValidAccount = isValidAccount;
                    String finalAccountDetails = details;
                    handler.post(() -> {
                        if (finalAccountDetails != null) {
                            validAccountDetails.setText(finalAccountDetails);
                        }
                        validAccountSwitch.setChecked(finalIsValidAccount);
                    });
                }
            }).start();

        }
    }

    private void checkProviders() {
        LocationManager locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        locationGpsSwitch.setChecked(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER));
        locationNetSwitch.setChecked(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkPermissions() {
        setItem(null, approximateLocationLabel, approximateLocationSwitch, ACCESS_COARSE_LOCATION);
        setItem(null, preciseLocationLabel, preciseLocationSwitch, ACCESS_FINE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setItem(backgroundLocationLayout, backgroundLocationLabel, backgroundLocationSwitch, ACCESS_BACKGROUND_LOCATION);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            setItem(storageLayout, storageLabel, storageSwitch, WRITE_EXTERNAL_STORAGE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setItem(notificationsLayout, notificationsLabel, notificationsSwitch, POST_NOTIFICATIONS);
        }
        checkBatteryUsage();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void setItem(@Nullable View layout, @NonNull TextView label, @NonNull SwitchCompat switchCompat, @NonNull String permission) {
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
        }
        switchCompat.setChecked(isAllowed(permission));
        label.setText(getPermissionLabel(permission));
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean isAllowed(@NonNull String permission) {
        boolean isAllowed =  ActivityCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED;
        if (Logger.DEBUG) { Log.d(TAG, "[isAllowed " + permission + ": " + isAllowed + " ]"); }
        return isAllowed;
    }

    private CharSequence getPermissionLabel(@NonNull String permission) {
        try {
            PackageManager pm = requireContext().getPackageManager();
            PermissionInfo info = pm.getPermissionInfo(permission, 0);
            CharSequence label = info.loadLabel(pm);
            return new StringBuilder(label.length())
                    .appendCodePoint(Character.toTitleCase(Character.codePointAt(label, 0)))
                    .append(label, Character.offsetByCodePoints(label, 0, 1), label.length());
        } catch (PackageManager.NameNotFoundException e) {
            if (Logger.DEBUG) { Log.d(TAG, "[getPermissionLabel not found:" + e + "]"); }
        }
        return permission;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkBatteryUsage() {
        batteryUsageLayout.setVisibility(View.VISIBLE);
        PowerManager pm = (PowerManager) requireContext().getSystemService(Context.POWER_SERVICE);
        boolean isIgnoringOptimizations = pm.isIgnoringBatteryOptimizations(requireContext().getPackageName());
        batteryUsageSwitch.setChecked(isIgnoringOptimizations);
        if (Logger.DEBUG) { Log.d(TAG, "[isIgnoringOptimizations: " + isIgnoringOptimizations + " ]"); }
    }


}
