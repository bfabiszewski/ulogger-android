/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.Manifest;
import android.app.IntentService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;

/**
 * Export track to GPX format
 */
public class GpxExportService extends IntentService {

    private static final String TAG = GpxExportService.class.getSimpleName();

    public static final String BROADCAST_WRITE_PERMISSION_DENIED = "net.fabiszewski.ulogger.broadcast.write_permission_denied";
    public static final String BROADCAST_EXPORT_FAILED = "net.fabiszewski.ulogger.broadcast.write_failed";
    public static final String BROADCAST_EXPORT_DONE = "net.fabiszewski.ulogger.broadcast.write_ok";

    private static final String ns_gpx = "http://www.topografix.com/GPX/1/1";
    private static final String ns_ulogger = "https://github.com/bfabiszewski/ulogger-android/1";
    private static final String ns_xsi = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String schemaLocation = ns_gpx + " http://www.topografix.com/GPX/1/1/gpx.xsd " +
            ns_ulogger + " https://raw.githubusercontent.com/bfabiszewski/ulogger-server/master/scripts/gpx_extensions1.xsd";

    private static final String ULOGGER_DIR = "ulogger_tracks";
    private static final String GPX_EXTENSION = ".gpx";

    public GpxExportService() {
        super("GpxExportService");
    }

    private DbAccess db;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Logger.DEBUG) { Log.d(TAG, "[gpx export create]"); }

        db = DbAccess.getInstance();
        db.open(this);
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
    protected void onHandleIntent(Intent intent) {

        if (!hasWritePermission()) {
            // no permission to write
            if (Logger.DEBUG) { Log.d(TAG, "[export gpx no permission]"); }
            sendBroadcast(BROADCAST_WRITE_PERMISSION_DENIED, null);
            return;
        }
        if (!isExternalStorageWritable()) {
            // no access to external storage
            if (Logger.DEBUG) { Log.d(TAG, "[export gpx not writable]"); }
            sendBroadcast(BROADCAST_EXPORT_FAILED, getString(R.string.e_external_not_writable));
            return;
        }

        FileOutputStream fileOutputStream = null;
        try {
            String trackName = db.getTrackName();
            if (trackName == null) {
                trackName = getString(R.string.unknown_track);
            }
            File dir = getDir();
            if (dir == null) {
                if (Logger.DEBUG) { Log.d(TAG, "[export gpx failed to create output folder]"); }
                sendBroadcast(BROADCAST_EXPORT_FAILED, getString(R.string.e_output_dir));
                return;
            }
            File file = getFile(dir, trackName);
            int i = 0;
            while (file.exists()) {
                file = getFile(dir, trackName + "_" + (++i));
            }

            fileOutputStream = new FileOutputStream(file);

            XmlSerializer serializer = Xml.newSerializer();
            StringWriter writer = new StringWriter();

            serializer.setOutput(writer);

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

            String dataWrite = writer.toString();
            fileOutputStream.write(dataWrite.getBytes());
            fileOutputStream.flush();

            if (Logger.DEBUG) { Log.d(TAG, "[export gpx file written to " + file.getPath()); }
            sendBroadcast(BROADCAST_EXPORT_DONE, null);
        } catch (IOException|IllegalArgumentException|IllegalStateException e) {
            if (Logger.DEBUG) { Log.d(TAG, "[export gpx exception: " + e + "]"); }
            sendBroadcast(BROADCAST_EXPORT_FAILED, e.getMessage());
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    if (Logger.DEBUG) { Log.d(TAG, "[export gpx exception: " + e + "]"); }
                    sendBroadcast(BROADCAST_EXPORT_FAILED, e.getMessage());
                }
            }
        }

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

        Cursor cursor = db.getPositions();

        // suppress as it requires target api 19
        //noinspection TryFinallyCanBeTryWithResources
        try {
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
                writeTag(serializer, "provider", DbAccess.getProvider(cursor), ns_ulogger);
                serializer.endTag(null, "extensions");

                serializer.endTag(null, "trkpt");
            }
            serializer.endTag(null, "trkseg");
        } finally {
            cursor.close();
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
     * Has user granted write permission?
     *
     * @return True if permission granted, false otherwise
     */
    private boolean hasWritePermission() {
        return (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * Is there external storage we can write to?
     *
     * @return True if writable, false otherwise
     */
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /**
     * Set up directory in Downloads folder
     *
     * @return File instance or null in case of failure
     */
    private File getDir() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ULOGGER_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            dir = null;
        }
        return dir;
    }

    /**
     * Set up file instance with given name in given folder
     *
     * @param dir Folder
     * @param trackName File name
     * @return File instance
     */
    private File getFile(@NonNull File dir, @NonNull String trackName) {
        String fileName = trackName.replaceAll("[?:\"'*|/\\\\<>]", "_") + GPX_EXTENSION;
        return new File(dir, fileName);
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
