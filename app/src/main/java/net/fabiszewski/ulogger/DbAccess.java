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
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Gateway class for database access
 */

class DbAccess implements AutoCloseable {

    private static int openCount;
    private static DbAccess instance;

    private static SQLiteDatabase db;
    private static DbHelper dbHelper;
    private static final String TAG = DbAccess.class.getSimpleName();

    /**
     * Private constructor
     */
    private DbAccess() {
    }

    /**
     * Get singleton instance
     *
     * @return DbAccess singleton
     */
    static synchronized DbAccess getInstance() {
        if (instance == null) {
            instance = new DbAccess();
        }
        return instance;
    }

    /**
     * Get singleton instance with open database
     * Needs to be closed
     *
     * @return DbAccess singleton
     */
    static synchronized DbAccess getOpenInstance(Context context) {
        if (instance == null) {
            instance = new DbAccess();
        }
        instance.open(context);
        return instance;
    }

    /**
     * Opens database
     *
     * @param context Context
     */
    void open(Context context) {
        synchronized (DbAccess.class) {
            if (openCount++ == 0) {
                if (Logger.DEBUG) {
                    Log.d(TAG, "[open]");
                }
                dbHelper = DbHelper.getInstance(context.getApplicationContext());
                db = dbHelper.getWritableDatabase();
            }
            if (Logger.DEBUG) {
                Log.d(TAG, "[+openCount = " + openCount + "]");
            }
        }
    }

    /**
     * Write location to database.
     *
     * @param loc      Location
     * @param comment  Comment
     * @param imageUri Image URI
     */
    private void writeLocation(Location loc, String comment, String imageUri) {
        if (Logger.DEBUG) {
            Log.d(TAG, "[writeLocation]");
        }
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
        if (comment != null && !comment.isEmpty()) {
            values.put(DbContract.Positions.COLUMN_COMMENT, comment);
        }
        if (imageUri != null && !imageUri.isEmpty()) {
            values.put(DbContract.Positions.COLUMN_IMAGE_URI, imageUri);
        }
        db.insert(DbContract.Positions.TABLE_NAME, null, values);
    }

    /**
     * Write location to database.
     *
     * @param loc Location
     */
    static void writeLocation(Context context, Location loc) {
        writeLocation(context, loc, null, null);
    }

    /**
     * Write location to database.
     *
     * @param context Context
     * @param location Location
     * @param comment Comment
     * @param imageUri Image URI
     */
    static void writeLocation(Context context, Location location, String comment, String imageUri) {
        try (DbAccess dbAccess = getOpenInstance(context)) {
            dbAccess.writeLocation(location, comment, imageUri);
        }
    }

    /**
     * Get result set containing all positions.
     *
     * @return Result set
     */
    Cursor getPositions() {
        return db.query(DbContract.Positions.TABLE_NAME,
                new String[]{ "*" },
                null, null, null, null,
                DbContract.Positions.COLUMN_TIME);
    }

    /**
     * Get result set containing position with given id
     *
     * @param id Position id
     * @return Image URI
     */
     @Nullable
     private Uri getImageUri(int id) {
        Cursor query = db.query(DbContract.Positions.TABLE_NAME,
                new String[]{ DbContract.Positions.COLUMN_IMAGE_URI },
                DbContract.Positions._ID + " = ?",
                new String[]{ Integer.toString(id) },
                null, null, null);
        Uri uri = null;
        if (query.moveToFirst()) {
            String imageUri = query.getString(0);
            if (imageUri != null) {
                uri = Uri.parse(imageUri);
            }
        }
        query.close();
        return uri;
    }

    /**
     * Get result set containing positions marked as not synchronized.
     *
     * @return Result set
     */
    Cursor getUnsynced() {
        return db.query(DbContract.Positions.TABLE_NAME,
                new String[]{ "*" },
                DbContract.Positions.COLUMN_SYNCED + " = ?",
                new String[]{ "0" },
                null, null,
                DbContract.Positions.COLUMN_TIME);
    }

    /**
     * Get error message stored in track table.
     *
     * @return Error message or null if none
     */
    @Nullable
    private String getError() {
        Cursor track = db.query(DbContract.Track.TABLE_NAME,
                new String[]{ DbContract.Track.COLUMN_ERROR },
                null, null, null, null, null,
                "1");
        String error = null;
        if (track.moveToFirst()) {
            error = track.getString(0);
        }
        track.close();
        return error;
    }

    /**
     * Get error message from track table.
     *
     * @param context Context
     * @return Error message or null if none
     */
    @Nullable
    static String getError(Context context) {
        try (DbAccess dbAccess = getOpenInstance(context)) {
            return dbAccess.getError();
        }
    }

