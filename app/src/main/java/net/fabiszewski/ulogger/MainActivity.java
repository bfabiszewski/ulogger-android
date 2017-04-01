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
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Main activity of ulogger
 *
 */

public class MainActivity extends AppCompatActivity {

    public static final String UPDATED_PREFS = "extra_updated_prefs";

    private final String TAG = MainActivity.class.getSimpleName();

    private final static int LED_GREEN = 1;
    private final static int LED_RED = 2;
    private final static int LED_YELLOW = 3;

    private final static int PERMISSION_LOCATION = 1;
    private final static int PERMISSION_WRITE = 2;
    private final static int RESULT_PREFS_UPDATED = 1;

    private String pref_units;
    private long pref_minTimeMillis;
    private boolean pref_liveSync;

    private final static double KM_MILE = 0.621371;

    private static boolean syncError = false;
    private boolean isUploading = false;
    private TextView syncErrorLabel;
    private TextView syncLabel;
    private TextView syncLed;
    private TextView locLabel;
    private TextView locLed;

    private DbAccess db;
    private static String TXT_START;
    private static String TXT_STOP;
    private Button toggleButton;

    /**
     * Initialization
     * @param savedInstanceState Saved state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        updatePreferences();
        TXT_START = getString(R.string.button_start);
        TXT_STOP = getString(R.string.button_stop);
        setContentView(R.layout.activity_main);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        toggleButton = (Button) findViewById(R.id.toggle_button);
        syncErrorLabel = (TextView) findViewById(R.id.sync_error);
        syncLabel = (TextView) findViewById(R.id.sync_status);
        syncLed = (TextView) findViewById(R.id.sync_led);
        locLabel = (TextView) findViewById(R.id.location_status);
        locLed = (TextView) findViewById(R.id.loc_led);
    }

    /**
     * On resume
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (Logger.DEBUG) { Log.d(TAG, "[onResume]"); }

        db = DbAccess.getInstance();
        db.open(this);
        String trackName = db.getTrackName();
        if (trackName != null) {
            updateTrackLabel(trackName);
        }

        if (LoggerService.isRunning()) {
            toggleButton.setText(TXT_STOP);
            setLocLed(LED_GREEN);
        } else {
            toggleButton.setText(TXT_START);
            setLocLed(LED_RED);
        }
        registerBroadcastReceiver();
        updateStatus();
    }

    /**
     * On pause
     */
    @Override
    protected void onPause() {
        if (Logger.DEBUG) { Log.d(TAG, "[onPause]"); }
        unregisterReceiver(mBroadcastReceiver);
        if (db != null) {
            db.close();
        }
        super.onPause();
    }

    /**
     * On destroy
     */
    @Override
    protected void onDestroy() {
        if (Logger.DEBUG) { Log.d(TAG, "[onDestroy]"); }
        super.onDestroy();
    }


