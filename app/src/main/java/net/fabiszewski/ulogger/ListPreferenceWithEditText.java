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
import android.content.res.TypedArray;
import android.os.Build;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import static net.fabiszewski.ulogger.Alert.showAlert;

class ListPreferenceWithEditText extends ListPreference implements Preference.OnPreferenceChangeListener {

    private CharSequence otherSummary;

    private static final String TAG = ListPreferenceWithEditText.class.getSimpleName();
    private static final String OTHER = "other";

    public ListPreferenceWithEditText(Context context) {
        super(context);
        init(context, null, 0, 0);
    }

    public ListPreferenceWithEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ListPreferenceWithEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ListPreferenceWithEditText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        setOnPreferenceChangeListener(this);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ListPreferenceWithEditText, defStyleAttr, defStyleRes);
        otherSummary = a.getText(R.styleable.ListPreferenceWithEditText_otherSummary);
        a.recycle();
    }

    @Override
    public int findIndexOfValue(String value) {
        int index = super.findIndexOfValue(value);
        if (index == -1) {
            return super.findIndexOfValue(OTHER);
        } else {
            return index;
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (newValue.toString().equals(OTHER)) {
            showOtherDialog(preference.getContext(), preference);
            return false;
        }
        return true;
    }

    private void showOtherDialog(Context context, Preference preference) {
        final String key = preference.getKey();
        final AlertDialog dialog = showAlert(context,
                preference.getTitle(),
                R.layout.other_dialog);
        final TextView textView = dialog.findViewById(R.id.other_textview);
        textView.setText(otherSummary);
        textView.setContentDescription(otherSummary);
        final EditText editText = dialog.findViewById(R.id.other_edittext);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        editText.setText(prefs.getString(key, ""));
        editText.setHint(prefs.getString(key, ""));
        final Button submit = dialog.findViewById(R.id.other_button_submit);
        submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newval = editText.getText().toString();
                if (newval.length() > 0 && Integer.valueOf(newval) >= 0) {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(key, newval);
                    editor.apply();
                    setValue(newval);
                    if (Logger.DEBUG) { Log.d(TAG, "[" + key + " set to " + newval + "]"); }
                }
                dialog.cancel();
            }
        });

        final Button cancel = dialog.findViewById(R.id.other_button_cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.cancel();
            }
        });
    }

}