    /**
     * Add error message to track table.
     *
     * @param error Error message
     */
    void setError(@Nullable String error) {
        ContentValues values = new ContentValues();
        values.put(DbContract.Track.COLUMN_ERROR, error);
        db.update(DbContract.Track.TABLE_NAME,
                values,
                null, null);
    }

    void resetError() {
        if (getError() != null) {
            setError(null);
        }
    }

    /**
     * Mark position as synchronized.
     *
     * @param id Position id
     */
    void setSynced(Context context, int id) {
        ContentValues values = new ContentValues();
        values.put(DbContract.Positions.COLUMN_SYNCED, "1");
        db.update(DbContract.Positions.TABLE_NAME,
                values,
                DbContract.Positions._ID + " = ?",
                new String[]{ String.valueOf(id) } );
        Uri uri = getImageUri(id);
        if (uri != null) {
            ImageHelper.deleteLocalImage(context, uri);
        }
    }

    /**
     * Get number of all positions in track
     *
     * @return Count
     */
    int countPositions() {
        return (int) DatabaseUtils.queryNumEntries(db, DbContract.Positions.TABLE_NAME);
    }

    /**
     * Get number of not synchronized items.
     *
     * @return Count
     */
    private int countUnsynced() {
        return (int) DatabaseUtils.queryNumEntries(db, DbContract.Positions.TABLE_NAME,
                DbContract.Positions.COLUMN_SYNCED + " = 0");
    }

    /**
     * Get number of not synchronized items.
     *
     * @param context Context
     * @return Count
     */
    static int countUnsynced(Context context) {
        try (DbAccess dbAccess = getOpenInstance(context)) {
            return dbAccess.countUnsynced();
        }
    }

    /**
     * Get number of not synchronized items.
     *
     * @return Count
     */
    private int countImages() {
        return (int) DatabaseUtils.queryNumEntries(db, DbContract.Positions.TABLE_NAME,
                DbContract.Positions.COLUMN_IMAGE_URI + " IS NOT NULL");
    }

    /**
     * Get number of not synchronized items.
     *
     * @param context Context
     * @return Count
     */
    static int countImages(Context context) {
        try (DbAccess dbAccess = getOpenInstance(context)) {
            return dbAccess.countImages();
        }
    }

    /**
     * Checks if database needs synchronization,
     * i.e. contains non-synchronized positions.
     *
     * @return True if synchronization needed, false otherwise
     */
    private boolean needsSync() {
        return countUnsynced() > 0;
    }

    /**
     * Checks if database needs synchronization,
     * i.e. contains non-synchronized positions.
     *
     * @param context Context
     * @return True if synchronization needed, false otherwise
     */
    static boolean needsSync(Context context) {
        try (DbAccess dbAccess = getOpenInstance(context)) {
            return dbAccess.needsSync();
        }
    }

    /**
     * Get first saved location time.
     *
     * @return UTC timestamp in seconds
     */
    long getFirstTimestamp() {
        return getLimitTimestamp("ASC");
    }

    /**
     * Get last saved location time.
     *
     * @return UTC timestamp in seconds
     */
    private long getLastTimestamp() {
        return getLimitTimestamp("DESC");
    }

    /**
     * Get limiting timestamp: start or stop
     * 
     * @param sortDirection SQLite sort order keyword, one of "ASC" or "DESC"
     * @return UTC timestamp in seconds
     */
    private long getLimitTimestamp(@NonNull String sortDirection) {
        Cursor query = db.query(DbContract.Positions.TABLE_NAME,
                new String[]{ DbContract.Positions.COLUMN_TIME },
                null, null, null, null,
                DbContract.Positions.COLUMN_TIME + " " + sortDirection,
                "1");
        long timestamp = 0;
        if (query.moveToFirst()) {
            timestamp = query.getInt(0);
        }
        query.close();
        return timestamp;
    }

    /**
     * Get last saved location time.
     *
     * @param context Context
     * @return UTC timestamp in seconds
     */
    static long getLastTimestamp(Context context) {
        try (DbAccess dbAccess = getOpenInstance(context)) {
            return dbAccess.getLastTimestamp();
        }
    }

