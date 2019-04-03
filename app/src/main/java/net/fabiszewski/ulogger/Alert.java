/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

class Alert {

    /**
     * Show confirmation dialog, OK and Cancel buttons
     * @param context Context
     * @param title Title
     * @param message Message
     * @param yesCallback Positive button callback
     */
    static void showConfirm(Context context, CharSequence title, CharSequence message,
                                   DialogInterface.OnClickListener yesCallback) {
        AlertDialog alertDialog = initDialog(context, title, message);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.ok), yesCallback);
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, context.getString(R.string.cancel),
                (dialog, which) -> dialog.dismiss());
        alertDialog.show();
    }

    /**
     * Show information dialog with OK button
     * @param context Context
     * @param title Title
     * @param message Message
     */
    static void showInfo(Context context, CharSequence title, CharSequence message) {
        AlertDialog alertDialog = initDialog(context, title, message);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, context.getString(R.string.ok),
                (dialog, which) -> dialog.dismiss());
        alertDialog.show();
    }

    /**
     * Set up basic dialog
     * @param context Context
     * @param title Title
     * @param message Message
     * @return AlertDialog Dialog
     */
    private static AlertDialog initDialog(Context context, CharSequence title, CharSequence message) {
        AlertDialog alertDialog = new AlertDialog.Builder(context).create();
        alertDialog.setTitle(title);
        alertDialog.setMessage(message);
        return alertDialog;
    }

    /**
     * Show dialog
     * @param context Context
     * @param title Title
     * @param layoutResource Layout resource id
     * @param iconResource Icon resource id
     * @return AlertDialog Dialog
     */
    static AlertDialog showAlert(Activity context, CharSequence title, int layoutResource, int iconResource) {
        @SuppressLint("InflateParams")
        View view = context.getLayoutInflater().inflate(layoutResource, null, false);
        AlertDialog alertDialog = new AlertDialog.Builder(context).create();
        alertDialog.setTitle(title);
        alertDialog.setView(view);
        if (iconResource > 0) {
            alertDialog.setIcon(iconResource);
        }
        alertDialog.show();
        return alertDialog;
    }

    /**
     * Show dialog
     * @param context Context
     * @param title Title
     * @param layoutResource Layout resource id
     * @return AlertDialog Dialog
     */
    static AlertDialog showAlert(Activity context, CharSequence title, int layoutResource) {
        return showAlert(context, title, layoutResource, 0);
    }
}
