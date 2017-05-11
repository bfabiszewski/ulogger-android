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
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.support.annotation.RequiresApi;
import android.app.AlertDialog;
import android.util.AttributeSet;
import android.view.View;

/**
 * URL edit text preference
 * Validates and trims URL
 */

class UrlEditTextPreference extends EditTextPreference {

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public UrlEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public UrlEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public UrlEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public UrlEditTextPreference(Context context) {
        super(context);
    }

    @Override
    public void setText(String text) {
        super.setText(text.trim());
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        getEditText().setError(null);
        final AlertDialog dialog = (AlertDialog) getDialog();
        View positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPositiveButtonClicked();
            }
        });
    }

    private void onPositiveButtonClicked() {
        if (WebHelper.isValidURL(getEditText().getText().toString().trim())) {
            getEditText().setError(null);
            onClick(getDialog(), DialogInterface.BUTTON_POSITIVE);
            getDialog().dismiss();
        } else {
            getEditText().setError(getContext().getString(R.string.provide_valid_url));
        }
    }
}
