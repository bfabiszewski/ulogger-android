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
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

import org.xmlpull.v1.XmlSerializer;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Export track to GPX format
 */
public class GpxExportService extends JobIntentService {

    private static final String TAG = GpxExportService.class.getSimpleName();

    public static final String BROADCAST_EXPORT_FAILED = "net.fabiszewski.ulogger.broadcast.write_failed";
    public static final String BROADCAST_EXPORT_DONE = "net.fabiszewski.ulogger.broadcast.write_ok";

    private static final String ns_gpx = "http://www.topografix.com/GPX/1/1";
    private static final String ns_ulogger = "https://github.com/bfabiszewski/ulogger-android/1";
    private static final String ns_xsi = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String schemaLocation = ns_gpx + " http://www.topografix.com/GPX/1/1/gpx.xsd " +
            ns_ulogger + " https://raw.githubusercontent.com/bfabiszewski/ulogger-server/master/scripts/gpx_extensions1.xsd";

    public static final String GPX_EXTENSION = ".gpx";
    public static final String GPX_MIME = "application/gpx+xml";

    private DbAccess db;

    static final int JOB_ID = 1000;

    /**
     * Convenience method for enqueuing work in to this service.
     */
    static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, GpxExportService.class, JOB_ID, work);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Logger.DEBUG) { Log.d(TAG, "[gpx export create]"); }

        db = DbAccess.getOpenInstance(this);
    }

    /**
     * Cleanup
     */
    @Override
    public void onDestroy() {
        if (Logger.DEBUG) { Log.d(TAG, "[gpx export stop]"); }
        if (db != null) {
            db.close();
        }
        super.onDestroy();
    }

    /**
     * Handle intent
     *
     * @param intent Intent
     */
    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        if (intent.getData() != null) {
            try {
                write(intent.getData());
                sendBroadcast(BROADCAST_EXPORT_DONE, null);
            } catch (IOException e) {
                sendBroadcast(BROADCAST_EXPORT_FAILED, e.getMessage());
            }
        }
    }

    /**
     * Write serialized track to URI
     * @param uri Target URI
     * @throws IOException Exception
     */
    private void write(@NonNull Uri uri) throws IOException {
        OutputStream stream = getContentResolver().openOutputStream(uri);
        if (stream == null) {
            throw new IOException(getString(R.string.e_open_out_stream));
        }
        try (BufferedOutputStream bufferedStream = new BufferedOutputStream(stream)) {
            serialize(bufferedStream);
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
    private void serialize(@NonNull OutputStream stream) throws IOException {
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
        String creator = getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME;
        serializer.attribute(null, "creator", creator);

        // metadata
        String trackName = db.getTrackName();
        if (trackName == null) {
            trackName = getString(R.string.unknown_track);
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
                    writeTag(serializer, "ele", DbAccess.getAltitude(cursor));
                }
                writeTag(serializer, "time", DbAccess.getTimeISO8601(cursor));
                writeTag(serializer, "name", DbAccess.getID(cursor));
                if (DbAccess.hasComment(cursor)) {
                    writeTag(serializer, "desc", DbAccess.getComment(cursor));
                }

                // ulogger extensions (accuracy, speed, bearing, provider)
                serializer.startTag(null, "extensions");
                if (DbAccess.hasAccuracy(cursor)) {
                    writeTag(serializer, "accuracy", DbAccess.getAccuracy(cursor), ns_ulogger);
                }
                if (DbAccess.hasSpeed(cursor)) {
                    writeTag(serializer, "speed", DbAccess.getSpeed(cursor), ns_ulogger);
                }
                if (DbAccess.hasBearing(cursor)) {
                    writeTag(serializer, "bearing", DbAccess.getBearing(cursor), ns_ulogger);
                }
                if (DbAccess.hasProvider(cursor)) {
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
     * Send broadcast message
     * @param broadcast Broadcast intent
     * @param message Optional extra message
     */
    private void sendBroadcast(String broadcast, String message) {
        Intent intent = new Intent(broadcast);
        if (message != null) {
            intent.putExtra("message", message);
        }
        sendBroadcast(intent);
    }

}
