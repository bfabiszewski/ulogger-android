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
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.SwitchCompat;

import java.util.Collections;

public class SwipeSwitch extends SwitchCompat {

    private final String TAG = SwipeSwitch.class.getSimpleName();
    private final Rect exclusionRect = new Rect();


    public SwipeSwitch(Context context) {
        super(context);
    }

    public SwipeSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SwipeSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
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

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (Build.VERSION.SDK_INT >= 29) {
            setGestureExclusionRects();
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void setGestureExclusionRects() {
        exclusionRect.set(0, 0, getWidth(), getHeight());
        if (Logger.DEBUG) { Log.d(TAG, "[setGestureExclusionRects: " + exclusionRect + "]"); }
        setSystemGestureExclusionRects(Collections.singletonList(exclusionRect));
    }

}
