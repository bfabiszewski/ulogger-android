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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONException;

import java.io.IOException;

public class SelfCheckFragment extends Fragment implements PermissionHelper.PermissionRequester {

    private static final String TAG = SelfCheckFragment.class.getSimpleName();

    private SwipeRefreshLayout swipe;
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
    final PermissionHelper permissionHelper;

    public SelfCheckFragment() {
        permissionHelper = new PermissionHelper(this, this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (Logger.DEBUG) { Log.d(TAG, "[onCreateView]"); }
        View layout = inflater.inflate(R.layout.fragment_self_check, container, false);
        swipe = (SwipeRefreshLayout) layout;
        swipe.setOnRefreshListener(this::selfCheck);
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

    public void selfCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermissions();
        }
        checkProviders();
        checkServer();
    }

    public void setRefreshing(boolean refreshing) {
        swipe.setRefreshing(refreshing);
    }

    private void checkServer() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String host = prefs.getString(SettingsActivity.KEY_HOST, "").replaceAll("/+$", "");

        setupServerSwitch(serverConfiguredSwitch, !host.isEmpty());
        setupServerSwitch(serverReachableSwitch, false);
        setupServerSwitch(validAccountSwitch, false);

        if (!host.isEmpty()) {
            setRefreshing(true);
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
                handler.post(() -> setRefreshing(false));
            }).start();
        } else {
            setRefreshing(false);
        }
    }

    private void setupServerSwitch(SwitchCompat serverSwitch, boolean state) {
        serverSwitch.setChecked(state);
        disableSwitch(serverSwitch);
        serverSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            if (isChecked) {
                Intent intent = new Intent(getContext(), SettingsActivity.class);
                preferencesLauncher.launch(intent);
            }
        });
    }

    final ActivityResultLauncher<Intent> preferencesLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> checkServer());

    final ActivityResultLauncher<Intent> locationSettingsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (Logger.DEBUG) { Log.d(TAG, "[locationSettingsLauncher result: " + result.getResultCode() + "]"); }
                checkProviders();

            });

    final ActivityResultLauncher<Intent> batterySettingsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    checkBatteryUsage();
                }
            });

    private void checkProviders() {
        checkProvider(LocationManager.GPS_PROVIDER, locationGpsSwitch);
        checkProvider(LocationManager.NETWORK_PROVIDER, locationNetSwitch);
    }

    private void checkProvider(String provider, SwitchCompat providerSwitch) {
        LocationManager locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        boolean isGpsProviderEnabled = locationManager.isProviderEnabled(provider);
        providerSwitch.setChecked(isGpsProviderEnabled);
        disableSwitch(providerSwitch);
        if (!isGpsProviderEnabled) {
            providerSwitch.setOnCheckedChangeListener((view, isChecked) -> {
                if (isChecked) {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    locationSettingsLauncher.launch(intent);
                }
            });
        }
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

    private void setItem(@Nullable View layout, @NonNull TextView label, @NonNull SwitchCompat switchCompat, @NonNull String permission) {
        if (layout != null) {
            layout.setVisibility(View.VISIBLE);
        }
        boolean hasPermission = permissionHelper.hasPermission(permission);
        switchCompat.setChecked(hasPermission);
        disableSwitch(switchCompat);
        label.setText(getPermissionLabel(permission));
        if (!hasPermission) {
            switchCompat.setOnCheckedChangeListener((view, isChecked) -> {
                if (isChecked) {
                    switch (permission) {
                        case ACCESS_COARSE_LOCATION:
                            permissionHelper.requestCoarseLocationPermission();
                            break;
                        case ACCESS_FINE_LOCATION:
                            permissionHelper.requestFineLocationPermission();
                            break;
                        case ACCESS_BACKGROUND_LOCATION:
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                permissionHelper.requestBackgroundLocationPermission();
                            }
                            break;
                        case WRITE_EXTERNAL_STORAGE:
                            permissionHelper.requestWriteExternalStoragePermission();
                            break;
                        case POST_NOTIFICATIONS:
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionHelper.requestNotificationsPermission();
                            }
                            break;
                    }
                }
            });
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void disableSwitch(@NonNull SwitchCompat switchCompat) {
        switchCompat.setOnTouchListener((view, event) -> {
            if (switchCompat.isChecked()) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    switchCompat.setChecked(true);
                }
                return true;
            }
            return false;
        });
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
        disableSwitch(batteryUsageSwitch);
        if (!isIgnoringOptimizations) {
            batteryUsageSwitch.setOnCheckedChangeListener((view, isChecked) -> {
                if (isChecked) {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    batterySettingsLauncher.launch(intent);
                }
            });
        }
        if (Logger.DEBUG) { Log.d(TAG, "[isIgnoringOptimizations: " + isIgnoringOptimizations + " ]"); }
    }

    @Override
    public void onPermissionGranted(String requestCode) {
        if (Logger.DEBUG) { Log.d(TAG, "[onPermissionGranted]"); }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermissions();
        }
    }

    @Override
    public void onPermissionDenied(String requestCode) {
        if (Logger.DEBUG) { Log.d(TAG, "[onPermissionDenied]"); }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermissions();
        }
    }
}
