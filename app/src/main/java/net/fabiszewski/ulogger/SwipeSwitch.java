/*
 * Copyright (c) 2020 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Switch;

import androidx.annotation.RequiresApi;

public class SwipeSwitch extends Switch {
    private static final String TAG = ImageTask.class.getSimpleName();

    public SwipeSwitch(Context context) {
        super(context);
    }

    public SwipeSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SwipeSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public SwipeSwitch(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean performClick() {
        return super.isChecked();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final boolean ret = super.onTouchEvent(ev);
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // prevent thumb staying in the middle
            setChecked(isChecked());
        }
        return ret;
    }
}
