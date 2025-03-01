/*
 * Copyright (c) 2021 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import static android.content.Intent.EXTRA_LOCAL_ONLY;
import static android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

public class OpenLocalDocument extends ActivityResultContracts.OpenDocument {
    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, @NonNull String[] input) {
        Intent intent = super.createIntent(context, input);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(EXTRA_LOCAL_ONLY, true);
        int flags = FLAG_GRANT_READ_URI_PERMISSION|FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
        intent.addFlags(flags);
        return intent;
    }

}
