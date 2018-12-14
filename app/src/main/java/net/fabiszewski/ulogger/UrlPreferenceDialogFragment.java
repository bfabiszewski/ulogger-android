/*
 * Copyright (c) 2018 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.app.Dialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.EditTextPreferenceDialogFragmentCompat;

public class UrlPreferenceDialogFragment extends EditTextPreferenceDialogFragmentCompat {

    public static UrlPreferenceDialogFragment newInstance(String key) {
        final UrlPreferenceDialogFragment fragment = new UrlPreferenceDialogFragment();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onStart() {
        super.onStart();
        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            Button positiveButton =  dialog.getButton(Dialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    EditText editText = dialog.findViewById(android.R.id.edit);
                    final String url = editText.getText().toString().trim();
                    if (url.isEmpty() || WebHelper.isValidURL(url)) {
                        editText.setError(null);
                        EditTextPreference preference = (EditTextPreference) getPreference();
                        preference.setText(url);
                        dismiss();
                    } else {
                        editText.setError(getContext().getString(R.string.provide_valid_url));
                    }
                }
            });
            // xml attribute inputType doesn't work in androidx
            EditText editText = dialog.findViewById(android.R.id.edit);
            editText.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
            // xml attribute hint doesn't work in androidx
            editText.setHint("https://www.example.com");
        }
    }
}

