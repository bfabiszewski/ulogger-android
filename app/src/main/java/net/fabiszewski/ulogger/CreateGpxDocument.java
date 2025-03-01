/*
 * Copyright (c) 2021 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

public class CreateGpxDocument extends ActivityResultContracts.CreateDocument {

    public static final String GPX_MIME = "application/gpx+xml";

    public CreateGpxDocument() {
        super(GPX_MIME);
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, @NonNull String input) {
        return super.createIntent(context, input)
                .setType(GPX_MIME)
                .addCategory(Intent.CATEGORY_OPENABLE);
    }
}
