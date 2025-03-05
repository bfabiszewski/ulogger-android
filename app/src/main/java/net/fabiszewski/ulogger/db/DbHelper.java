/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.NonNull;

import net.fabiszewski.ulogger.Logger;

/**
 * Database helper
 *
 */

class DbHelper extends SQLiteOpenHelper {

    private static DbHelper instance;

    private static final String TAG = DbHelper.class.getSimpleName();

    private static final int DATABASE_VERSION = 3;
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
            DbContract.Positions.COLUMN_SYNCED + " INTEGER DEFAULT 0)";

    private static final String SQL_POS_CREATE_INDEX_SYNCED =
            "CREATE INDEX " + DbContract.Positions.INDEX_SYNCED + " " +
            "ON " + DbContract.Positions.TABLE_NAME + "(" + DbContract.Positions.COLUMN_SYNCED + ")";

    private static final String SQL_POS_CREATE_INDEX_TIME =
            "CREATE INDEX " + DbContract.Positions.INDEX_TIME + " " +
            "ON " + DbContract.Positions.TABLE_NAME +  "(" + DbContract.Positions.COLUMN_TIME + ")";

    private static final String SQL_POS_DROP_INDEX_SYNCED =
            "DROP INDEX IF EXISTS " + DbContract.Positions.INDEX_SYNCED;

    private static final String SQL_POS_DROP_INDEX_TIME =
            "DROP INDEX IF EXISTS " + DbContract.Positions.INDEX_TIME;

    private static final String SQL_CREATE_TRACK =
            "CREATE TABLE " + DbContract.Track.TABLE_NAME + " (" +
            DbContract.Track.COLUMN_ID + " INTEGER DEFAULT NULL," +
            DbContract.Track.COLUMN_NAME + " TEXT," +
            DbContract.Track.COLUMN_ERROR + " TEXT DEFAULT NULL)";

    private static final String SQL_DROP_POSITIONS =
            "DROP TABLE IF EXISTS " + DbContract.Positions.TABLE_NAME;

    private static final String SQL_DROP_TRACK =
            "DROP TABLE IF EXISTS " + DbContract.Track.TABLE_NAME;

    private static final String SQL_POS_ADD_COLUMN_COMMENT =
            "ALTER TABLE " + DbContract.Positions.TABLE_NAME + " ADD COLUMN " +
            DbContract.Positions.COLUMN_IMAGE_URI + " TEXT DEFAULT NULL";

    private static final String SQL_POS_ADD_COLUMN_IMAGE_URI =
            "ALTER TABLE " + DbContract.Positions.TABLE_NAME + " ADD COLUMN " +
            DbContract.Positions.COLUMN_COMMENT + " TEXT DEFAULT NULL";

    private static final String SQL_TRACK_ADD_COLUMN_ERROR =
            "ALTER TABLE " + DbContract.Track.TABLE_NAME + " ADD COLUMN " +
            DbContract.Track.COLUMN_ERROR + " TEXT DEFAULT NULL";

    private static final String SQL_MOVE_POSITIONS_TO_BACKUP =
            "ALTER TABLE " + DbContract.Positions.TABLE_NAME + " " +
            "RENAME TO " + DbContract.Positions.TABLE_NAME + BACKUP_SUFFIX;

    private static final String SQL_COPY_POSITIONS_FROM_V2 =
            "INSERT INTO " + DbContract.Positions.TABLE_NAME + " (" +
            DbContract.Positions.COLUMN_TIME + "," +
            DbContract.Positions.COLUMN_LATITUDE + "," +
            DbContract.Positions.COLUMN_LONGITUDE + "," +
            DbContract.Positions.COLUMN_ALTITUDE + "," +
            DbContract.Positions.COLUMN_BEARING + "," +
            DbContract.Positions.COLUMN_SPEED + "," +
            DbContract.Positions.COLUMN_ACCURACY + "," +
            DbContract.Positions.COLUMN_PROVIDER + "," +
            DbContract.Positions.COLUMN_COMMENT + "," +
            DbContract.Positions.COLUMN_IMAGE_URI + "," +
            DbContract.Positions.COLUMN_SYNCED + ") " +
            "SELECT " +
            DbContract.Positions.COLUMN_TIME + "," +
            DbContract.Positions.COLUMN_LATITUDE + "," +
            DbContract.Positions.COLUMN_LONGITUDE + "," +
            DbContract.Positions.COLUMN_ALTITUDE + "," +
            DbContract.Positions.COLUMN_BEARING + "," +
            DbContract.Positions.COLUMN_SPEED + "," +
            DbContract.Positions.COLUMN_ACCURACY + "," +
            DbContract.Positions.COLUMN_PROVIDER + "," +
            DbContract.Positions.COLUMN_COMMENT + "," +
            DbContract.Positions.COLUMN_IMAGE_URI + "," +
            DbContract.Positions.COLUMN_SYNCED + " " +
            "FROM " + DbContract.Positions.TABLE_NAME + BACKUP_SUFFIX;

