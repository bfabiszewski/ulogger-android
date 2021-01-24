/*
 * Copyright (c) 2018 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.TwoStatePreference;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static net.fabiszewski.ulogger.SettingsActivity.KEY_ALLOW_EXTERNAL;
import static net.fabiszewski.ulogger.SettingsActivity.KEY_AUTO_NAME;
import static net.fabiszewski.ulogger.SettingsActivity.KEY_AUTO_START;
import static net.fabiszewski.ulogger.SettingsActivity.KEY_HOST;
import static net.fabiszewski.ulogger.SettingsActivity.KEY_LIVE_SYNC;
import static net.fabiszewski.ulogger.SettingsActivity.KEY_PASS;
import static net.fabiszewski.ulogger.SettingsActivity.KEY_PROVIDER;
import static net.fabiszewski.ulogger.SettingsActivity.KEY_USERNAME;

@SuppressWarnings("WeakerAccess")
public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String TAG = SettingsFragment.class.getSimpleName();

    private static final Map<String, Integer> PERMISSION_CODES;
    static {
        Map<String, Integer> map = new HashMap<>();
        map.put(KEY_AUTO_START, 1);
        map.put(KEY_ALLOW_EXTERNAL, 2);
        PERMISSION_CODES = Collections.unmodifiableMap(map);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        setListeners();
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (preference instanceof EditTextPreference && KEY_HOST.equals(preference.getKey())) {
            final UrlPreferenceDialogFragment fragment = UrlPreferenceDialogFragment.newInstance(preference.getKey());
            fragment.setTargetFragment(this, 0);
            fragment.show(getParentFragmentManager(), "UrlPreferenceDialogFragment");
        } else if (preference instanceof AutoNamePreference && KEY_AUTO_NAME.equals(preference.getKey())) {
            final AutoNamePreferenceDialogFragment fragment = AutoNamePreferenceDialogFragment.newInstance(preference.getKey());
            fragment.setTargetFragment(this, 0);
            fragment.show(getParentFragmentManager(), "AutoNamePreferenceDialogFragment");
        } else if (preference instanceof ListPreference && KEY_PROVIDER.equals(preference.getKey())) {
            final ProviderPreferenceDialogFragment fragment = ProviderPreferenceDialogFragment.newInstance(preference.getKey());
            fragment.setTargetFragment(this, 0);
            fragment.show(getParentFragmentManager(), "ProviderPreferenceDialogFragment");
        } else if (preference instanceof ListPreference) {
            final ListPreferenceDialogWithMessageFragment fragment = ListPreferenceDialogWithMessageFragment.newInstance(preference.getKey());
            fragment.setTargetFragment(this, 0);
            fragment.show(getParentFragmentManager(), "ListPreferenceDialogWithMessageFragment");
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    /**
     * Set various listeners
     */
    private void setListeners() {
        final Preference prefLiveSync = findPreference(KEY_LIVE_SYNC);
        final Preference prefUsername = findPreference(KEY_USERNAME);
        final Preference prefPass = findPreference(KEY_PASS);
        final Preference prefHost = findPreference(KEY_HOST);
        // on change listeners
        if (prefLiveSync != null) {
            prefLiveSync.setOnPreferenceChangeListener(liveSyncChanged);
        }
        if (prefUsername != null) {
            prefUsername.setOnPreferenceChangeListener(serverSetupChanged);
        }
        if (prefPass != null) {
            prefPass.setOnPreferenceChangeListener(serverSetupChanged);
        }
        if (prefHost != null) {
            prefHost.setOnPreferenceChangeListener(serverSetupChanged);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final Preference prefAutoStart = findPreference(KEY_AUTO_START);
            final Preference prefAllowExternal = findPreference(KEY_ALLOW_EXTERNAL);
            if (prefAutoStart != null) {
                prefAutoStart.setOnPreferenceChangeListener(permissionLevelChanged);
            }
            if (prefAllowExternal != null) {
                prefAllowExternal.setOnPreferenceChangeListener(permissionLevelChanged);
            }
        }
        // on click listeners
        if (prefUsername != null) {
            prefUsername.setOnPreferenceClickListener(serverSetupClicked);
        }
        if (prefHost != null) {
            prefHost.setOnPreferenceClickListener(serverSetupClicked);
        }
    }

    /**
     * On change listener to validate whether live synchronization is allowed
     */
    private final Preference.OnPreferenceChangeListener liveSyncChanged = (preference, newValue) -> {
        final Context context = preference.getContext();
        if (Boolean.parseBoolean(newValue.toString())) {
            if (!isValidServerSetup(context)) {
                Toast.makeText(context, R.string.provide_user_pass_url, Toast.LENGTH_LONG).show();
                return false;
            }
        }
        return true;
    };

    /**
     * On change listener to destroy session cookies if server setup has changed
     */
    private final Preference.OnPreferenceChangeListener serverSetupChanged = (preference, newValue) -> {
        // update web helper settings, remove session cookies
        WebHelper.updatePreferences(preference.getContext());
        // disable live synchronization if any server preference is removed
        if (newValue.toString().trim().length() == 0) {
            disableLiveSync(preference.getContext());
        }
        return true;
    };

    /**
     * On change listener to check permission for background location
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    private final Preference.OnPreferenceChangeListener permissionLevelChanged = (preference, newValue) -> {
        final Context context = preference.getContext();
        if (Boolean.parseBoolean(newValue.toString())) {
            if (!hasBackgroundLocationPermission(context)) {
                requestBackgroundLocationPermission(context, preference.getKey());
                return false;
            }
        }
        return true;
    };

    /**
     * On click listener to warn if server setup has changed
     */
    private final Preference.OnPreferenceClickListener serverSetupClicked = preference -> {
        final Context context = preference.getContext();
        DbAccess db = DbAccess.getInstance();
        db.open(context);
        if (db.getTrackId() > 0) {
            // track saved on server
            Alert.showInfo(context,
                    context.getString(R.string.warning),
                    context.getString(R.string.track_server_setup_warning)
            );

        }
        return true;
    };

    /**
     * Disable live sync preference, reset checkbox
     * @param context Context
     */
    private void disableLiveSync(@NonNull Context context) {
        setBooleanPreference(context, KEY_LIVE_SYNC, false);
    }

    /**
     * Enable preference, set checkbox
     * @param context Context
     */
    private void setBooleanPreference(@NonNull Context context, @NonNull String key, boolean isSet) {
        if (Logger.DEBUG) { Log.d(TAG, "[enabling " + key + "]"); }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(key, isSet);
        editor.apply();

        final Preference preference = findPreference(key);
        if (preference instanceof TwoStatePreference) {
            ((TwoStatePreference) preference).setChecked(isSet);
        }
    }

    /**
     * Check whether server setup parameters are set
     * @param context Context
     * @return boolean True if set
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isValidServerSetup(@NonNull Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String host = prefs.getString(KEY_HOST, null);
        final String user = prefs.getString(KEY_USERNAME, null);
        final String pass = prefs.getString(KEY_PASS, null);
        return ((host != null && !host.isEmpty())
                && (user != null && !user.isEmpty())
                && (pass != null && !pass.isEmpty()));
    }

    /**
     * Check whether user granted background location permission.
     * Background location permission only needed on API 30+
     * when application is started without user interaction
     *
     * @param context Context
     * @return True if has permission, false otherwise
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    @SuppressWarnings({"BooleanMethodIsAlwaysInverted", "RedundantSuppression"})
    private boolean hasBackgroundLocationPermission(@NonNull Context context) {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check whether user granted location permission
     * @param context Context
     * @return True if has permission, false otherwise
     */
    private boolean hasForwardLocationPermission(@NonNull Context context) {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request permission when given preference key changed
     * @param context Context
     * @param key Key
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    private void requestBackgroundLocationPermission(@NonNull Context context, @NonNull String key) {
        String permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION;
        // Background location permission can only be granted when forward location is permitted
        if (!hasForwardLocationPermission(context)) {
            if (Logger.DEBUG) { Log.d(TAG, "[forward location permission denied]"); }
            permission = Manifest.permission.ACCESS_FINE_LOCATION;
        }

        Integer requestCode = PERMISSION_CODES.get(key);
        if (requestCode != null) {
            if (permission.equals(Manifest.permission.ACCESS_BACKGROUND_LOCATION) &&
                    ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                final CharSequence option = getBackgroundPermissionOptionLabel(context);
                Alert.showConfirm(
                        context,
                        getString(R.string.background_location_required),
                        getString(R.string.background_location_rationale, option),
                        (dialog, which) -> {
                            dialog.dismiss();
                            requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, requestCode);
                        }
                );
            } else {
                if (Logger.DEBUG) {
                    Log.d(TAG, "[request permission " + permission + ", code " + PERMISSION_CODES.get(key) + "]");
                }
                requestPermissions(new String[]{permission}, requestCode);
            }
        }
    }

    /**
     * Wrapper for getBackgroundPermissionOptionLabel() method
     * Will return translated label only when context string was also translated
     * @param context Context
     * @return Localized label
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    private CharSequence getBackgroundPermissionOptionLabel(Context context) {
        CharSequence option = context.getPackageManager().getBackgroundPermissionOptionLabel();

        if (Locale.getDefault().getLanguage().equals("en")) {
            return option;
        }

        CharSequence translated = context.getString(R.string.background_location_rationale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(Locale.ENGLISH);
        CharSequence defaultText = context.createConfigurationContext(config).getText(R.string.background_location_rationale);

        return translated.equals(defaultText) ? "Allow all the time" : option;
    }

    /**
     * Callback for the result from requesting permissions.
     * @param requestCode  The request code passed in requestPermissions()
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (Logger.DEBUG) { Log.d(TAG, "[onRequestPermissionsResult: " + requestCode + ", permissions: " + Arrays.toString(permissions) + "]"); }

        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            if (Logger.DEBUG) { Log.d(TAG, "[onRequestPermissionsResult: refused]"); }
            return;
        }

        String preferenceKey = null;
        for (Map.Entry<String, Integer> entry : PERMISSION_CODES.entrySet()) {
            if (requestCode == entry.getValue()) {
                preferenceKey = entry.getKey();
                break;
            }
        }

        Context context = getContext();

        if (preferenceKey != null && context != null) {
            if (Arrays.asList(permissions).contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
                requestBackgroundLocationPermission(context, preferenceKey);
            } else if (Arrays.asList(permissions).contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                setBooleanPreference(context, preferenceKey, true);
            }
        }
    }

}
