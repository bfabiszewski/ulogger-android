/*
 * Copyright (c) 2018 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;

class PasswordPreference extends EditTextPreference implements EditTextPreference.OnBindEditTextListener {
    public PasswordPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setOnBindEditTextListener(this);
    }

    public PasswordPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOnBindEditTextListener(this);
    }

    public PasswordPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnBindEditTextListener(this);
    }

    public PasswordPreference(Context context) {
        super(context);
        setOnBindEditTextListener(this);
    }

    /**
     * Returns the summary of this preference.
     * Masked password or default "not set" string.
     *
     * @return The summary
     */
    @Override
    public CharSequence getSummary() {
        if ((getSummaryProvider() != null)) {
            final String text = super.getText();
            if (!TextUtils.isEmpty(text)) {
                return getMaskedText(text);
            }
        }
        return super.getSummary();
    }

    /**
     * Get string with each letter substituted by asterisk
     * @param text Input string
     * @return Masked string
     */
    private static String getMaskedText(String text) {
        return new String(new char[text.length()]).replace("\0", "*");
    }

    /**
     * Called when the dialog view for this preference has been bound, allowing you to
     * customize the {@link EditText} displayed in the dialog.
     *
     * @param editText The {@link EditText} displayed in the dialog
     */
    @Override
    public void onBindEditText(@NonNull EditText editText) {
        editText.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
    }
}
