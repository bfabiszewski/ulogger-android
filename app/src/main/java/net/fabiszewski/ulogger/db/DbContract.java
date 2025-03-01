/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger.db;

import android.provider.BaseColumns;

/**
 * Database contract
 *
 */

public final class DbContract {

    private DbContract() {}

    /** Positions table */
    public static class Positions implements BaseColumns {
        static final String TABLE_NAME = "positions";
        static final String COLUMN_TIME = "time";
        static final String COLUMN_LONGITUDE = "longitude";
        static final String COLUMN_LATITUDE = "latitude";
        static final String COLUMN_ALTITUDE = "altitude";
        static final String COLUMN_ACCURACY = "accuracy";
        static final String COLUMN_SPEED = "speed";
        static final String COLUMN_BEARING = "bearing";
        static final String COLUMN_PROVIDER = "provider";
        static final String COLUMN_COMMENT = "comment";
        static final String COLUMN_IMAGE_URI = "imageUri";
        static final String COLUMN_SYNCED = "synced";

        static final String INDEX_TIME = "timeIdx";
        static final String INDEX_SYNCED = "syncedIdx";
    }

    /** Track table */
    public static class Track {
        static final String TABLE_NAME = "track";
        static final String COLUMN_ID = "id";
        static final String COLUMN_NAME = "name";
        static final String COLUMN_ERROR = "error";
    }
}
