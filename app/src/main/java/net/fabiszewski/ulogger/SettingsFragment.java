/*
 * Copyright (c) 2018 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.TwoStatePreference;

import static net.fabiszewski.ulogger.SettingsActivity.*;

@SuppressWarnings("WeakerAccess")
public class SettingsFragment extends PreferenceFragmentCompat {
    private static final String TAG = SettingsFragment.class.getSimpleName();

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
            if (getFragmentManager() != null) {
                fragment.show(getFragmentManager(), "UrlPreferenceDialogFragment");
            }
        } else if (preference instanceof ListPreference && KEY_PROVIDER.equals(preference.getKey())) {
            final ProviderPreferenceDialogFragment fragment = ProviderPreferenceDialogFragment.newInstance(preference.getKey());
            fragment.setTargetFragment(this, 0);
            if (getFragmentManager() != null) {
                fragment.show(getFragmentManager(), "ProviderPreferenceDialogFragment");
            }
        } else if (preference instanceof ListPreference) {
            final ListPreferenceDialogWithMessageFragment fragment = ListPreferenceDialogWithMessageFragment.newInstance(preference.getKey());
            fragment.setTargetFragment(this, 0);
            if (getFragmentManager() != null) {
                fragment.show(getFragmentManager(), "ListPreferenceDialogWithMessageFragment");
            }
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
    private final Preference.OnPreferenceChangeListener liveSyncChanged = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            final Context context = preference.getContext();
            if (Boolean.parseBoolean(newValue.toString())) {
                if (!isValidServerSetup(context)) {
                    Toast.makeText(context, R.string.provide_user_pass_url, Toast.LENGTH_LONG).show();
                    return false;
                }
            }
            return true;
        }
    };

    /**
     * On change listener to destroy session cookies if server setup has changed
     */
    private final Preference.OnPreferenceChangeListener serverSetupChanged = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            // remove session cookies
            WebHelper.deauthorize();
            // disable live synchronization if any server preference is removed
            if (newValue.toString().trim().length() == 0) {
                disableLiveSync(preference.getContext());
            }
            return true;
        }

    };

    /**
     * On click listener to warn if server setup has changed
     */
    private final Preference.OnPreferenceClickListener serverSetupClicked = new Preference.OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
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
        }

    };

    /**
     * Disable live sync preference, reset checkbox
     * @param context Context
     */
    private void disableLiveSync(Context context) {
        if (Logger.DEBUG) { Log.d(TAG, "[disabling live sync]"); }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_LIVE_SYNC, false);
        editor.apply();

        final Preference prefLiveSync = findPreference(KEY_LIVE_SYNC);
        if (prefLiveSync instanceof TwoStatePreference) {
            ((TwoStatePreference) prefLiveSync).setChecked(false);
        }
    }

    /**
     * Check whether server setup parameters are set
     * @param context Context
     * @return boolean True if set
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isValidServerSetup(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String host = prefs.getString(KEY_HOST, null);
        final String user = prefs.getString(KEY_USERNAME, null);
        final String pass = prefs.getString(KEY_PASS, null);
        return ((host != null && !host.isEmpty())
                && (user != null && !user.isEmpty())
                && (pass != null && !pass.isEmpty()));
    }
}
