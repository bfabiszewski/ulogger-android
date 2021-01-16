/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static net.fabiszewski.ulogger.Alert.showAlert;
import static net.fabiszewski.ulogger.Alert.showConfirm;

@SuppressWarnings("WeakerAccess")
public class MainFragment extends Fragment {

    private final String TAG = MainFragment.class.getSimpleName();

    private final static int LED_GREEN = 1;
    private final static int LED_RED = 2;
    private final static int LED_YELLOW = 3;

    private final static int PERMISSION_LOCATION = 1;

    private final static double KM_MILE = 0.621371;
    private final static double KM_NMILE = 0.5399568;

    private static boolean syncError = false;
    private boolean isUploading = false;

    private TextView syncErrorLabel;
    private TextView syncLabel;
    private TextView syncLed;
    private TextView locLabel;
    private TextView locLed;
    private SwipeSwitch switchLogger;
    private Button buttonShare;

    private PorterDuffColorFilter redFilter;
    private PorterDuffColorFilter greenFilter;
    private PorterDuffColorFilter yellowFilter;

    private OnFragmentInteractionListener mListener;

    public MainFragment() {
    }

    static MainFragment newInstance() {
        return new MainFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ScrollView layout = (ScrollView) inflater.inflate(R.layout.fragment_main, container, false);

        switchLogger = layout.findViewById(R.id.switchLogger);
        Button buttonWaypoint = layout.findViewById(R.id.buttonWaypoint);
        Button buttonUpload = layout.findViewById(R.id.buttonUpload);
        Button buttonNewTrack = layout.findViewById(R.id.buttonNewTrack);
        buttonShare = layout.findViewById(R.id.buttonShare);
        syncErrorLabel = layout.findViewById(R.id.sync_error);
        syncLabel = layout.findViewById(R.id.sync_status);
        syncLed = layout.findViewById(R.id.sync_led);
        locLabel = layout.findViewById(R.id.location_status);
        locLed = layout.findViewById(R.id.loc_led);
        LinearLayout layoutSummary = layout.findViewById(R.id.layoutSummary);

        switchLogger.setOnCheckedChangeListener(this::toggleLogging);
        buttonWaypoint.setOnClickListener(this::addWaypoint);
        buttonUpload.setOnClickListener(this::uploadData);
        buttonNewTrack.setOnClickListener(this::newTrack);
        buttonShare.setOnClickListener(this::shareURL);
        layoutSummary.setOnClickListener(this::trackSummary);
        return layout;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        greenFilter = new PorterDuffColorFilter(ContextCompat.getColor(context, R.color.colorGreen), PorterDuff.Mode.SRC_ATOP);
        redFilter = new PorterDuffColorFilter(ContextCompat.getColor(context, R.color.colorRed), PorterDuff.Mode.SRC_ATOP);
        yellowFilter = new PorterDuffColorFilter(ContextCompat.getColor(context, R.color.colorYellow), PorterDuff.Mode.SRC_ATOP);

        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * On resume
     */
    @Override
    public void onResume() {
        super.onResume();
        if (Logger.DEBUG) { Log.d(TAG, "[onResume]"); }

        Context context = getContext();
        if (context != null) {
            String trackName = DbAccess.getTrackName(context);
            updateTrackLabel(trackName);

            if (LoggerService.isRunning()) {
                switchLogger.setChecked(true);
                setLocLed(LED_GREEN);
            } else {
                switchLogger.setChecked(false);
                setLocLed(LED_RED);
            }
            registerBroadcastReceiver();
            updateStatus();
        }
    }

    /**
     * On pause
     */
    @Override
    public void onPause() {
        if (Logger.DEBUG) { Log.d(TAG, "[onPause]"); }
        Context context = getContext();
        if (context != null) {
            context.unregisterReceiver(broadcastReceiver);
        }
        super.onPause();
    }

    /**
     * Called when the user swipes tracking switch
     * @param view View
     */
    private void toggleLogging(View view, boolean isChecked) {
        if (isChecked && !LoggerService.isRunning()) {
            startLogger(view.getContext());
        } else if (!isChecked && LoggerService.isRunning()) {
            stopLogger(view.getContext());
        }
    }

    /**
     * Start logger service
     */
    private void startLogger(Context context) {
        // start tracking
        if (DbAccess.getTrackName(context) != null) {
            Intent intent = new Intent(context, LoggerService.class);
            context.startService(intent);
        } else {
            if (mListener != null) {
                mListener.showNoTrackWarning();
            }
            switchLogger.setChecked(false);
        }
    }

    /**
     * Stop logger service
     */
    private void stopLogger(Context context) {
        // stop tracking
        Intent intent = new Intent(context, LoggerService.class);
        context.stopService(intent);
    }

    /**
     * Start waypoint activity
     */
    private void addWaypoint(View view) {
        if (DbAccess.getTrackName(view.getContext()) != null) {
            WaypointFragment fragment = WaypointFragment.newInstance();
            FragmentTransaction transaction = getParentFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_placeholder, fragment);
            transaction.addToBackStack(null);
            transaction.commit();
        } else {
            if (mListener != null) {
                mListener.showNoTrackWarning();
            }
        }
    }

