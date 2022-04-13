/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import static androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import static net.fabiszewski.ulogger.Alert.showAlert;
import static net.fabiszewski.ulogger.Alert.showConfirm;
import static net.fabiszewski.ulogger.GpxExportTask.GPX_EXTENSION;
import static java.util.concurrent.Executors.newCachedThreadPool;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import java.util.concurrent.ExecutorService;

/**
 * Main activity of ulogger
 *
 */

public class MainActivity extends AppCompatActivity
        implements FragmentManager.OnBackStackChangedListener, MainFragment.OnFragmentInteractionListener,
        GpxExportTask.GpxExportTaskCallback {

    private final String TAG = MainActivity.class.getSimpleName();

    public final static String UPDATED_PREFS = "extra_updated_prefs";

    public String preferenceHost;
    public String preferenceUnits;
    public long preferenceMinTimeMillis;
    public boolean preferenceLiveSync;
    private GpxExportTask gpxExportTask;
    private final ExecutorService executor = newCachedThreadPool();

    private DbAccess db;

    /**
     * Initialization
     * @param savedInstanceState Saved state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        updatePreferences();
        setContentView(R.layout.activity_main);
        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        if (savedInstanceState == null) {
            MainFragment fragment = MainFragment.newInstance();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_placeholder, fragment).commit();
        }
        getSupportFragmentManager().addOnBackStackChangedListener(this);
        //Handle when activity is recreated like on orientation Change
        setHomeUpButton();
    }

    /**
     * On resume
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (Logger.DEBUG) { Log.d(TAG, "[onResume]"); }
        db = DbAccess.getOpenInstance(this);
    }

    /**
     * On pause
     */
    @Override
    protected void onPause() {
        if (Logger.DEBUG) { Log.d(TAG, "[onPause]"); }
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
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.menu_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            settingsLauncher.launch(intent);
            return true;
        } else if (id == R.id.menu_about) {
            showAbout();
            return true;
        } else if (id == R.id.menu_export) {
            startExport();
            return true;
        } else if (id == R.id.menu_clear) {
            clearTrack();
            return true;
        } else if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Reread user preferences
     */
    private void updatePreferences() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        preferenceUnits = prefs.getString(SettingsActivity.KEY_UNITS, getString(R.string.pref_units_default));
        preferenceMinTimeMillis = Long.parseLong(prefs.getString(SettingsActivity.KEY_MIN_TIME, getString(R.string.pref_mintime_default))) * 1000;
        preferenceLiveSync = prefs.getBoolean(SettingsActivity.KEY_LIVE_SYNC, false);
        preferenceHost = prefs.getString(SettingsActivity.KEY_HOST, "").replaceAll("/+$", "");
    }

    /**
     * Display warning if track name is not set
     */
    public void showNoTrackWarning() {
        showToast(getString(R.string.no_track_warning));
    }

    /**
     * Start export service
     */
    private void startExport() {
        if (db.countPositions() > 0) {
            try {
                getExportUri.launch(DbAccess.getTrackName(this) + GPX_EXTENSION);
            } catch (ActivityNotFoundException e) {
                showToast(getString(R.string.cannot_open_picker), Toast.LENGTH_LONG);
            }
        } else {
            showToast(getString(R.string.nothing_to_export));
        }
    }


    private void clearTrack() {
        if (LoggerService.isRunning()) {
            showToast(getString(R.string.logger_running_warning));
            return;
        }
        if (DbAccess.getTrackName(MainActivity.this) != null) {
            showConfirm(MainActivity.this,
                    getString(R.string.warning),
                    getString(R.string.clear_warning),
                    (dialog, which) -> {
                        dialog.dismiss();
                        DbAccess.clearTrack(MainActivity.this);
                        LoggerService.resetLastLocation();
                        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder);
                        if (currentFragment instanceof MainFragment) {
                            currentFragment.onResume();
                        }
                    }
            );
        }
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
        final AlertDialog dialog = showAlert(MainActivity.this,
                getString(R.string.app_name),
                R.layout.about,
                R.drawable.ic_ulogger_logo_24dp);
        final TextView versionLabel = dialog.findViewById(R.id.about_version);
        if (versionLabel != null) {
            versionLabel.setText(getString(R.string.about_version, BuildConfig.VERSION_NAME));
        }
        final TextView descriptionLabel = dialog.findViewById(R.id.about_description);
        final TextView description2Label = dialog.findViewById(R.id.about_description2);
        if (descriptionLabel != null && description2Label != null) {
            descriptionLabel.setText(HtmlCompat.fromHtml(getString(R.string.about_description), HtmlCompat.FROM_HTML_MODE_LEGACY));
            description2Label.setText(HtmlCompat.fromHtml(getString(R.string.about_description2), HtmlCompat.FROM_HTML_MODE_LEGACY));
            descriptionLabel.setMovementMethod(LinkMovementMethod.getInstance());
            description2Label.setMovementMethod(LinkMovementMethod.getInstance());
        }
        final Button okButton = dialog.findViewById(R.id.about_button_ok);
        if (okButton != null) {
            okButton.setOnClickListener(v -> dialog.dismiss());
        }
    }

    private void setHomeUpButton() {
        boolean enabled = getSupportFragmentManager().getBackStackEntryCount() > 0;
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(enabled);
        }
    }

    /**
     * Open file picker to get exported file URI, then run export task
     */
    final ActivityResultLauncher<String> getExportUri = registerForActivityResult(new CreateGpxDocument(), uri -> {
        if (uri != null) {
            runGpxExportTask(uri);
        }
    });

    /**
     * Open settings activity, update preferences on return
     */
    final ActivityResultLauncher<Intent> settingsLauncher = registerForActivityResult(new StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            // Preferences updated
            updatePreferences();
            if (LoggerService.isRunning()) {
                // restart logging
                Intent intent = new Intent(MainActivity.this, LoggerService.class);
                intent.putExtra(UPDATED_PREFS, true);
                startService(intent);
            }
        }
    });

    /**
     * Called whenever the contents of the back stack change.
     */
    @Override
    public void onBackStackChanged() {
        setHomeUpButton();
    }

    /**
     * Start GPX export task
     */
    private void runGpxExportTask(@NonNull Uri uri) {
        if (gpxExportTask == null || !gpxExportTask.isRunning()) {
            gpxExportTask = new GpxExportTask(uri, this);
            executor.execute(gpxExportTask);
            showToast(getString(R.string.export_started));
        }
    }

    @Override
    public void onGpxExportTaskCompleted() {
        showToast(getString(R.string.export_done));
    }

    @Override
    public void onGpxExportTaskFailure(@NonNull String error) {
        String message = getString(R.string.export_failed);
        if (!error.isEmpty()) {
            message += "\n" + error;
        }
        showToast(message);
    }

    @Override
    public Activity getActivity() {
        return this;
    }

}