    private static final String SQL_DROP_POSITIONS_BACKUP =
            "DROP TABLE IF EXISTS " + DbContract.Positions.TABLE_NAME + BACKUP_SUFFIX;

    /**
     * Private constructor
     *
     * @param context Context
     */
    private DbHelper(@NonNull Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Get DbHelper singleton instance
     *
     * @param context Context
     * @return DbHelper instance
     */
    @NonNull
    static DbHelper getInstance(@NonNull Context context) {

        if (instance == null) {
            instance = new DbHelper(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Create track and positions tables
     *
     * @param db Database handle
     */
    public void onCreate(@NonNull SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_POSITIONS);
        db.execSQL(SQL_POS_CREATE_INDEX_TIME);
        db.execSQL(SQL_POS_CREATE_INDEX_SYNCED);
        db.execSQL(SQL_CREATE_TRACK);
    }

    /**
     * On upgrade
     *
     * @param db Database handle
     * @param oldVersion Old version number
     * @param newVersion New version number
     */
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        if (Logger.DEBUG) { Log.d(TAG, "[onUpgrade: from " + oldVersion + " to " + newVersion + "]"); }
        switch (oldVersion) {
            case 1:
                migrateToVersion2(db);
                // fallthrough
            case 2:
                migrateToVersion3(db);
                break;
            default:
                dropAndCreate(db);
        }
    }

    /**
     * Drops and recreates database
     *
     * @param db Database handle
     */
    private void dropAndCreate(@NonNull SQLiteDatabase db) {
        db.execSQL(SQL_DROP_POSITIONS);
        db.execSQL(SQL_DROP_TRACK);
        onCreate(db);
    }

    /**
     * Migrates base from version 1 to 2
     *
     * @param db Database handle
     */
    private void migrateToVersion2(@NonNull SQLiteDatabase db) {
        if (Logger.DEBUG) { Log.d(TAG, "[migrateToVersion2]"); }

        // only affects positions schema
        db.execSQL(SQL_POS_ADD_COLUMN_COMMENT);
        db.execSQL(SQL_POS_ADD_COLUMN_IMAGE_URI);
        db.execSQL(SQL_POS_CREATE_INDEX_SYNCED);
    }

    /**
     * Migrates base from version 2 to 3
     *
     * @param db Database handle
     */
    private void migrateToVersion3(@NonNull SQLiteDatabase db) {
        if (Logger.DEBUG) { Log.d(TAG, "[migrateToVersion3]"); }

        // migrate track
        db.execSQL(SQL_TRACK_ADD_COLUMN_ERROR);

        // upgrade positions
        // cannot drop error column, so recreate
        db.execSQL(SQL_MOVE_POSITIONS_TO_BACKUP);
        // indices must be dropped as names collide with create index
        db.execSQL(SQL_POS_DROP_INDEX_SYNCED);
        db.execSQL(SQL_POS_DROP_INDEX_TIME);
        db.execSQL(SQL_CREATE_POSITIONS);
        db.execSQL(SQL_POS_CREATE_INDEX_TIME);
        db.execSQL(SQL_POS_CREATE_INDEX_SYNCED);
        db.execSQL(SQL_COPY_POSITIONS_FROM_V2);
        db.execSQL(SQL_DROP_POSITIONS_BACKUP);
    }

    /**
     * On downgrade just drop and recreate tables
     * Warning: data will be lost
     *
     * @param db Database handle
     * @param oldVersion Old version number
     * @param newVersion New version number
     */
    public void onDowngrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        if (Logger.DEBUG) { Log.d(TAG, "[onDowngrade: from " + oldVersion + " to " + newVersion + "]"); }
        dropAndCreate(db);
    }
}