    /**
     * Called when the user clicks the New track button
     * @param view View
     */
    private void newTrack(@SuppressWarnings("UnusedParameters") View view) {
        if (LoggerService.isRunning()) {
            showToast(getString(R.string.logger_running_warning));
        } else if (DbAccess.needsSync(view.getContext())) {
            showNotSyncedWarning();
        } else {
            showTrackDialog();
        }
    }

    /**
     * Show toast
     * @param text Text
     */
    private void showToast(String text) {
        Context context = getContext();
        if (context != null) {
            Toast toast = Toast.makeText(requireContext(), text, Toast.LENGTH_LONG);
            toast.show();
        }
    }

    /**
     * Share track URL
     * Called when the user clicks the share button
     * @param view View
     */
    private void shareURL(View view) {
        Context context = view.getContext();
        String trackName = DbAccess.getTrackName(context);
        int trackId = DbAccess.getTrackId(context);
        MainActivity activity = (MainActivity) requireActivity();
        String host = activity.preferenceHost;
        if (trackId > 0 && host.length() > 0) {
            String trackUrl = host + "/#" + trackId;
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, trackUrl);
            sendIntent.putExtra(Intent.EXTRA_TITLE, trackName);
            sendIntent.setType("text/plain");
            Intent shareIntent = Intent.createChooser(sendIntent, getString(R.string.share_link));
            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setData(Uri.parse(trackUrl));
            shareIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{viewIntent});
            startActivity(shareIntent);
        }
    }

    /**
     * Called when the user clicks the Upload button
     * @param view View
     */
    private void uploadData(View view) {
        Context context = view.getContext();
        if (!SettingsFragment.isValidServerSetup(context)) {
            showToast(getString(R.string.provide_user_pass_url));
        } else if (DbAccess.needsSync(context)) {
            Intent syncIntent = new Intent(context, WebSyncService.class);
            WebSyncService.enqueueWork(context, syncIntent);
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
    private void trackSummary(View view) {
        Context context = view.getContext();
        final TrackSummary summary = DbAccess.getTrackSummary(context);
        if (summary == null) {
            showToast(getString(R.string.no_positions));
            return;
        }

        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            final AlertDialog dialog = showAlert(activity,
                    getString(R.string.track_summary),
                    R.layout.summary,
                    R.drawable.ic_equalizer_white_24dp);
            final Button okButton = dialog.findViewById(R.id.summary_button_ok);
            if (okButton != null) {
                okButton.setOnClickListener(v -> dialog.dismiss());
            }
            final TextView summaryDistance = dialog.findViewById(R.id.summary_distance);
            final TextView summaryDuration = dialog.findViewById(R.id.summary_duration);
            final TextView summaryPositions = dialog.findViewById(R.id.summary_positions);
            double distance = (double) summary.getDistance() / 1000;
            String unitName = getString(R.string.unit_kilometer);
            if (activity.preferenceUnits.equals(getString(R.string.pref_units_imperial))) {
                distance *= KM_MILE;
                unitName = getString(R.string.unit_mile);
            } else if (activity.preferenceUnits.equals(getString(R.string.pref_units_nautical))) {
                distance *= KM_NMILE;
                unitName = getString(R.string.unit_nmile);
            }
            final NumberFormat nf = NumberFormat.getInstance();
            nf.setMaximumFractionDigits(2);
            final String distanceString = nf.format(distance);
            if (summaryDistance != null) {
                summaryDistance.setText(getString(R.string.summary_distance, distanceString, unitName));
            }
            final long h = summary.getDuration() / 3600;
            final long m = summary.getDuration() % 3600 / 60;
            if (summaryDuration != null) {
                summaryDuration.setText(getString(R.string.summary_duration, h, m));
            }
            int positionsCount = (int) summary.getPositionsCount();
            if (summaryPositions != null) {
                summaryPositions.setText(getResources().getQuantityString(R.plurals.summary_positions, positionsCount, positionsCount));
            }
        }
    }

    /**
     * Display warning before deleting not synchronized track
     */
    private void showNotSyncedWarning() {
        Context context = getContext();
        if (context != null) {
            showConfirm(context,
                    context.getString(R.string.warning),
                    context.getString(R.string.notsync_warning),
                    (dialog, which) -> {
                        dialog.dismiss();
                        showTrackDialog();
                    }
            );
        }
    }

    /**
     * Display track name dialog
     */
    private void showTrackDialog() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        final AlertDialog dialog = showAlert(activity,
                getString(R.string.title_newtrack),
                R.layout.newtrack_dialog);
        final EditText editText = dialog.findViewById(R.id.newtrack_edittext);
        if (editText == null) {
            return;
        }
        editText.setText(AutoNamePreference.getAutoTrackName(activity));
        editText.setOnClickListener(view -> editText.selectAll());

        final Button submit = dialog.findViewById(R.id.newtrack_button_submit);
        if (submit != null) {
            submit.setOnClickListener(v -> {
                String trackName = editText.getText().toString();
                if (trackName.length() == 0) {
                    showToast(getString(R.string.empty_trackname_warning));
                }
                DbAccess.newTrack(v.getContext(), trackName);
                LoggerService.resetLastLocation();
                updateTrackLabel(trackName);
                updateStatus();
                dialog.cancel();
            });
        }

        final Button cancel = dialog.findViewById(R.id.newtrack_button_cancel);
        if (cancel != null) {
            cancel.setOnClickListener(v -> dialog.cancel());
        }
    }

    /**
     * Update track name label
     * @param trackName Track name
     */
    private void updateTrackLabel(@Nullable String trackName) {
        View view = getView();
        if (view != null) {
            final TextView trackLabel = view.findViewById(R.id.newtrack_label);
            if (trackName == null) {
                trackName = "-";
            }
            trackLabel.setText(trackName);
        }
    }

    /**
     * Update location tracking status label
     * @param lastUpdateRealtime Real time of last location update
     */
    private void updateLocationLabel(long lastUpdateRealtime) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        // get last location update time
        String timeString;
        long timestamp = 0;
        long elapsed = 0;
        long dbTimestamp;
        if (lastUpdateRealtime > 0) {
            elapsed = (SystemClock.elapsedRealtime() - lastUpdateRealtime);
            timestamp = System.currentTimeMillis() - elapsed;
        } else if ((dbTimestamp = DbAccess.getLastTimestamp(context)) > 0) {
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
        MainActivity activity = (MainActivity) requireActivity();
        if (LoggerService.isRunning() && (timestamp == 0 || elapsed > activity.preferenceMinTimeMillis * 2)) {
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
        Context context = getContext();
        if (context == null) {
            return;
        }
        updateShareButton();
        updateLocationLabel(LoggerService.lastUpdateRealtime());
        // get sync status
        int count = DbAccess.countUnsynced(context);
        String error = DbAccess.getError(context);
        if (error != null) {
            if (Logger.DEBUG) { Log.d(TAG, "[sync error: " + error + "]"); }
            setSyncError(error);
        } else {
            resetSyncError();
        }
        updateSyncStatus(count);
    }

    /**
     * Update visibility of share button
     */
    private void updateShareButton() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        if (DbAccess.getTrackId(context) > 0) {
            buttonShare.setVisibility(View.VISIBLE);
        } else {
            buttonShare.setVisibility(View.GONE);
        }
    }

    /**
     * Set status led color
     * @param led Led text view
     * @param color Color (red, yellow or green)
     */
    private void setLedColor(TextView led, int color) {
        Drawable l = TextViewCompat.getCompoundDrawablesRelative(led)[0];
        switch (color) {
            case LED_RED:
                l.setColorFilter(redFilter);
                break;

            case LED_GREEN:
                l.setColorFilter(greenFilter);
                break;

            case LED_YELLOW:
                l.setColorFilter(yellowFilter);
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
     * Set sync error flag and label
     * @param message Error message
     */
    private void setSyncError(String message) {
        syncError = true;
        syncErrorLabel.setText(message);
    }

    /**
     * Reset sync error flag and label
     */
    private void resetSyncError() {
        if (syncError) {
            syncErrorLabel.setText(null);
            syncError = false;
        }
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     */
    public interface OnFragmentInteractionListener {
        void showNoTrackWarning();
    }


    /**
     * Register broadcast receiver for synchronization
     * and tracking status updates
     */
    private void registerBroadcastReceiver() {
        Context context = getContext();
        if (context != null) {
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
            filter.addAction(GpxExportService.BROADCAST_EXPORT_FAILED);
            filter.addAction(GpxExportService.BROADCAST_EXPORT_DONE);
            filter.addAction(WebSyncService.BROADCAST_SYNC_DONE);
            filter.addAction(WebSyncService.BROADCAST_SYNC_FAILED);
            context.registerReceiver(broadcastReceiver, filter);
        }
    }

    /**
     * Broadcast receiver
     */
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Logger.DEBUG) { Log.d(TAG, "[broadcast received " + intent + "]"); }
            if (intent == null || intent.getAction() == null) {
                return;
            }
            MainActivity activity = (MainActivity) getActivity();
            switch (intent.getAction()) {
                case LoggerService.BROADCAST_LOCATION_UPDATED:
                    updateLocationLabel(LoggerService.lastUpdateRealtime());
                    setLocLed(LED_GREEN);
                    if (activity != null && !activity.preferenceLiveSync) {
                        updateSyncStatus(DbAccess.countUnsynced(context));
                    }
                    break;
                case WebSyncService.BROADCAST_SYNC_DONE:
                    final int unsyncedCount = DbAccess.countUnsynced(context);
                    updateSyncStatus(unsyncedCount);
                    setSyncLed(LED_GREEN);
                    // reset error flag and label
                    resetSyncError();
                    // showConfirm message if manual uploading
                    if (isUploading && unsyncedCount == 0) {
                        showToast(getString(R.string.uploading_done));
                        isUploading = false;
                    }
                    if (buttonShare.getVisibility() == View.GONE) {
                        buttonShare.setVisibility(View.VISIBLE);
                    }
                    break;
                case (WebSyncService.BROADCAST_SYNC_FAILED): {
                    updateSyncStatus(DbAccess.countUnsynced(context));
                    setSyncLed(LED_RED);
                    // set error flag and label
                    String message = intent.getStringExtra("message");
                    setSyncError(message);
                    // showConfirm message if manual uploading
                    if (isUploading) {
                        showToast(getString(R.string.uploading_failed) + "\n" + message);
                        isUploading = false;
                    }
                    break;
                }
                case LoggerService.BROADCAST_LOCATION_STARTED:
                    switchLogger.setChecked(true);
                    showToast(getString(R.string.tracking_started));
                    setLocLed(LED_YELLOW);
                    break;
                case LoggerService.BROADCAST_LOCATION_STOPPED:
                    switchLogger.setChecked(false);
                    showToast(getString(R.string.tracking_stopped));
                    setLocLed(LED_RED);
                    break;
                case LoggerService.BROADCAST_LOCATION_GPS_DISABLED:
                    showToast(getString(R.string.gps_disabled_warning));
                    break;
                case LoggerService.BROADCAST_LOCATION_NETWORK_DISABLED:
                    showToast(getString(R.string.net_disabled_warning));
                    break;
                case LoggerService.BROADCAST_LOCATION_DISABLED:
                    showToast(getString(R.string.location_disabled));
                    setLocLed(LED_RED);
                    break;
                case LoggerService.BROADCAST_LOCATION_NETWORK_ENABLED:
                    showToast(getString(R.string.using_network));
                    break;
                case LoggerService.BROADCAST_LOCATION_GPS_ENABLED:
                    showToast(getString(R.string.using_gps));
                    break;
                case GpxExportService.BROADCAST_EXPORT_DONE:
                    showToast(getString(R.string.export_done));
                    break;
                case GpxExportService.BROADCAST_EXPORT_FAILED: {
                    String message = getString(R.string.export_failed);
                    if (intent.hasExtra("message")) {
                        message += "\n" + intent.getStringExtra("message");
                    }
                    showToast(message);
                    break;
                }
                case LoggerService.BROADCAST_LOCATION_PERMISSION_DENIED:
                    showToast(getString(R.string.location_permission_denied));
                    setLocLed(LED_RED);
                    if (activity != null) {
                        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_LOCATION);
                    }
                    break;
            }
        }
    };

    /**
     * Callback on permission request result
     * Called after user granted/rejected location permission
     *
     * @param requestCode Permission code
     * @param permissions Permissions
     * @param grantResults Result
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_LOCATION) {
            if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Context context = getContext();
                if (context != null) {
                    startLogger(context);
                }
            }
        }
    }

}
