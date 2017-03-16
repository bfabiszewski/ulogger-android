/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.provider.BaseColumns;

/**
 * Database contract
 *
 */

final class DbContract {

    private DbContract() {}

    /** Positions table */
    public static class Positions implements BaseColumns {
        public static final String TABLE_NAME = "positions";
        public static final String COLUMN_TIME = "time";
        public static final String COLUMN_LONGITUDE = "longitude";
        public static final String COLUMN_LATITUDE = "latitude";
        public static final String COLUMN_ALTITUDE = "altitude";
        public static final String COLUMN_ACCURACY = "accuracy";
        public static final String COLUMN_SPEED = "speed";
        public static final String COLUMN_BEARING = "bearing";
        public static final String COLUMN_PROVIDER = "provider";
        public static final String COLUMN_SYNCED = "synced";
        public static final String COLUMN_ERROR = "error";
    }

    /** Track table */
    public static class Track {
        public static final String TABLE_NAME = "track";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_NAME = "name";
    }
}