    /**
     * Create main menu
     * @param menu Menu
     * @return Always true
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /**
     * Main menu options
     * @param item Selected option
     * @return True if handled
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.menu_settings:
                Intent i = new Intent(MainActivity.this, SettingsActivity.class);
                startActivityForResult(i, RESULT_PREFS_UPDATED);
                return true;
            case R.id.menu_about:
                showAbout();
                return true;
            case R.id.menu_export:
                startExport();
                return true;

            default:
                return super.onOptionsItemSelected(item);

        }
    }

    /**
     * Callback on permission request result
     * Called after user granted/rejected location permission
     *
     * @param requestCode Permission code
     * @param permissions Permissions
     * @param grantResults Result
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        // onPause closed db
        db.open(this);
        switch (requestCode) {
            case PERMISSION_LOCATION:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // onPause closed db
                    db.open(this);
                    startLogger();
                }
                break;
            case PERMISSION_WRITE:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    startExport();
                }
                break;
        }
        db.close();
    }

    /**
     * Callback on activity result.
     * Called after user updated preferences
     *
     * @param requestCode Activity code
     * @param resultCode Result
     * @param data Data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case RESULT_PREFS_UPDATED:
                // Preferences updated
                updatePreferences();
                if (LoggerService.isRunning()) {
                    // restart logging
                    Intent intent = new Intent(MainActivity.this, LoggerService.class);
                    intent.putExtra(UPDATED_PREFS, true);
                    startService(intent);
                }
                break;
        }
    }

    /**
     * Reread user preferences
     */
    private void updatePreferences() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        pref_units = prefs.getString("prefUnits", getString(R.string.pref_units_default));
        pref_minTimeMillis = Long.parseLong(prefs.getString("prefMinTime", getString(R.string.pref_mintime_default))) * 1000;
        pref_liveSync = prefs.getBoolean("prefLiveSync", false);
    }

    /**
     * Called when the user clicks the Start/Stop button
     * @param view View
     */
    public void toggleLogging(@SuppressWarnings("UnusedParameters") View view) {
        if (LoggerService.isRunning()) {
            stopLogger();
        } else {
            startLogger();
        }
    }

    /**
     * Start logger service
     */
    private void startLogger() {
        // start tracking
        if (db.getTrackName() != null) {
            Intent intent = new Intent(MainActivity.this, LoggerService.class);
            startService(intent);
        } else {
            showEmptyTrackNameWarning();
        }
    }

    /**
     * Stop logger service
     */
    private void stopLogger() {
        // stop tracking
        Intent intent = new Intent(MainActivity.this, LoggerService.class);
        stopService(intent);
    }

    /**
     * Start export service
     */
    private void startExport() {
        if (db.countPositions() > 0) {
            Intent exportIntent = new Intent(MainActivity.this, GpxExportService.class);
            startService(exportIntent);
            showToast(getString(R.string.export_started));
        } else {
            showToast(getString(R.string.nothing_to_export));
        }
    }

    /**
     * Called when the user clicks the New track button
     * @param view View
     */
    public void newTrack(@SuppressWarnings("UnusedParameters") View view) {
        if (LoggerService.isRunning()) {
            showToast(getString(R.string.logger_running_warning));
        } else if (db.needsSync()) {
            showNotSyncedWarning();
        } else {
            showTrackDialog();
        }
    }

    /**
     * Called when the user clicks the Upload button
     * @param view View
     */
    public void uploadData(@SuppressWarnings("UnusedParameters") View view) {
        if (!SettingsActivity.isValidServerSetup(this)) {
            showToast(getString(R.string.provide_user_pass_url), Toast.LENGTH_LONG);
        } else if (db.needsSync()) {
            Intent syncIntent = new Intent(MainActivity.this, WebSyncService.class);
            startService(syncIntent);
            showToast(getString(R.string.uploading_started));
            isUploading = true;
        } else {
            showToast(getString(R.string.nothing_to_synchronize));
        }
    }

    /**
     * Called when the user clicks the track text view
     * @param view View
     */
    public void trackSummary(@SuppressWarnings("UnusedParameters") View view) {
        final TrackSummary summary = db.getTrackSummary();
        if (summary == null) {
            showToast(getString(R.string.no_positions));
            return;
        }

        @SuppressLint("InflateParams")
        View summaryView = getLayoutInflater().inflate(R.layout.summary, null, false);
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle(getString(R.string.track_summary));
        alertDialog.setView(summaryView);
        alertDialog.setIcon(R.drawable.ic_equalizer_white_24dp);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();

        final TextView summaryDistance = (TextView) alertDialog.findViewById(R.id.summary_distance);
        final TextView summaryDuration = (TextView) alertDialog.findViewById(R.id.summary_duration);
        final TextView summaryPositions = (TextView) alertDialog.findViewById(R.id.summary_positions);
        double distance = (double) summary.getDistance() / 1000;
        String unitName = getString(R.string.unit_kilometer);
        if (pref_units.equals(getString(R.string.pref_units_imperial))) {
            distance *= KM_MILE;
            unitName = getString(R.string.unit_mile);
        }
        final NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(2);
        final String distanceString = nf.format(distance);
        summaryDistance.setText(getString(R.string.summary_distance, distanceString, unitName));
        final long h = summary.getDuration() / 3600;
        final long m = summary.getDuration() % 3600 / 60;
        summaryDuration.setText(getString(R.string.summary_duration, h, m));
        summaryPositions.setText(getResources().getQuantityString(R.plurals.summary_positions, (int) summary.getPositionsCount(), (int) summary.getPositionsCount()));
    }

    /**
     * Display toast message
     * @param text Message
     */
    private void showToast(CharSequence text) {
        showToast(text, Toast.LENGTH_SHORT);
    }

    /**
     * Display toast message
     * @param text Message
     * @param duration Duration
     */
    private void showToast(CharSequence text, int duration) {
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    /**
     * Display About dialog
     */
    private void showAbout() {
        @SuppressLint("InflateParams")
        View view = getLayoutInflater().inflate(R.layout.about, null, false);
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle(getString(R.string.app_name));
        alertDialog.setView(view);
        alertDialog.setIcon(R.drawable.ic_ulogger_logo_24dp);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
        final TextView versionLabel = (TextView) alertDialog.findViewById(R.id.about_version);
        versionLabel.setText(getString(R.string.about_version, BuildConfig.VERSION_NAME));
        final TextView descriptionLabel = (TextView) alertDialog.findViewById(R.id.about_description);
        final TextView description2Label = (TextView) alertDialog.findViewById(R.id.about_description2);
        final String server_link = getString(R.string.ulogger_server_link, getString(R.string.ulogger_server));
        final String app_link = getString(R.string.app_link, getString(R.string.homepage, getString(R.string.app_name)));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            descriptionLabel.setText(fromHtmlDepreciated(getString(R.string.about_description, server_link)));
            description2Label.setText(fromHtmlDepreciated(getString(R.string.about_description2, app_link)));
        } else {
            descriptionLabel.setText(Html.fromHtml(getString(R.string.about_description, server_link), android.text.Html.FROM_HTML_MODE_LEGACY));
            description2Label.setText(Html.fromHtml(getString(R.string.about_description2, app_link), android.text.Html.FROM_HTML_MODE_LEGACY));
        }
    }

    /**
     * Depreciated fromHtml method for build version < 24
     * @param text Message text
     * @return Text with parsed html
     */
    @SuppressWarnings("deprecation")
    private static CharSequence fromHtmlDepreciated(String text) {
        return Html.fromHtml(text);
    }

    /**
     * Display warning before deleting not synchronized track
     */
    private void showNotSyncedWarning() {
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle(getString(R.string.warning));
        alertDialog.setMessage(getString(R.string.notsync_warning));
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        showTrackDialog();
                    }
                });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }

    /**
     * Display warning if track name is not set
     */
    private void showEmptyTrackNameWarning() {
        showToast(getString(R.string.empty_trackname_warning));
    }

    /**
     * Display track name dialog
     */
    private void showTrackDialog() {
        final Dialog dialog = new Dialog(MainActivity.this);
        dialog.setTitle(R.string.title_newtrack);
        dialog.setContentView(R.layout.newtrack_dialog);
        final EditText editText = (EditText) dialog.findViewById(R.id.newtrack_edittext);
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getDefault());
        final String dateSuffix = sdf.format(Calendar.getInstance().getTime());
        final String autoName = "Auto_" + dateSuffix;
        editText.setText(autoName);
        editText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                editText.selectAll();
            }
        });
        dialog.show();

        final Button submit = (Button) dialog.findViewById(R.id.newtrack_button_submit);
        submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String trackName = editText.getText().toString();
                if (trackName.length() == 0) {
                    return;
                }
                db.newTrack(trackName);
                LoggerService.resetUpdateRealtime();
                updateTrackLabel(trackName);
                updateStatus();
                dialog.cancel();
            }
        });

        final Button cancel = (Button) dialog.findViewById(R.id.newtrack_button_cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.cancel();
            }
        });
    }

    /**
     * Update track name label
     * @param trackName Track name
     */
    private void updateTrackLabel(String trackName) {
        final TextView trackLabel = (TextView) findViewById(R.id.newtrack_label);
        trackLabel.setText(trackName);
    }

    /**
     * Update location tracking status label
     * @param lastUpdateRealtime Real time of last location update
     */
    private void updateLocationLabel(long lastUpdateRealtime) {
        // get last location update time
        String timeString;
        long timestamp = 0;
        long elapsed = 0;
        long dbTimestamp;
        if (lastUpdateRealtime > 0) {
            elapsed = (SystemClock.elapsedRealtime() - lastUpdateRealtime);
            timestamp = System.currentTimeMillis() - elapsed;
        } else if ((dbTimestamp = db.getLastTimestamp()) > 0) {
            timestamp = dbTimestamp * 1000;
            elapsed = System.currentTimeMillis() - timestamp;
        }

        if (timestamp > 0) {
            final Date updateDate = new Date(timestamp);
            final Calendar calendar = Calendar.getInstance();
            calendar.setTime(updateDate);
            final Calendar today = Calendar.getInstance();
            SimpleDateFormat sdf;

            if (calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    && calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
                sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            } else {
                sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            }
            sdf.setTimeZone(TimeZone.getDefault());
            timeString = String.format(getString(R.string.label_last_update), sdf.format(updateDate));
        } else {
            timeString = "-";
        }
        locLabel.setText(timeString);
        // Change led if more than 2 update periods elapsed since last location update
        if (LoggerService.isRunning() && (timestamp == 0 || elapsed > pref_minTimeMillis * 2)) {
            setLocLed(LED_YELLOW);
        }
    }

    /**
     * Update synchronization status label and led
     * @param unsynced Count of not synchronized positions
     */
    private void updateSyncStatus(int unsynced) {
        String text;
        if (unsynced > 0) {
            text = getResources().getQuantityString(R.plurals.label_positions_behind, unsynced, unsynced);
            if (syncError) {
                setSyncLed(LED_RED);
            } else {
                setSyncLed(LED_YELLOW);
            }
        } else {
            text = getString(R.string.label_synchronized);
            setSyncLed(LED_GREEN);
        }

        syncLabel.setText(text);
    }

    /**
     * Update location tracking and synchronization status
     */
    private void updateStatus() {
        updateLocationLabel(LoggerService.lastUpdateRealtime());
        // get sync status
        int count = db.countUnsynced();
        String error = db.getError();
        if (error != null) {
            if (Logger.DEBUG) { Log.d(TAG, "[sync error: " + error + "]"); }
            syncError = true;
            syncErrorLabel.setText(error);
        } else if (syncError) {
            syncError = false;
            syncErrorLabel.setText(null);
        }
        updateSyncStatus(count);
    }

    /**
     * Set status led color
     * @param led Led text view
     * @param color Color (red, yellow or green)
     */
    private void setLedColor(TextView led, int color) {
        Drawable l;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            l = led.getCompoundDrawables()[0];
        } else {
            l = led.getCompoundDrawablesRelative()[0];
        }
        switch (color) {
            case LED_RED:
                l.setColorFilter(ContextCompat.getColor(this, R.color.colorRed), PorterDuff.Mode.SRC_ATOP);
                break;

            case LED_GREEN:
                l.setColorFilter(ContextCompat.getColor(this, R.color.colorGreen), PorterDuff.Mode.SRC_ATOP);
                break;

            case LED_YELLOW:
                l.setColorFilter(ContextCompat.getColor(this, R.color.colorYellow), PorterDuff.Mode.SRC_ATOP);
                break;
        }
        l.invalidateSelf();
    }

    /**
     * Set synchronization status led color
     * Red - synchronization error
     * Yellow - synchronization delay
     * Green - synchronized
     * @param color Color
     */
    private void setSyncLed(int color) {
        if (Logger.DEBUG) { Log.d(TAG, "[setSyncLed " + color + "]"); }
        setLedColor(syncLed, color);
    }

    /**
     * Set location tracking status led color
     * Red - tracking off
     * Yellow - tracking on, long time since last update
     * Green - tracking on, recently updated
     * @param color Color
     */
    private void setLocLed(int color) {
        if (Logger.DEBUG) { Log.d(TAG, "[setLocLed " + color + "]"); }
        setLedColor(locLed, color);
    }

    /**
     * Register broadcast receiver for synchronization
     * and tracking status updates
     */
    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(LoggerService.BROADCAST_LOCATION_STARTED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_STOPPED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_UPDATED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_DISABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_GPS_DISABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_NETWORK_DISABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_GPS_ENABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_NETWORK_ENABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_PERMISSION_DENIED);
        filter.addAction(GpxExportService.BROADCAST_WRITE_PERMISSION_DENIED);
        filter.addAction(GpxExportService.BROADCAST_EXPORT_FAILED);
        filter.addAction(GpxExportService.BROADCAST_EXPORT_DONE);
        filter.addAction(WebSyncService.BROADCAST_SYNC_DONE);
        filter.addAction(WebSyncService.BROADCAST_SYNC_FAILED);
        registerReceiver(mBroadcastReceiver, filter);
    }

    /**
     * Broadcast receiver
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Logger.DEBUG) { Log.d(TAG, "[broadcast received " + intent + "]"); }
            if (intent.getAction().equals(LoggerService.BROADCAST_LOCATION_UPDATED)) {
                updateLocationLabel(LoggerService.lastUpdateRealtime());
                setLocLed(LED_GREEN);
                if (!pref_liveSync) {
                    updateSyncStatus(db.countUnsynced());
                }
            } else if (intent.getAction().equals(WebSyncService.BROADCAST_SYNC_DONE)) {
                final int unsyncedCount = db.countUnsynced();
                updateSyncStatus(unsyncedCount);
                setSyncLed(LED_GREEN);
                // reset error flag and label
                if (syncError) {
                    syncErrorLabel.setText(null);
                    syncError = false;
                }
                // show message if manual uploading
                if (isUploading && unsyncedCount == 0) {
                    showToast(getString(R.string.uploading_done));
                    isUploading = false;
                }
            } else if (intent.getAction().equals((WebSyncService.BROADCAST_SYNC_FAILED))) {
                updateSyncStatus(db.countUnsynced());
                setSyncLed(LED_RED);
                // set error flag and label
                String message = intent.getStringExtra("message");
                syncErrorLabel.setText(message);
                syncError = true;
                // show message if manual uploading
                if (isUploading) {
                    showToast(getString(R.string.uploading_failed) + "\n" + message, Toast.LENGTH_LONG);
                    isUploading = false;
                }
            } else if (intent.getAction().equals(LoggerService.BROADCAST_LOCATION_STARTED)) {
                toggleButton.setText(TXT_STOP);
                showToast(getString(R.string.tracking_started));
                setLocLed(LED_YELLOW);
            } else if (intent.getAction().equals(LoggerService.BROADCAST_LOCATION_STOPPED)) {
                toggleButton.setText(TXT_START);
                showToast(getString(R.string.tracking_stopped));
                setLocLed(LED_RED);
            } else if (intent.getAction().equals(LoggerService.BROADCAST_LOCATION_GPS_DISABLED)) {
                showToast(getString(R.string.gps_disabled_warning), Toast.LENGTH_LONG);
            } else if (intent.getAction().equals(LoggerService.BROADCAST_LOCATION_NETWORK_DISABLED)) {
                showToast(getString(R.string.net_disabled_warning), Toast.LENGTH_LONG);
            } else if (intent.getAction().equals(LoggerService.BROADCAST_LOCATION_DISABLED)) {
                showToast(getString(R.string.location_disabled), Toast.LENGTH_LONG);
                setLocLed(LED_RED);
            } else if (intent.getAction().equals(LoggerService.BROADCAST_LOCATION_NETWORK_ENABLED)) {
                showToast(getString(R.string.using_network), Toast.LENGTH_LONG);
            } else if (intent.getAction().equals(LoggerService.BROADCAST_LOCATION_GPS_ENABLED)) {
                showToast(getString(R.string.using_gps), Toast.LENGTH_LONG);
            } else if (intent.getAction().equals(GpxExportService.BROADCAST_EXPORT_DONE)) {
                showToast(getString(R.string.export_done), Toast.LENGTH_LONG);
            } else if (intent.getAction().equals(GpxExportService.BROADCAST_EXPORT_FAILED)) {
                String message = getString(R.string.export_failed);
                if (intent.hasExtra("message")) {
                    message += "\n" + intent.getStringExtra("message");
                }
                showToast(message, Toast.LENGTH_LONG);
            } else if (intent.getAction().equals(LoggerService.BROADCAST_LOCATION_PERMISSION_DENIED)) {
                showToast(getString(R.string.location_permission_denied), Toast.LENGTH_LONG);
                setLocLed(LED_RED);
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_LOCATION);
            } else if (intent.getAction().equals(GpxExportService.BROADCAST_WRITE_PERMISSION_DENIED)) {
                showToast(getString(R.string.write_permission_denied), Toast.LENGTH_LONG);
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_WRITE);
            }
        }
    };
}
