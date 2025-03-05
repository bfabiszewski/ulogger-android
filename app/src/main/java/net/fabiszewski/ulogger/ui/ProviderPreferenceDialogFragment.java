/*
 * Copyright (c) 2018 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;

import net.fabiszewski.ulogger.Logger;
import net.fabiszewski.ulogger.R;

import java.util.ArrayList;
import java.util.List;

public class ProviderPreferenceDialogFragment extends ListPreferenceDialogWithMessageFragment {

    private static final String TAG = ProviderPreferenceDialogFragment.class.getSimpleName();

    private final static int VALUE_GPS = 1;
    private final static int VALUE_NET = 2;
    private final static int VALUE_ALL = (VALUE_GPS | VALUE_NET);

    private CharSequence[] entries;
    private CharSequence[] entryValues;
    private List<Integer> missingProviders;

    private ListPreference preference;

    @NonNull
    public static ProviderPreferenceDialogFragment newInstance(String key) {
        final ProviderPreferenceDialogFragment fragment = new ProviderPreferenceDialogFragment();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);

        return fragment;
    }

    /**
     * Use custom adapter
     *
     * @param builder Builder
     */
    @Override
    protected void onPrepareDialogBuilder(@NonNull AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        preference = (ListPreference) getPreference();
        final Context context = getContext();
        if (context == null) {
            return;
        }

        preference.setOnPreferenceChangeListener((preference, newValue) -> {
            if (Logger.DEBUG) { Log.d(TAG, "[preference changed: " + newValue + "]"); }
            int providersMask = Integer.parseInt((String) newValue);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(SettingsActivity.KEY_USE_GPS, (providersMask & VALUE_GPS) == VALUE_GPS);
            editor.putBoolean(SettingsActivity.KEY_USE_NET, (providersMask & VALUE_NET) == VALUE_NET);
            editor.apply();
            return true;
        });

        entries = preference.getEntries();
        entryValues = preference.getEntryValues();
        int defaultValue = VALUE_ALL;
        LocationManager locManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        missingProviders = new ArrayList<>();
        final boolean existsGPS = locManager != null && locManager.getAllProviders().contains(LocationManager.GPS_PROVIDER);
        final boolean existsNet = locManager != null && locManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER);
        if (Logger.DEBUG) { Log.d(TAG, "[Providers available: " + (locManager != null ? locManager.getAllProviders() : null)); }
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
        int currentIndex = preference.findIndexOfValue(preference.getValue());
        if (Logger.DEBUG) { Log.d(TAG, "[current value: " + currentIndex + "]"); }
        if (currentIndex == -1) {
            currentIndex = preference.findIndexOfValue(String.valueOf(defaultValue));
            if (Logger.DEBUG) { Log.d(TAG, "[using default: " + currentIndex + "]"); }
        }

        ListAdapter listAdapter = new CustomAdapter(getContext(), getSingleChoiceLayoutResource(), preference.getEntries());

        builder.setSingleChoiceItems(listAdapter, currentIndex, (dialog, position) -> {
            String value = preference.getEntryValues()[position].toString();
            if (preference.callChangeListener(value)) {
                preference.setValue(value);
            }
            dialog.dismiss();
        });

        builder.setPositiveButton(null, null);
    }

    /**
     * Get default layout for single choice dialog
     * @return Layout resource id
     */
    @SuppressWarnings("resource")
    @SuppressLint("PrivateResource")
    // Don't use auto-closable, breaks lower APIs
    private int getSingleChoiceLayoutResource() {
        int resId = android.R.layout.select_dialog_singlechoice;
        final Context context = getContext();
        if (context != null) {
            final TypedArray typedArray = context.obtainStyledAttributes(null, R.styleable.AlertDialog,
                    androidx.appcompat.R.attr.alertDialogStyle, 0);
            resId = typedArray.getResourceId(R.styleable.AlertDialog_singleChoiceItemLayout, resId);
            typedArray.recycle();
        }
        return resId;
    }


    /**
     * Custom adapter with disabled unavailable providers
     */
    private class CustomAdapter extends ArrayAdapter<CharSequence> {

        final int resourceId;

        CustomAdapter(@NonNull Context context, int resource, @NonNull CharSequence[] objects) {
            super(context, resource, android.R.id.text1, objects);
            resourceId = resource;
        }

        @Override
        @SuppressWarnings("SameReturnValue")
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return !missingProviders.contains(Integer.valueOf(entryValues[position].toString()));
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(requireActivity()).inflate(resourceId, parent, false);
                if (!isEnabled(position)) {
                    ((CheckedTextView) convertView).setTextColor(Color.LTGRAY);
                }
            }
            ((CheckedTextView) convertView).setText(entries[position]);
            return convertView;
        }

    }
}
