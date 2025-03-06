/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger.ui;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static net.fabiszewski.ulogger.ui.SettingsFragment.isValidServerSetup;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import net.fabiszewski.ulogger.Logger;
import net.fabiszewski.ulogger.R;
import net.fabiszewski.ulogger.WebAuthException;
import net.fabiszewski.ulogger.utils.PermissionHelper;
import net.fabiszewski.ulogger.utils.WebHelper;

import org.json.JSONException;

import java.io.IOException;

public class SelfCheckFragment extends Fragment implements PermissionHelper.PermissionRequester {

    private static final String TAG = SelfCheckFragment.class.getSimpleName();

    private SwipeRefreshLayout swipe;
    private TextView permissionsLabel;
    private TextView automatedUsageLabel;
    private View approximateLocationLayout;
    private TextView approximateLocationLabel;
    private SwitchCompat approximateLocationSwitch;
    private View preciseLocationLayout;
    private SwitchCompat preciseLocationSwitch;
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
        permissionsLabel = layout.findViewById(R.id.permissionsLabel);
        automatedUsageLabel = layout.findViewById(R.id.automatedUsageLabel);
        approximateLocationLayout = layout.findViewById(R.id.permissionApproximateLocation);
        approximateLocationSwitch = layout.findViewById(R.id.permissionApproximateLocationResult);
        approximateLocationLabel = layout.findViewById(R.id.permissionApproximateLocationLabel);
        preciseLocationLayout = layout.findViewById(R.id.permissionPreciseLocation);
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
        boolean isValidServerSetup = isValidServerSetup(requireContext());
        serverReachableDetails.setText("");
        serverReachableDetails.setVisibility(View.GONE);
        validAccountDetails.setText("");
        validAccountDetails.setVisibility(View.GONE);
        setupServerSwitch(serverConfiguredSwitch, isValidServerSetup);
        setupServerSwitch(serverReachableSwitch, false);
        setupServerSwitch(validAccountSwitch, false);

        if (isValidServerSetup) {
            setRefreshing(true);
            final Handler handler = new Handler(Looper.getMainLooper());

            new Thread(() -> serverThreadChecks(handler)).start();
        } else {
            setRefreshing(false);
        }
    }

    private void serverThreadChecks(@NonNull Handler handler) {
        final WebHelper webHelper = new WebHelper(requireContext());
        boolean isReachable = false;
        String details = null;
        try {
            isReachable = webHelper.isReachable();
        } catch (IOException e) {
            details = e.getLocalizedMessage();
        }
        postServerCheckResults(handler, serverReachableDetails, serverReachableSwitch, details, isReachable);
        if (isReachable) {
            boolean isValidAccount = false;
            try {
                webHelper.checkAuthorization();
                isValidAccount = true;
            } catch (IOException | WebAuthException | JSONException e) {
                details = e.getLocalizedMessage();
            }
            postServerCheckResults(handler, validAccountDetails, validAccountSwitch, details, isValidAccount);
        }
        handler.post(() -> setRefreshing(false));
    }

    private void postServerCheckResults(@NonNull Handler handler, @NonNull TextView textView,
                                        @NonNull SwitchCompat switchCompat, @Nullable String details, boolean state) {
        handler.post(() -> {
            if (details != null) {
                textView.setVisibility(View.VISIBLE);
                textView.setText(details);
            }
            setSwitch(switchCompat, state);
        });
    }

    private void setupServerSwitch(@NonNull SwitchCompat serverSwitch, boolean checked) {
        serverSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            boolean isSetProgrammatically = view.getTag() != null && (boolean) view.getTag();
            if (isSetProgrammatically) {
                view.setTag(false);
            } else if (isChecked) {
                Intent intent = new Intent(getContext(), SettingsActivity.class);
                preferencesLauncher.launch(intent);
            }
        });
        setSwitch(serverSwitch, checked);
        disableSwitchIfChecked(serverSwitch);
    }

    private void setSwitch(@NonNull SwitchCompat switchCompat, boolean checked) {
        if (switchCompat.isChecked() != checked) {
            switchCompat.setTag(true);
            switchCompat.setChecked(checked);
        }
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

    private void checkProvider(@NonNull String provider, @NonNull SwitchCompat providerSwitch) {
        providerSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            boolean isSetProgrammatically = view.getTag() != null && (boolean) view.getTag();
            if (isSetProgrammatically) {
                view.setTag(false);
            } else if (isChecked) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                locationSettingsLauncher.launch(intent);
            }
        });
        LocationManager locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        setSwitch(providerSwitch, locationManager.isProviderEnabled(provider));
        disableSwitchIfChecked(providerSwitch);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkPermissions() {
        permissionsLabel.setVisibility(View.VISIBLE);
        automatedUsageLabel.setVisibility(View.VISIBLE);

        setItem(approximateLocationLayout, approximateLocationLabel, approximateLocationSwitch, ACCESS_COARSE_LOCATION);
        setItem(preciseLocationLayout, preciseLocationLabel, preciseLocationSwitch, ACCESS_FINE_LOCATION);

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
        switchCompat.setOnCheckedChangeListener((view, isChecked) -> {
            boolean isSetProgrammatically = view.getTag() != null && (boolean) view.getTag();
            if (isSetProgrammatically) {
                view.setTag(false);
            } else if (isChecked) {
                switch (permission) {
                    case ACCESS_COARSE_LOCATION ->
                            permissionHelper.requestCoarseLocationPermission();
                    case ACCESS_FINE_LOCATION -> permissionHelper.requestFineLocationPermission();
                    case ACCESS_BACKGROUND_LOCATION -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            permissionHelper.requestBackgroundLocationPermission();
                        }
                    }
                    case WRITE_EXTERNAL_STORAGE ->
                            permissionHelper.requestWriteExternalStoragePermission();
                    case POST_NOTIFICATIONS -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionHelper.requestNotificationsPermission();
                        }
                    }
                }
            }
        });
        setSwitch(switchCompat, permissionHelper.hasPermission(permission));
        disableSwitchIfChecked(switchCompat);
        label.setText(getPermissionLabel(permission));
    }

    @SuppressLint("ClickableViewAccessibility")
    private void disableSwitchIfChecked(@NonNull SwitchCompat switchCompat) {
        switchCompat.setOnTouchListener((view, event) -> {
            if (switchCompat.isChecked()) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    setSwitch(switchCompat, true);
                }
                return true;
            }
            return false;
        });
    }

    @NonNull
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
        batteryUsageSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            boolean isSetProgrammatically = view.getTag() != null && (boolean) view.getTag();
            if (isSetProgrammatically) {
                view.setTag(false);
            } else if (isChecked) {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                batterySettingsLauncher.launch(intent);
            }
        });
        PowerManager pm = (PowerManager) requireContext().getSystemService(Context.POWER_SERVICE);
        boolean isIgnoringOptimizations = pm.isIgnoringBatteryOptimizations(requireContext().getPackageName());
        setSwitch(batteryUsageSwitch, isIgnoringOptimizations);
        disableSwitchIfChecked(batteryUsageSwitch);
        if (Logger.DEBUG) { Log.d(TAG, "[isIgnoringOptimizations: " + isIgnoringOptimizations + " ]"); }
    }

    @Override
    public void onPermissionGranted(@Nullable String requestCode) {
        if (Logger.DEBUG) { Log.d(TAG, "[onPermissionGranted]"); }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermissions();
        }
    }

    @Override
    public void onPermissionDenied(@Nullable String requestCode) {
        if (Logger.DEBUG) { Log.d(TAG, "[onPermissionDenied]"); }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermissions();
        }
    }
}