    /**
     * Get current track id.
     *
     * @return Track id, zero if no track with valid id in database
     */
    int getTrackId() {
        Cursor track = db.query(DbContract.Track.TABLE_NAME,
                new String[]{ DbContract.Track.COLUMN_ID },
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
    @Nullable
    String getTrackName() {
        Cursor track = db.query(DbContract.Track.TABLE_NAME,
                new String[]{ DbContract.Track.COLUMN_NAME },
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
     * Get current track name.
     *
     * @param context Context
     * @return Track name, null if no track in database
     */
    static String getTrackName(Context context) {
        try (DbAccess dbAccess = getOpenInstance(context)) {
            return dbAccess.getTrackName();
        }
    }

    /**
     * Get current track id.
     *
     * @param context Context
     * @return Track id, zero if no track with valid id in database
     */
    static int getTrackId(Context context) {
        try (DbAccess dbAccess = getOpenInstance(context)) {
            return dbAccess.getTrackId();
        }
    }

    /**
     * Update current track, set id.
     *
     * @param id New track id
     */
    void setTrackId(int id) {
        ContentValues values = new ContentValues();
        values.put(DbContract.Track.COLUMN_ID, id);
        db.update(DbContract.Track.TABLE_NAME, values, null, null);
    }

    /**
     * Set up new track.
     * Deletes all previous track data and positions. Adds new track.
     *
     * @param name New track name
     */
    private void newTrack(String name) {
        clear();
        ContentValues values = new ContentValues();
        values.put(DbContract.Track.COLUMN_NAME, name);
        db.insert(DbContract.Track.TABLE_NAME, null, values);
    }

    /**
     * Set up new track.
     * Deletes all previous track data and positions. Adds new track.
     *
     * @param context Context
     * @param name New track name
     */
    static void newTrack(Context context, String name) {
        try (DbAccess dbAccess = getOpenInstance(context)) {
            ImageHelper.clearTrackImages(context);
            dbAccess.newTrack(name);
        }
    }

    /**
     * Truncate all tables
     */
    private void clear() {
        truncateTrack();
        truncatePositions();
    }

    /**
     * Clear track.
     * Deletes all track data and positions.
     *
     * @param context Context
     */
    static void clearTrack(Context context) {
        try (DbAccess dbAccess = getOpenInstance(context)) {
            ImageHelper.clearTrackImages(context);
            dbAccess.clear();
        }
    }


    /**
     * Set up new track with default name if there is no active track.
     * To be used in automated context
     *
     * @param context Context
     */
    static void newAutoTrack(Context context) {
        if (getTrackName(context) == null) {
            newTrack(context, AutoNamePreference.getAutoTrackName(context));
        }
    }

    /**
     * Get track summary
     *
     * @return TrackSummary object, null if no positions
     */
    @Nullable
    static TrackSummary getTrackSummary(Context context) {
        try (DbAccess dbAccess = getOpenInstance(context);
            Cursor positions = dbAccess.getPositions()) {
            TrackSummary summary = null;
            if (positions.moveToFirst()) {
                double distance = 0.0;
                long count = 1;
                double startLon = getLongitudeAsDouble(positions);
                double startLat = getLatitudeAsDouble(positions);
                long startTime = getTimeAsLong(positions);
                long endTime = startTime;
                while (positions.moveToNext()) {
                    count++;
                    double endLon = getLongitudeAsDouble(positions);
                    double endLat = getLatitudeAsDouble(positions);
                    endTime = getTimeAsLong(positions);
                    float[] results = new float[1];
                    Location.distanceBetween(startLat, startLon, endLat, endLon, results);
                    distance += results[0];
                    startLon = endLon;
                    startLat = endLat;
                }
                long duration = endTime - startTime;
                summary = new TrackSummary(Math.round(distance), duration, count);
            }
            return summary;
        }
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
    @Override
    public void close() {
        synchronized (DbAccess.class) {
            if (--openCount == 0) {
                if (Logger.DEBUG) {
                    Log.d(TAG, "[close]");
                }

                if (db != null) {
                    db.close();
                }
                if (dbHelper != null) {
                    dbHelper.close();
                }
            }
            if (Logger.DEBUG) {
                Log.d(TAG, "[-openCount = " + openCount + "]");
            }
        }
    }

    /**
     * Get accuracy from positions cursor
     *
     * @param cursor Cursor
     * @return String accuracy
     */
    static String getAccuracy(Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_ACCURACY);
    }

    /**
     * Check if cursor contains accuracy data
     *
     * @param cursor Cursor
     * @return True if has accuracy data
     */
    static boolean hasAccuracy(Cursor cursor) {
        return isColumnNotNull(cursor, DbContract.Positions.COLUMN_ACCURACY);
    }

    /**
     * Get speed from positions cursor
     *
     * @param cursor Cursor
     * @return String speed
     */
    static String getSpeed(Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_SPEED);
    }

    /**
     * Check if cursor contains speed data
     *
     * @param cursor Cursor
     * @return True if has speed data
     */
    static boolean hasSpeed(Cursor cursor) {
        return isColumnNotNull(cursor, DbContract.Positions.COLUMN_SPEED);
    }

    /**
     * Get bearing from positions cursor
     *
     * @param cursor Cursor
     * @return String bearing
     */
    static String getBearing(Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_BEARING);
    }

    /**
     * Check if cursor contains bearing data
     *
     * @param cursor Cursor
     * @return True if has bearing data
     */
    static boolean hasBearing(Cursor cursor) {
        return isColumnNotNull(cursor, DbContract.Positions.COLUMN_BEARING);
    }

    /**
     * Get altitude from positions cursor
     *
     * @param cursor Cursor
     * @return String altitude
     */
    static String getAltitude(Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_ALTITUDE);
    }

    /**
     * Check if cursor contains altitude data
     *
     * @param cursor Cursor
     * @return True if has altitude data
     */
    static boolean hasAltitude(Cursor cursor) {
        return isColumnNotNull(cursor, DbContract.Positions.COLUMN_ALTITUDE);
    }

    /**
     * Get provider from positions cursor
     *
     * @param cursor Cursor
     * @return String provider
     */
    static String getProvider(Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_PROVIDER);
    }

