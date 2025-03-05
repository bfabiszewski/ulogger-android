/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.EditTextPreferenceDialogFragmentCompat;

import net.fabiszewski.ulogger.R;

import java.util.Calendar;
import java.util.Date;

public class AutoNamePreferenceDialogFragment extends EditTextPreferenceDialogFragmentCompat implements TextWatcher {
    private EditText editText = null;
    private TextView preview = null;
    private Date TEMPLATE_DATE;

    @NonNull
    public static AutoNamePreferenceDialogFragment newInstance(String key) {
        final AutoNamePreferenceDialogFragment fragment = new AutoNamePreferenceDialogFragment();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onStart() {
        super.onStart();
        TEMPLATE_DATE = Calendar.getInstance().getTime();
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            editText = dialog.findViewById(android.R.id.edit);
            Button positiveButton = dialog.getButton(Dialog.BUTTON_POSITIVE);
            if (positiveButton != null && editText != null) {
                editText.addTextChangedListener(this);
                ViewGroup layout = (ViewGroup) editText.getParent();
                preview = new TextView(requireContext());
                layout.addView(preview, layout.indexOfChild(editText));
                preview.setGravity(Gravity.CENTER);
                updatePreview(editText.getText().toString());
                positiveButton.setOnClickListener(v -> {
                    final String template = editText.getText().toString().trim();
                    if (template.isEmpty()) {
                        setError(getString(R.string.empty_trackname_warning));
                    } else {
                        setError(null);
                        EditTextPreference preference = (EditTextPreference) getPreference();
                        preference.setText(template);
                        dismiss();
                    }
                });
            }
        }
    }

    /**
     * Set error on editText
     * @param string Message
     */
    private void setError(String string) {
        if (editText != null) {
            editText.setError(string);
        }
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) { }

    @Override
    public void onTextChanged(CharSequence charSequence, int start, int before, int count) { }

    @Override
    public void afterTextChanged(@NonNull Editable editable) {
        updatePreview(editable.toString());
        //noinspection SizeReplaceableByIsEmpty
        if (editable.length() == 0) {
            setError(getString(R.string.empty_trackname_warning));
        }
    }

    /**
     * Update preview TextView
     * @param string Preview input string
     */
    private void updatePreview(@NonNull String string) {
        if (preview != null) {
            try {
                preview.setText(AutoNamePreference.getAutoName(string, TEMPLATE_DATE));
            } catch (IllegalArgumentException e) {
                setError(getString(R.string.illegal_template_warning));
            }
        }
    }

}
