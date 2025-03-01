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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.DialogPreference;
import androidx.preference.ListPreferenceDialogFragmentCompat;

import net.fabiszewski.ulogger.R;

public class ListPreferenceDialogWithMessageFragment extends ListPreferenceDialogFragmentCompat {

    @NonNull
    public static ListPreferenceDialogWithMessageFragment newInstance(String key) {
        final ListPreferenceDialogWithMessageFragment fragment = new ListPreferenceDialogWithMessageFragment();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    /**
     * Overridden to set custom title view with title (dialogTitle) and message (dialogMessage)
     * @param builder AlertDialog builder
     */
    @Override
    protected void onPrepareDialogBuilder(@NonNull AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        final DialogPreference preference = getPreference();
        @SuppressLint("InflateParams")
        View customTitleView = LayoutInflater.from(requireActivity()).inflate(R.layout.alert_dialog_title, null);
        final TextView dialogTitle = customTitleView.findViewById(R.id.customTitle);
        dialogTitle.setText(preference.getDialogTitle());
        final TextView messageView = customTitleView.findViewById(R.id.customMessage);
        messageView.setText(preference.getDialogMessage());
        builder.setCustomTitle(customTitleView);
        builder.setMessage(null);
    }
}
