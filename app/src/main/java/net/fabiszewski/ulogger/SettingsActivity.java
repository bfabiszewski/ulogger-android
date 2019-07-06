/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Adds preferences from xml resource
 *
 */

public class SettingsActivity extends AppCompatActivity {

    public static final String KEY_AUTO_START = "prefAutoStart";
    public static final String KEY_HOST = "prefHost";
    public static final String KEY_LIVE_SYNC = "prefLiveSync";
    public static final String KEY_MIN_ACCURACY = "prefMinAccuracy";
    public static final String KEY_MIN_DISTANCE = "prefMinDistance";
    public static final String KEY_MIN_TIME = "prefMinTime";
    public static final String KEY_IMAGE_SIZE = "prefImageSize";
    public static final String KEY_PASS = "prefPass";
    public static final String KEY_PROVIDER = "prefProvider";
    public static final String KEY_UNITS = "prefUnits";
    public static final String KEY_USERNAME = "prefUsername";
    public static final String KEY_USE_GPS = "prefUseGps";
    public static final String KEY_USE_NET = "prefUseNet";
    public static final String KEY_LOGGER_RUNNING = "prefLoggerRunning";
    public static final String KEY_ALLOW_EXTERNAL = "prefAllowExternal";
    public static final String KEY_AUTO_NAME = "prefAutoName";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new SettingsFragment())
                    .commit();
        }
    }


}
