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
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;


/**
 * List preference for location provider
 *
 */

public class ProviderPreference extends ListPreference {

    private final static int VALUE_GPS = 1;
    private final static int VALUE_NET = 2;
    private SharedPreferences prefs;

    /**
     * Constructor
     *
     * @param context Context
     * @param attrs Attributes
     */
    public ProviderPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    /**
     * Set preferences values based on user selection
     *
     * @param context Context
     */
    private void init(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                SharedPreferences.Editor editor = prefs.edit();
                int providersMask = Integer.valueOf((String) newValue);
                editor.putBoolean("prefUseGps", (providersMask & VALUE_GPS) == VALUE_GPS);
                editor.putBoolean("prefUseNet", (providersMask & VALUE_NET) == VALUE_NET);
                editor.apply();
                return true;
            }
        });
    }
}
