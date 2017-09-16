/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.Toast;

/**
 * Adds preferences from xml resource
 *
 */

public class SettingsActivity extends PreferenceActivity {

    private static Preference prefLiveSync = null;
    private static Preference prefUsername = null;
    private static Preference prefPass = null;
    private static Preference prefHost = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            onCreatePreferenceActivity();
        } else {
            onCreatePreferenceFragment();
        }

    }

    // API = 10
    @SuppressWarnings("deprecation")
    private void onCreatePreferenceActivity() {
        addPreferencesFromResource(R.xml.preferences);
        prefLiveSync = findPreference("prefLiveSync");
        prefUsername = findPreference("prefUsername");
        prefPass = findPreference("prefPass");
        prefHost = findPreference("prefHost");
        setListeners();
    }

    // API >= 11
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void onCreatePreferenceFragment() {
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new MyPreferenceFragment())
                .commit();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
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
