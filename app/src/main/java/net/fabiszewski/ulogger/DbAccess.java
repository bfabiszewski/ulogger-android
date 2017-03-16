/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gateway class for database access
 *
 */

class DbAccess {

    private static final AtomicInteger openCount = new AtomicInteger();
    private static DbAccess sInstance;

    private static SQLiteDatabase db;
    private static DbHelper mDbHelper;
    private static final String TAG = DbAccess.class.getSimpleName();


    /**
     * Private constructor
     */
    private DbAccess() {
    }

    /**
     * Get singleton instance
     * @return DbAccess singleton
     */
    public static synchronized DbAccess getInstance() {
        if (sInstance == null) {
            sInstance = new DbAccess();
        }
        return sInstance;
    }

    /**
     * Opens database
     * @param context Context
     */
    public synchronized void open(Context context) {
        if(openCount.incrementAndGet() == 1) {
            mDbHelper = DbHelper.getInstance(context.getApplicationContext());
            db = mDbHelper.getWritableDatabase();
            Log.d(TAG, "[db open: " + db + "]");
        }
        Log.d(TAG, "[db open,  counter: " + openCount + "]");
    }

    /**
     * Write location to database.
     *
     * @param loc Location
     */
    public void writeLocation(Location loc) {
        ContentValues values = new ContentValues();
        values.put(DbContract.Positions.COLUMN_TIME, loc.getTime() / 1000);
        values.put(DbContract.Positions.COLUMN_LATITUDE, loc.getLatitude());
        values.put(DbContract.Positions.COLUMN_LONGITUDE, loc.getLongitude());
        if (loc.hasBearing()) {
            values.put(DbContract.Positions.COLUMN_BEARING, loc.getBearing());
        }
        if (loc.hasAltitude()) {
            values.put(DbContract.Positions.COLUMN_ALTITUDE, loc.getAltitude());
        }
        if (loc.hasSpeed()) {
            values.put(DbContract.Positions.COLUMN_SPEED, loc.getSpeed());
        }
        if (loc.hasAccuracy()) {
            values.put(DbContract.Positions.COLUMN_ACCURACY, loc.getAccuracy());
        }
        values.put(DbContract.Positions.COLUMN_PROVIDER, loc.getProvider());
        Log.d(TAG, "[writeLocation]");

        db.insert(DbContract.Positions.TABLE_NAME, null, values);
    }

    /**
     * Get result set containing positions marked as not synchronized.
     *
     * @return Result set
     */
    public Cursor getUnsynced() {
        return db.query(DbContract.Positions.TABLE_NAME,
                new String[] {"*"},
                DbContract.Positions.COLUMN_SYNCED + "=?",
                new String[] {"0"},
                null, null,
                DbContract.Positions._ID);
    }

    /**
     * Get error message from first not synchronized position.
     *
     * @return Error message or null if none
     */
    public String getError() {
        Cursor query = db.query(DbContract.Positions.TABLE_NAME,
                new String[] {DbContract.Positions.COLUMN_ERROR},
                DbContract.Positions.COLUMN_SYNCED + "=?",
                new String[] {"0"},
                null, null,
                DbContract.Positions._ID,
                "1");
        String error = null;
        if (query.moveToFirst()) {
            error = query.getString(0);
        }
        query.close();
        return error;
    }

    /**
     * Add error message to first not synchronized position.
     *
     * @param error Error message
     */
    public void setError(String error) {
        ContentValues values = new ContentValues();
        values.put(DbContract.Positions.COLUMN_ERROR, error);
        db.update(DbContract.Positions.TABLE_NAME,
                values,
                DbContract.Positions._ID +
                        "=(SELECT MIN(" + DbContract.Positions._ID + ") " +
                        "FROM " + DbContract.Positions.TABLE_NAME + " " +
                        "WHERE " + DbContract.Positions.COLUMN_SYNCED + "=?)",
                new String[] { "0" });
    }

    /**
     * Mark position as synchronized.
     *
     * @param id Position id
     */
    public void setSynced(int id) {
        ContentValues values = new ContentValues();
        values.put(DbContract.Positions.COLUMN_SYNCED, "1");
        values.putNull(DbContract.Positions.COLUMN_ERROR);
        db.update(DbContract.Positions.TABLE_NAME,
                values,
                DbContract.Positions._ID + "=?",
                new String[] { String.valueOf(id) });
    }

    /**
     * Get number of not synchronized items.
     *
     * @return Count
     */
    public int countUnsynced() {
        Cursor count = db.query(DbContract.Positions.TABLE_NAME,
                new String[] {"COUNT(*)"},
                DbContract.Positions.COLUMN_SYNCED + "=?",
                new String[] {"0"},
                null, null, null);
        int result = 0;
        if (count.moveToFirst()) {
            result = count.getInt(0);
        }
        count.close();
        return result;
    }