    /**
     * Check if cursor contains provider data
     *
     * @param cursor Cursor
     * @return True if has provider data
     */
    static boolean hasProvider(Cursor cursor) {
        return isColumnNotNull(cursor, DbContract.Positions.COLUMN_PROVIDER);
    }

    /**
     * Get comment from positions cursor
     *
     * @param cursor Cursor
     * @return String comment
     */
    static String getComment(Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_COMMENT);
    }

    /**
     * Check if cursor contains image URI
     *
     * @param cursor Cursor
     * @return True if has image URI
     */
    static boolean hasImageUri(Cursor cursor) {
        return isColumnNotNull(cursor, DbContract.Positions.COLUMN_IMAGE_URI);
    }

    /**
     * Get image URI from positions cursor
     *
     * @param cursor Cursor
     * @return String URI
     */
    static String getImageUri(Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_IMAGE_URI);
    }

    /**
     * Check if cursor contains comment data
     *
     * @param cursor Cursor
     * @return True if has comment data
     */
    static boolean hasComment(Cursor cursor) {
        return isColumnNotNull(cursor, DbContract.Positions.COLUMN_COMMENT);
    }

    /**
     * Get latitude from positions cursor
     *
     * @param cursor Cursor
     * @return String latitude
     */
    static String getLatitude(Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_LATITUDE);
    }

    /**
     * Get longitude from positions cursor
     *
     * @param cursor Cursor
     * @return String longitude
     */
    static String getLongitude(Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_LONGITUDE);
    }

    /**
     * Get longitude from positions cursor
     *
     * @param cursor Cursor
     * @return Longitude
     */
    private static double getLongitudeAsDouble(Cursor cursor) {
        return getColumnAsDouble(cursor, DbContract.Positions.COLUMN_LONGITUDE);
    }

    /**
     * Get latitude from positions cursor
     *
     * @param cursor Cursor
     * @return Longitude
     */
    private static double getLatitudeAsDouble(Cursor cursor) {
        return getColumnAsDouble(cursor, DbContract.Positions.COLUMN_LATITUDE);
    }

    /**
     * Get time from positions cursor
     *
     * @param cursor Cursor
     * @return String time
     */
    static String getTime(Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_TIME);
    }

    /**
     * Get ISO 8601 formatted time from positions cursor
     *
     * @param cursor Cursor
     * @return String time
     */
    static String getTimeISO8601(Cursor cursor) {
        long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DbContract.Positions.COLUMN_TIME));
        return getTimeISO8601(timestamp);
    }

    /**
     * Get time from positions cursor
     *
     * @param cursor Cursor
     * @return Time
     */
    private static long getTimeAsLong(Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(DbContract.Positions.COLUMN_TIME));
    }

    /**
     * Get ID from positions cursor
     *
     * @param cursor Cursor
     * @return String ID
     */
    static String getID(Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions._ID);
    }

    /**
     * Format unix timestamp as ISO 8601 time
     *
     * @param timestamp Timestamp
     * @return Formatted time
     */
    static String getTimeISO8601(long timestamp) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(timestamp * 1000);
    }

    /**
     * Get given column value as double
     *
     * @param cursor Result set
     * @param column Column name
     * @return Column value
     */
    private static double getColumnAsDouble(Cursor cursor, String column) {
        return cursor.getDouble(cursor.getColumnIndexOrThrow(column));
    }

    /**
     * Get given column value as string
     *
     * @param cursor Result set
     * @param column Column name
     * @return Column value
     */
    private static String getColumnAsString(Cursor cursor, String column) {
        return cursor.getString(cursor.getColumnIndexOrThrow(column));
    }

    /**
     * Check given column is not null
     *
     * @param cursor Result set
     * @param column Column name
     * @return True if not null
     */
    private static boolean isColumnNotNull(Cursor cursor, String column) {
        return !cursor.isNull(cursor.getColumnIndexOrThrow(column));
    }
}
