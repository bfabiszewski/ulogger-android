/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger.db;

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

import net.fabiszewski.ulogger.Logger;
import net.fabiszewski.ulogger.TrackSummary;
import net.fabiszewski.ulogger.ui.AutoNamePreference;
import net.fabiszewski.ulogger.utils.ImageHelper;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Gateway class for database access
 */

public class DbAccess implements AutoCloseable {

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
    @NonNull
    public static synchronized DbAccess getInstance() {
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
    @NonNull
    public static synchronized DbAccess getOpenInstance(@NonNull Context context) {
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
    public void open(@NonNull Context context) {
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
    private void writeLocation(@NonNull Location loc, @Nullable String comment, @Nullable String imageUri) {
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
     * @param location Location
     */
    public static void writeLocation(@NonNull Context context, @NonNull Location location) {
        writeLocation(context, location, null, null);
    }

    /**
     * Write location to database.
     *
     * @param context Context
     * @param location Location
     * @param comment Comment
     * @param imageUri Image URI
     */
    public static void writeLocation(@NonNull Context context, @NonNull Location location, @Nullable String comment, @Nullable String imageUri) {
        try (DbAccess dbAccess = getOpenInstance(context)) {
            dbAccess.writeLocation(location, comment, imageUri);
        }
    }

    /**
     * Get result set containing all positions.
     *
     * @return Result set
     */
    @NonNull
    public Cursor getPositions() {
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
    @NonNull
    public Cursor getUnsynced() {
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
    public static String getError(@NonNull Context context) {
        try (DbAccess dbAccess = getOpenInstance(context)) {
            return dbAccess.getError();
        }
    }

    /**
     * Add error message to track table.
     *
     * @param error Error message
     */
    public void setError(@Nullable String error) {
        ContentValues values = new ContentValues();
        values.put(DbContract.Track.COLUMN_ERROR, error);
        db.update(DbContract.Track.TABLE_NAME,
                values,
                null, null);
    }

    public void resetError() {
        if (getError() != null) {
            setError(null);
        }
    }

    /**
     * Mark position as synchronized.
     *
     * @param id Position id
     */
    public void setSynced(@NonNull Context context, int id) {
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
    public int countPositions() {
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
    public static int countUnsynced(@NonNull Context context) {
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
    public static int countImages(@NonNull Context context) {
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
    public static boolean needsSync(@NonNull Context context) {
        try (DbAccess dbAccess = getOpenInstance(context)) {
            return dbAccess.needsSync();
        }
    }

    /**
     * Get first saved location time.
     *
     * @return UTC timestamp in seconds
     */
    public long getFirstTimestamp() {
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
    public static long getLastTimestamp(@NonNull Context context) {
        try (DbAccess dbAccess = getOpenInstance(context)) {
            return dbAccess.getLastTimestamp();
        }
    }

    /**
     * Get current track id.
     *
     * @return Track id, zero if no track with valid id in database
     */
    public int getTrackId() {
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
    public String getTrackName() {
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
    @Nullable
    public static String getTrackName(@NonNull Context context) {
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
    public static int getTrackId(@NonNull Context context) {
        try (DbAccess dbAccess = getOpenInstance(context)) {
            return dbAccess.getTrackId();
        }
    }

    /**
     * Update current track, set id.
     *
     * @param id New track id
     */
    public void setTrackId(int id) {
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
    private void newTrack(@NonNull String name) {
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
    public static void newTrack(@NonNull Context context, @NonNull String name) {
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
    public static void clearTrack(@NonNull Context context) {
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
    public static void newAutoTrack(@NonNull Context context) {
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
    public static TrackSummary getTrackSummary(@NonNull Context context) {
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
    @Nullable
    public static String getAccuracy(@NonNull Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_ACCURACY);
    }

    /**
     * Check if cursor contains accuracy data
     *
     * @param cursor Cursor
     * @return True if has accuracy data
     */
    public static boolean hasAccuracy(@NonNull Cursor cursor) {
        return isColumnNotNull(cursor, DbContract.Positions.COLUMN_ACCURACY);
    }

    /**
     * Get speed from positions cursor
     *
     * @param cursor Cursor
     * @return String speed
     */
    @Nullable
    public static String getSpeed(@NonNull Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_SPEED);
    }

    /**
     * Check if cursor contains speed data
     *
     * @param cursor Cursor
     * @return True if has speed data
     */
    public static boolean hasSpeed(@NonNull Cursor cursor) {
        return isColumnNotNull(cursor, DbContract.Positions.COLUMN_SPEED);
    }

    /**
     * Get bearing from positions cursor
     *
     * @param cursor Cursor
     * @return String bearing
     */
    @Nullable
    public static String getBearing(@NonNull Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_BEARING);
    }

    /**
     * Check if cursor contains bearing data
     *
     * @param cursor Cursor
     * @return True if has bearing data
     */
    public static boolean hasBearing(@NonNull Cursor cursor) {
        return isColumnNotNull(cursor, DbContract.Positions.COLUMN_BEARING);
    }

    /**
     * Get altitude from positions cursor
     *
     * @param cursor Cursor
     * @return String altitude
     */
    @Nullable
    public static String getAltitude(@NonNull Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_ALTITUDE);
    }

    /**
     * Check if cursor contains altitude data
     *
     * @param cursor Cursor
     * @return True if has altitude data
     */
    public static boolean hasAltitude(@NonNull Cursor cursor) {
        return isColumnNotNull(cursor, DbContract.Positions.COLUMN_ALTITUDE);
    }

    /**
     * Get provider from positions cursor
     *
     * @param cursor Cursor
     * @return String provider
     */
    @Nullable
    public static String getProvider(@NonNull Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_PROVIDER);
    }

    /**
     * Check if cursor contains provider data
     *
     * @param cursor Cursor
     * @return True if has provider data
     */
    public static boolean hasProvider(@NonNull Cursor cursor) {
        return isColumnNotNull(cursor, DbContract.Positions.COLUMN_PROVIDER);
    }

    /**
     * Get comment from positions cursor
     *
     * @param cursor Cursor
     * @return String comment
     */
    @Nullable
    public static String getComment(@NonNull Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_COMMENT);
    }

    /**
     * Check if cursor contains image URI
     *
     * @param cursor Cursor
     * @return True if has image URI
     */
    public static boolean hasImageUri(@NonNull Cursor cursor) {
        return isColumnNotNull(cursor, DbContract.Positions.COLUMN_IMAGE_URI);
    }

    /**
     * Get image URI from positions cursor
     *
     * @param cursor Cursor
     * @return String URI
     */
    @Nullable
    public static String getImageUri(@NonNull Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_IMAGE_URI);
    }

    /**
     * Check if cursor contains comment data
     *
     * @param cursor Cursor
     * @return True if has comment data
     */
    public static boolean hasComment(@NonNull Cursor cursor) {
        return isColumnNotNull(cursor, DbContract.Positions.COLUMN_COMMENT);
    }

    /**
     * Get latitude from positions cursor
     *
     * @param cursor Cursor
     * @return String latitude
     */
    @Nullable
    public static String getLatitude(@NonNull Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_LATITUDE);
    }

    /**
     * Get longitude from positions cursor
     *
     * @param cursor Cursor
     * @return String longitude
     */
    @Nullable
    public static String getLongitude(@NonNull Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_LONGITUDE);
    }

    /**
     * Get longitude from positions cursor
     *
     * @param cursor Cursor
     * @return Longitude
     */
    private static double getLongitudeAsDouble(@NonNull Cursor cursor) {
        return getColumnAsDouble(cursor, DbContract.Positions.COLUMN_LONGITUDE);
    }

    /**
     * Get latitude from positions cursor
     *
     * @param cursor Cursor
     * @return Longitude
     */
    private static double getLatitudeAsDouble(@NonNull Cursor cursor) {
        return getColumnAsDouble(cursor, DbContract.Positions.COLUMN_LATITUDE);
    }

    /**
     * Get time from positions cursor
     *
     * @param cursor Cursor
     * @return String time
     */
    @Nullable
    public static String getTime(@NonNull Cursor cursor) {
        return getColumnAsString(cursor, DbContract.Positions.COLUMN_TIME);
    }

    /**
     * Get ISO 8601 formatted time from positions cursor
     *
     * @param cursor Cursor
     * @return String time
     */
    @NonNull
    public static String getTimeISO8601(@NonNull Cursor cursor) {
        long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DbContract.Positions.COLUMN_TIME));
        return getTimeISO8601(timestamp);
    }

    /**
     * Get time from positions cursor
     *
     * @param cursor Cursor
     * @return Time
     */
    private static long getTimeAsLong(@NonNull Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(DbContract.Positions.COLUMN_TIME));
    }

    /**
     * Get ID from positions cursor
     *
     * @param cursor Cursor
     * @return String ID
     */
    @NonNull
    public static String getID(@NonNull Cursor cursor) {
        String id = getColumnAsString(cursor, DbContract.Positions._ID);
        return id == null ? "0" : id;
    }

    /**
     * Format unix timestamp as ISO 8601 time
     *
     * @param timestamp Timestamp
     * @return Formatted time
     */
    @NonNull
    public static String getTimeISO8601(long timestamp) {
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
    private static double getColumnAsDouble(@NonNull Cursor cursor, @NonNull String column) {
        return cursor.getDouble(cursor.getColumnIndexOrThrow(column));
    }

    /**
     * Get given column value as string
     *
     * @param cursor Result set
     * @param column Column name
     * @return Column value
     */
    @Nullable
    private static String getColumnAsString(@NonNull Cursor cursor, @NonNull String column) {
        return cursor.getString(cursor.getColumnIndexOrThrow(column));
    }

    /**
     * Check given column is not null
     *
     * @param cursor Result set
     * @param column Column name
     * @return True if not null
     */
    private static boolean isColumnNotNull(@NonNull Cursor cursor, @NonNull String column) {
        return !cursor.isNull(cursor.getColumnIndexOrThrow(column));
    }
}
