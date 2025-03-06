/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger.tasks;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import net.fabiszewski.ulogger.BuildConfig;
import net.fabiszewski.ulogger.Logger;
import net.fabiszewski.ulogger.R;
import net.fabiszewski.ulogger.db.DbAccess;

import org.xmlpull.v1.XmlSerializer;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

/**
 * Export track to GPX format
 */
public class GpxExportTask implements Runnable {

    private static final String TAG = GpxExportTask.class.getSimpleName();

    private static final String ns_gpx = "http://www.topografix.com/GPX/1/1";
    private static final String ns_ulogger = "https://github.com/bfabiszewski/ulogger-android/1";
    private static final String ns_xsi = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String schemaLocation = ns_gpx + " http://www.topografix.com/GPX/1/1/gpx.xsd " +
            ns_ulogger + " https://raw.githubusercontent.com/bfabiszewski/ulogger-server/master/scripts/gpx_extensions1.xsd";

    public static final String GPX_EXTENSION = ".gpx";

    private DbAccess db;

    private final WeakReference<GpxExportTaskCallback> weakCallback;

    private String errorMessage = "";
    private final Uri uri;

    private boolean isRunning = false;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    /**
     * @param uri URI to exported file
     * @param callback Callback activity
     */
    public GpxExportTask(@NonNull Uri uri, @NonNull GpxExportTaskCallback callback) {
        this.uri = uri;
        weakCallback = new WeakReference<>(callback);
    }

    /**
     * Runnable actions
     */
    @Override
    public void run() {
        isRunning = true;
        boolean result = doInBackground();
        uiHandler.post(() -> onPostExecute(result));
        isRunning = false;
    }

    /**
     * Check whether task is running
     * @return True if running
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Actions to run on worker thread
     * @return True on success
     */
    @WorkerThread
    private boolean doInBackground() {
        if (Logger.DEBUG) { Log.d(TAG, "[doInBackground]"); }
        try {
            Activity activity = getActivity();
            if (activity == null) {
                return false;
            }
            Context context = activity.getApplicationContext();
            if (Logger.DEBUG) { Log.d(TAG, "[gpx export start]"); }
            db = DbAccess.getOpenInstance(context);
            write(context, this.uri);
            if (Logger.DEBUG) { Log.d(TAG, "[gpx export stop]"); }
            if (db != null) {
                db.close();
            }
        } catch (IOException e) {
            if (e.getMessage() != null) {
                errorMessage = e.getMessage();
            }
            return false;
        }
        return true;
    }

    /**
     * Post execution actions
     * @param isSuccess Result of task, true if successful
     */
    @UiThread
    private void onPostExecute(boolean isSuccess) {
        GpxExportTaskCallback callback = weakCallback.get();
        if (callback != null && callback.getActivity() != null) {
            if (isSuccess) {
                callback.onGpxExportTaskCompleted();
            } else {
                callback.onGpxExportTaskFailure(errorMessage);
            }
        }
    }

    /**
     * Write serialized track to URI
     * @param uri Target URI
     * @throws IOException Exception
     */
    private void write(@NonNull Context context, @NonNull Uri uri) throws IOException {
        OutputStream stream = context.getContentResolver().openOutputStream(uri);
        if (stream == null) {
            throw new IOException(context.getString(R.string.e_open_out_stream));
        }
        try (BufferedOutputStream bufferedStream = new BufferedOutputStream(stream)) {
            serialize(context, bufferedStream);
            if (Logger.DEBUG) { Log.d(TAG, "[export gpx file written to " + uri); }
        } catch (IOException|IllegalArgumentException|IllegalStateException e) {
            if (Logger.DEBUG) { Log.d(TAG, "[export gpx write exception: " + e + "]"); }
            throw new IOException(e.getMessage());
        }
    }

    /**
     * Serialize and write
     * @param stream Output stream
     * @throws IOException Exception
     */
    private void serialize(@NonNull Context context, @NonNull OutputStream stream) throws IOException {
        XmlSerializer serializer = Xml.newSerializer();
        serializer.setOutput(stream, "UTF-8");

        // header
        serializer.startDocument("UTF-8", true);
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
        serializer.setPrefix("xsi", ns_xsi);
        serializer.setPrefix("ulogger", ns_ulogger);
        serializer.startTag("", "gpx");
        serializer.attribute(null, "xmlns", ns_gpx);
        serializer.attribute(ns_xsi, "schemaLocation", schemaLocation);
        serializer.attribute(null, "version", "1.1");
        String creator = context.getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME;
        serializer.attribute(null, "creator", creator);

        // metadata
        String trackName = db.getTrackName();
        if (trackName == null) {
            trackName = context.getString(R.string.unknown_track);
        }
        long trackTimestamp = db.getFirstTimestamp();
        String trackTime = DbAccess.getTimeISO8601(trackTimestamp);
        serializer.startTag(null, "metadata");
        writeTag(serializer, "name", trackName);
        writeTag(serializer, "time", trackTime);
        serializer.endTag(null, "metadata");

        // track
        serializer.startTag(null, "trk");
        writeTag(serializer, "name", trackName);
        writePositions(serializer);
        serializer.endTag(null, "trk");

        serializer.endTag("", "gpx");
        serializer.endDocument();
        serializer.flush();
    }

