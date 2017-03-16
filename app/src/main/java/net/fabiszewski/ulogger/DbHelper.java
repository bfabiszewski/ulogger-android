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

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "ulogger.db";

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
            DbContract.Positions.COLUMN_SYNCED + " INTEGER DEFAULT 0," +
            DbContract.Positions.COLUMN_ERROR + " TEXT DEFAULT NULL)";
    private static final String SQL_CREATE_TRACK =
            "CREATE TABLE " + DbContract.Track.TABLE_NAME + " (" +
                    DbContract.Track.COLUMN_ID + " INTEGER DEFAULT NULL," +
                    DbContract.Track.COLUMN_NAME + " TEXT)";

    private static final String SQL_DELETE_POSITIONS =
            "DROP TABLE IF EXISTS " + DbContract.Positions.TABLE_NAME;
    private static final String SQL_DELETE_TRACK =
            "DROP TABLE IF EXISTS " + DbContract.Track.TABLE_NAME;

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
    public static synchronized DbHelper getInstance(Context context) {

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
        db.execSQL(SQL_CREATE_TRACK);
    }

    /**
     * On upgrade delete all tables, call create
     * @param db Database handle
     * @param oldVersion Old version number
     * @param newVersion New version number
     */
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_POSITIONS);
        db.execSQL(SQL_DELETE_TRACK);
        onCreate(db);
    }

    /**
     * On downgrade behave as on upgrade
     * @param db Database handle
     * @param oldVersion Old version number
     * @param newVersion New version number
     */
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