    /**
     * Checks if database needs synchronization,
     * i.e. contains non-synchronized positions.
     *
     * @return True if synchronization needed, false otherwise
     */
    public boolean needsSync() {
        return (countUnsynced() > 0);
    }

    /**
     * Get last saved location time.
     *
     * @return UTC timestamp in seconds
     */
    public long getLastTimestamp() {
        Cursor query = db.query(DbContract.Positions.TABLE_NAME,
                new String[] {DbContract.Positions.COLUMN_TIME},
                null, null, null, null,
                DbContract.Positions._ID + " DESC",
                "1");
        long timestamp = 0;
        if (query.moveToFirst()) {
            timestamp = query.getInt(0);
        }
        query.close();
        return timestamp;
    }

    /**
     * Get current track id.
     *
     * @return Track id, zero if no track with valid id in database
     */
    public int getTrackId() {
        Cursor track = db.query(DbContract.Track.TABLE_NAME,
                new String[] {DbContract.Track.COLUMN_ID},
                DbContract.Track.COLUMN_ID + " IS NOT NULL",
                null, null, null, null,
                "1");
        int trackId = 0;
        if (track.moveToFirst()) {
            trackId = track.getInt(0);
        }
        track.close();
        return trackId;
    }

    /**
     * Get current track name.
     *
     * @return Track name, null if no track in database
     */
    public String getTrackName() {
        Cursor track = db.query(DbContract.Track.TABLE_NAME,
                new String[] {DbContract.Track.COLUMN_NAME},
                null, null, null, null, null,
                "1");
        String trackName = null;
        if (track.moveToFirst()) {
            trackName = track.getString(0);
        }
        track.close();
        return trackName;
    }

    /**
     * Update current track, set id.
     *
     * @param id New track id
     */
    public void setTrackId(int id) {
        ContentValues values = new ContentValues();
        values.put(DbContract.Track.COLUMN_ID, id);
        db.update(DbContract.Track.TABLE_NAME,
                values,
                null, null);
    }

    /**
     * Start new track.
     * Deletes all previous track data and positions. Adds new track.
     *
     * @param name New track name
     */
    public void newTrack(String name) {
        truncateTrack();
        truncatePositions();
        ContentValues values = new ContentValues();
        values.put(DbContract.Track.COLUMN_NAME, name);
        db.insert(DbContract.Track.TABLE_NAME, null, values);
    }

    /**
     * Get track summary
     *
     * @return TrackSummary object
     */
    public TrackSummary getTrackSummary() {
        Cursor positions = db.query(DbContract.Positions.TABLE_NAME,
                new String[] {"*"},
                null, null, null, null,
                DbContract.Positions._ID);
        double startLon, startLat, endLon, endLat;
        long startTime, endTime;
        long distance = 0;
        TrackSummary summary = null;
        if (positions.moveToFirst()) {
            long count = 1;
            startLon = positions.getDouble(positions.getColumnIndex(DbContract.Positions.COLUMN_LONGITUDE));
            startLat = positions.getDouble(positions.getColumnIndex(DbContract.Positions.COLUMN_LATITUDE));
            startTime = positions.getLong(positions.getColumnIndex(DbContract.Positions.COLUMN_TIME));
            endTime = startTime;
            while (positions.moveToNext()) {
                count++;
                endLon = positions.getDouble(positions.getColumnIndex(DbContract.Positions.COLUMN_LONGITUDE));
                endLat = positions.getDouble(positions.getColumnIndex(DbContract.Positions.COLUMN_LATITUDE));
                endTime = positions.getLong(positions.getColumnIndex(DbContract.Positions.COLUMN_TIME));
                float[] results = new float[1];
                Location.distanceBetween(startLat, startLon, endLat, endLon, results);
                distance += results[0];
                startLon = endLon;
                startLat = endLat;
            }
            long duration = endTime - startTime;
            summary = new TrackSummary(distance, duration, count);
        }
        positions.close();
        return summary;
    }

    /**
     * Deletes all track metadata.
     */
    private void truncateTrack() {
        db.delete(DbContract.Track.TABLE_NAME, null, null);
    }

    /**
     * Deletes all positions
     */
    private void truncatePositions() {
        db.delete(DbContract.Positions.TABLE_NAME, null, null);
    }

    /**
     * Closes database
     */
    public synchronized void close() {
        Log.d(TAG, "[db close,  counter: " + openCount + "]");
        if(openCount.decrementAndGet() == 0) {
            Log.d(TAG, "[db close: " + db + "]");
            if (db != null) {
                db.close();
            }
            if (mDbHelper != null) {
                mDbHelper.close();
            }
        }
    }

}
