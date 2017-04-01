/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.LocationManager;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListAdapter;

import java.util.ArrayList;


/**
 * List preference for location provider
 *
 */

class ProviderPreference extends ListPreference {

    private static final String TAG = ProviderPreference.class.getSimpleName();

    private final static int VALUE_GPS = 1;
    private final static int VALUE_NET = 2;
    private final static int VALUE_ALL = (VALUE_GPS | VALUE_NET);

    private final LayoutInflater mInflater;

    private CharSequence[] entries;
    private CharSequence[] entryValues;
    private int currentIndex;
    private int defaultValue;

    private final ArrayList<Integer> missingProviders;

    /**
     * Constructor
     *
     * @param context Context
     * @param attrs Attributes
     */
    public ProviderPreference(final Context context, AttributeSet attrs) {
        super(context, attrs);
        mInflater = LayoutInflater.from(context);

        LocationManager locManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        missingProviders = new ArrayList<>();
        final boolean existsGPS = locManager.getAllProviders().contains(LocationManager.GPS_PROVIDER);
        final boolean existsNet = locManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER);
        if (Logger.DEBUG) { Log.d(TAG, "[Providers available: " + locManager.getAllProviders()); }
        defaultValue = VALUE_ALL;
        if (!existsGPS) {
            missingProviders.add(VALUE_GPS);
            missingProviders.add(VALUE_ALL);
            defaultValue ^= VALUE_GPS;
        }
        if (!existsNet) {
            missingProviders.add(VALUE_NET);
            missingProviders.add(VALUE_ALL);
            defaultValue ^= VALUE_NET;
        }

        setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (Logger.DEBUG) { Log.d(TAG, "[preference changed: " + newValue + "]"); }
                int providersMask = Integer.valueOf((String) newValue);
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("prefUseGps", (providersMask & VALUE_GPS) == VALUE_GPS);
                editor.putBoolean("prefUseNet", (providersMask & VALUE_NET) == VALUE_NET);
                editor.apply();
                return true;
            }
        });
    }

    /**
     * Use custom adapter
     *
     * @param builder Builder
     */
    @Override
    protected void onPrepareDialogBuilder(@NonNull AlertDialog.Builder builder) {

        entries = getEntries();
        entryValues = getEntryValues();
        currentIndex = findIndexOfValue(getValue());
        if (Logger.DEBUG) { Log.d(TAG, "[current value: " + currentIndex + "]"); }
        if (currentIndex == -1) {
            currentIndex = findIndexOfValue(String.valueOf(defaultValue));
            if (Logger.DEBUG) { Log.d(TAG, "[using default: " + currentIndex + "]"); }
        }

        ListAdapter listAdapter = new CustomAdapter(getContext(), getEntries());

        builder.setAdapter(listAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int position) {
                String value = getEntryValues()[position].toString();
                if (callChangeListener(value)) {
                    setValue(value);
                }
                dialog.dismiss();
            }
        });
        builder.setPositiveButton(null, null);
    }


    /**
     * Custom adapter with disabled unavailable providers
     */
    private class CustomAdapter extends ArrayAdapter<CharSequence> {

        CustomAdapter(@NonNull Context context, @NonNull CharSequence[] objects) {
            super(context, android.R.layout.select_dialog_singlechoice, objects);
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return !missingProviders.contains(Integer.valueOf(entryValues[position].toString()));
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            CheckedTextView row;
            if (convertView != null) {
                row = (CheckedTextView) convertView;
            } else {
                row = (CheckedTextView) mInflater.inflate(android.R.layout.select_dialog_singlechoice, parent, false);

                row.setText(entries[position]);
                if (missingProviders.contains(Integer.valueOf(entryValues[position].toString()))) {
                    row.setTextColor(Color.LTGRAY);
                }

                if (position == currentIndex) {
                    row.setChecked(true);
                }
            }

            return row;
        }

    }

}
