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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Database helper
 *
 */

class DbHelper extends SQLiteOpenHelper {

    private static DbHelper sInstance;

    private static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "ulogger.db";
    private static final String BACKUP_SUFFIX = "_backup";

    private static final String SQL_CREATE_POSITIONS =
            "CREATE TABLE " + DbContract.Positions.TABLE_NAME + " (" +
            DbContract.Positions._ID + " INTEGER PRIMARY KEY," +
            DbContract.Positions.COLUMN_TIME + " TEXT," +
            DbContract.Positions.COLUMN_LATITUDE + " TEXT," +
            DbContract.Positions.COLUMN_LONGITUDE + " TEXT," +
            DbContract.Positions.COLUMN_ALTITUDE + " TEXT DEFAULT NULL," +
            DbContract.Positions.COLUMN_BEARING + " TEXT DEFAULT NULL," +
            DbContract.Positions.COLUMN_SPEED + " TEXT DEFAULT NULL," +
            DbContract.Positions.COLUMN_ACCURACY + " TEXT DEFAULT NULL," +
            DbContract.Positions.COLUMN_PROVIDER + " TEXT," +
            DbContract.Positions.COLUMN_COMMENT + " TEXT DEFAULT NULL," +
            DbContract.Positions.COLUMN_IMAGE_URI + " TEXT DEFAULT NULL," +
            DbContract.Positions.COLUMN_SYNCED + " INTEGER DEFAULT 0," +
            DbContract.Positions.COLUMN_ERROR + " TEXT DEFAULT NULL)";
    private static final String SQL_CREATE_INDEX_SYNCED =
            "CREATE INDEX syncedIdx ON " + DbContract.Positions.TABLE_NAME + "(" +
            DbContract.Positions.COLUMN_SYNCED + ")";
    private static final String SQL_CREATE_TRACK =
            "CREATE TABLE " + DbContract.Track.TABLE_NAME + " (" +
            DbContract.Track.COLUMN_ID + " INTEGER DEFAULT NULL," +
            DbContract.Track.COLUMN_NAME + " TEXT)";

    private static final String SQL_DROP_POSITIONS =
            "DROP TABLE IF EXISTS " + DbContract.Positions.TABLE_NAME;
    private static final String SQL_DROP_TRACK =
            "DROP TABLE IF EXISTS " + DbContract.Track.TABLE_NAME;

    private static final String SQL_ADD_COLUMN_COMMENT =
            "ALTER TABLE " + DbContract.Positions.TABLE_NAME + " ADD COLUMN " +
            DbContract.Positions.COLUMN_IMAGE_URI + " TEXT DEFAULT NULL";

    private static final String SQL_ADD_COLUMN_IMAGE_URI =
            "ALTER TABLE " + DbContract.Positions.TABLE_NAME + " ADD COLUMN " +
            DbContract.Positions.COLUMN_COMMENT + " TEXT DEFAULT NULL";

    private static final String SQL_RENAME_POSITIONS_V1 =
            "ALTER TABLE " + DbContract.Positions.TABLE_NAME + " " +
                    "RENAME TO " + DbContract.Positions.TABLE_NAME + BACKUP_SUFFIX;
    private static final String SQL_INSERT_FROM_BACKUP_V1 =
            "INSERT INTO " + DbContract.Positions.TABLE_NAME + " (" +
            DbContract.Positions.COLUMN_TIME + "," +
            DbContract.Positions.COLUMN_LATITUDE + "," +
            DbContract.Positions.COLUMN_LONGITUDE + "," +
            DbContract.Positions.COLUMN_ALTITUDE + "," +
            DbContract.Positions.COLUMN_BEARING + "," +
            DbContract.Positions.COLUMN_SPEED + "," +
            DbContract.Positions.COLUMN_ACCURACY + "," +
            DbContract.Positions.COLUMN_PROVIDER + "," +
            DbContract.Positions.COLUMN_SYNCED + "," +
            DbContract.Positions.COLUMN_ERROR + ") " +
            "SELECT " +
            DbContract.Positions.COLUMN_TIME + "," +
            DbContract.Positions.COLUMN_LATITUDE + "," +
            DbContract.Positions.COLUMN_LONGITUDE + "," +
            DbContract.Positions.COLUMN_ALTITUDE + "," +
            DbContract.Positions.COLUMN_BEARING + "," +
            DbContract.Positions.COLUMN_SPEED + "," +
            DbContract.Positions.COLUMN_ACCURACY + "," +
            DbContract.Positions.COLUMN_PROVIDER + "," +
            DbContract.Positions.COLUMN_SYNCED + "," +
            DbContract.Positions.COLUMN_ERROR + " " +
            "FROM " + DbContract.Positions.TABLE_NAME + BACKUP_SUFFIX;
    private static final String SQL_DROP_POSITIONS_BACKUP =
            "DROP TABLE IF EXISTS " + DbContract.Positions.TABLE_NAME + BACKUP_SUFFIX;

    /**
     * Private constructor
     *
     * @param context Context
     */
    private DbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Get DbHelper singleton instance
     *
     * @param context Context
     * @return DbHelper instance
     */
    static DbHelper getInstance(Context context) {

        if (sInstance == null) {
            sInstance = new DbHelper(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Create track and positions tables
     * @param db Database handle
     */
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_POSITIONS);
        db.execSQL(SQL_CREATE_INDEX_SYNCED);
        db.execSQL(SQL_CREATE_TRACK);
    }

    /**
     * On upgrade
     * @param db Database handle
     * @param oldVersion Old version number
     * @param newVersion New version number
     */
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1 && newVersion == 2) {
            migrateToVersion2(db);
        } else {
            dropAndCreate(db);
        }
    }

    private void dropAndCreate(SQLiteDatabase db) {
        db.execSQL(SQL_DROP_POSITIONS);
        db.execSQL(SQL_DROP_TRACK);
        onCreate(db);
    }

    private void migrateToVersion2(SQLiteDatabase db) {
        // only affects positions schema
        db.execSQL(SQL_ADD_COLUMN_COMMENT);
        db.execSQL(SQL_ADD_COLUMN_IMAGE_URI);
        db.execSQL(SQL_CREATE_INDEX_SYNCED);

    }

    /**
     * On downgrade
     * @param db Database handle
     * @param oldVersion Old version number
     * @param newVersion New version number
     */
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 2 && newVersion == 1) {
            downgradeToVersion1(db);
        } else {
            dropAndCreate(db);
        }
    }

    private void downgradeToVersion1(SQLiteDatabase db) {
        db.execSQL(SQL_RENAME_POSITIONS_V1);
        db.execSQL(SQL_CREATE_POSITIONS);
        db.execSQL(SQL_INSERT_FROM_BACKUP_V1);
        db.execSQL(SQL_DROP_POSITIONS_BACKUP);
    }
}
