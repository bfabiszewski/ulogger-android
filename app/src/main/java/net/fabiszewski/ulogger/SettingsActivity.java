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
import android.util.Patterns;
import android.widget.Toast;

/**
 * Adds preferences from xml resource
 *
 */

public class SettingsActivity extends PreferenceActivity {

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
        final Preference prefHost = findPreference("prefHost");
        if (prefHost != null) {
            prefHost.setOnPreferenceChangeListener(hostChanged);
        }
        final Preference prefLiveSync = findPreference("prefLiveSync");
        if (prefLiveSync != null) {
            prefLiveSync.setOnPreferenceChangeListener(liveSyncChanged);
        }
    }

    /**
     * On change listener to validate server url
     */
    private final static Preference.OnPreferenceChangeListener hostChanged = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (Patterns.WEB_URL.matcher(newValue.toString().trim()).matches()) {
                return true;
            } else {
                Toast.makeText(preference.getContext(), R.string.provide_valid_url, Toast.LENGTH_LONG).show();
                return false;
            }
        }

    };

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

    static boolean isValidServerSetup(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String host = prefs.getString("prefHost", null);
        final String user = prefs.getString("prefUsername", null);
        final String pass = prefs.getString("prefPass", null);
        return ((host != null && !host.isEmpty())
                && (user != null && !user.isEmpty())
                && (pass != null && !pass.isEmpty()));
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
            final Preference prefHost = findPreference("prefHost");
            if (prefHost != null) {
                prefHost.setOnPreferenceChangeListener(hostChanged);
            }
            final Preference prefLiveSync = findPreference("prefLiveSync");
            if (prefLiveSync != null) {
                prefLiveSync.setOnPreferenceChangeListener(liveSyncChanged);
            }
        }

    }
}
