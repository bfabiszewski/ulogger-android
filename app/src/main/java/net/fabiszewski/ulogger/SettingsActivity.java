/*
 * Copyright (c) 2017 Bartek Fabiszewski
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
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Adds preferences from xml resource
 *
 */

public class SettingsActivity extends PreferenceActivity {
    private static final String TAG = SettingsActivity.class.getSimpleName();

    private static Preference prefLiveSync = null;
    private static Preference prefUsername = null;
    private static Preference prefPass = null;
    private static Preference prefHost = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onCreatePreferenceFragment();
    }

    private void onCreatePreferenceFragment() {
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new MyPreferenceFragment())
                .commit();
    }

    public static class MyPreferenceFragment extends PreferenceFragment {

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            prefLiveSync = findPreference("prefLiveSync");
            prefUsername = findPreference("prefUsername");
            prefPass = findPreference("prefPass");
            prefHost = findPreference("prefHost");
            setListeners();
        }
    }

    /**
     * Set various listeners
     */
    private static void setListeners() {
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
    private final static Preference.OnPreferenceChangeListener liveSyncChanged = new Preference.OnPreferenceChangeListener() {
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
    private final static Preference.OnPreferenceChangeListener serverSetupChanged = new Preference.OnPreferenceChangeListener() {
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

    private static void disableLiveSync(Context context) {
        if (Logger.DEBUG) { Log.d(TAG, "[disabling live sync]"); }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("prefLiveSync", false);
        editor.apply();
        if (prefLiveSync != null && prefLiveSync instanceof CheckBoxPreference) {
            ((CheckBoxPreference) prefLiveSync).setChecked(false);
        }
    }

    /**
     * On click listener to warn if server setup has changed
     */
    private final static Preference.OnPreferenceClickListener serverSetupClicked = new Preference.OnPreferenceClickListener() {
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
     * Check whether server setup parameters are set
     * @param context Context
     * @return boolean True if set
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean isValidServerSetup(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String host = prefs.getString("prefHost", null);
        final String user = prefs.getString("prefUsername", null);
        final String pass = prefs.getString("prefPass", null);
        return ((host != null && !host.isEmpty())
                && (user != null && !user.isEmpty())
                && (pass != null && !pass.isEmpty()));
    }
}