    /**
     * Write <trkseg> tag
     *
     * @param serializer XmlSerializer
     * @throws IOException IO exception
     * @throws IllegalArgumentException Xml illegal argument
     * @throws IllegalStateException Xml illegal state
     */
    private void writePositions(@NonNull XmlSerializer serializer)
            throws IOException, IllegalArgumentException, IllegalStateException {

        try (Cursor cursor = db.getPositions()) {
            serializer.startTag(null, "trkseg");
            while (cursor.moveToNext()) {
                serializer.startTag(null, "trkpt");
                serializer.attribute(null, "lat", DbAccess.getLatitude(cursor));
                serializer.attribute(null, "lon", DbAccess.getLongitude(cursor));
                if (DbAccess.hasAltitude(cursor)) {
                    //noinspection DataFlowIssue
                    writeTag(serializer, "ele", DbAccess.getAltitude(cursor));
                }
                writeTag(serializer, "time", DbAccess.getTimeISO8601(cursor));
                writeTag(serializer, "name", DbAccess.getID(cursor));
                if (DbAccess.hasComment(cursor)) {
                    //noinspection DataFlowIssue
                    writeTag(serializer, "desc", DbAccess.getComment(cursor));
                }

                // ulogger extensions (accuracy, speed, bearing, provider)
                serializer.startTag(null, "extensions");
                if (DbAccess.hasAccuracy(cursor)) {
                    //noinspection DataFlowIssue
                    writeTag(serializer, "accuracy", DbAccess.getAccuracy(cursor), ns_ulogger);
                }
                if (DbAccess.hasSpeed(cursor)) {
                    //noinspection DataFlowIssue
                    writeTag(serializer, "speed", DbAccess.getSpeed(cursor), ns_ulogger);
                }
                if (DbAccess.hasBearing(cursor)) {
                    //noinspection DataFlowIssue
                    writeTag(serializer, "bearing", DbAccess.getBearing(cursor), ns_ulogger);
                }
                if (DbAccess.hasProvider(cursor)) {
                    //noinspection DataFlowIssue
                    writeTag(serializer, "provider", DbAccess.getProvider(cursor), ns_ulogger);
                }
                serializer.endTag(null, "extensions");

                serializer.endTag(null, "trkpt");
            }
            serializer.endTag(null, "trkseg");
        }
    }

    /**
     * Write tag without namespace
     *
     * @param serializer XmlSerializer
     * @param name Tag name
     * @param text Tag text
     * @throws IOException IO exception
     * @throws IllegalArgumentException Xml illegal argument
     * @throws IllegalStateException Xml illegal state
     */
    private void writeTag(@NonNull XmlSerializer serializer, @NonNull String name, @NonNull String text)
            throws IOException, IllegalArgumentException, IllegalStateException {
        writeTag(serializer, name, text, null);
    }

    /**
     * Write tag
     *
     * @param serializer XmlSerializer
     * @param name Tag name
     * @param text Tag text
     * @param ns Namespace
     * @throws IOException IO exception
     * @throws IllegalArgumentException Xml illegal argument
     * @throws IllegalStateException Xml illegal state
     */
    private void writeTag(@NonNull XmlSerializer serializer, @NonNull String name, @NonNull String text, String ns)
            throws IOException, IllegalArgumentException, IllegalStateException {
        serializer.startTag(ns, name);
        serializer.text(text);
        serializer.endTag(ns, name);
    }

    /**
     * Get activity from callback
     * @return Activity, null if not available
     */
    @Nullable
    private Activity getActivity() {
        GpxExportTaskCallback callback = weakCallback.get();
        if (callback != null) {
            return callback.getActivity();
        }
        return null;
    }

    /**
     * Callback interface
     */
    public interface GpxExportTaskCallback {
        void onGpxExportTaskCompleted();
        void onGpxExportTaskFailure(@NonNull String error);
        Activity getActivity();
    }

}
