/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger.utils;

import static java.lang.Math.abs;
import static java.lang.Math.floor;
import static java.lang.Math.round;

import android.content.Context;
import android.location.Location;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.fabiszewski.ulogger.R;

import java.text.DecimalFormatSymbols;
import java.util.Locale;


public class LocationFormatter {

    private final Location location;

    private int[] lat;
    private int[] lon;
    private char latHemisphere;
    private char lonHemisphere;
    private static char separator;

    public LocationFormatter(@NonNull Location location) {
        this.location = location;
        convert();
        getSeparator();
    }

    private void getSeparator() {
        DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance();
        separator = dfs.getDecimalSeparator();
    }

    @NonNull
    public String getLatitudeDMS() {
        return getDMS(lat, latHemisphere);
    }

    @NonNull
    public String getLongitudeDMS() {
        return getDMS(lon, lonHemisphere);
    }

    @NonNull
    private static String getDMS(@NonNull int[] coordinate, char hemisphere) {
        return String.format(Locale.US, "%d°%d′%d%c%04d″%c", coordinate[0], coordinate[1], coordinate[2], separator, coordinate[3], hemisphere);
    }

    /**
     * Get text line with location details
     * @param context Context
     * @return Text
     */
    @NonNull
    public String getDetails(@NonNull Context context) {
        String details = "";
        if (!location.hasAccuracy() && !location.hasAltitude() && location.getProvider() == null) {
            return details;
        }
        String provider = getProvider(context);
        String altitude = getAltitude(context);
        String accuracy = getAccuracy(context);

        if (altitude != null) {
            details += altitude;
        }
        if (accuracy != null) {
            if (!details.isEmpty()) {
                details += " • ";
            }
            details += accuracy;
        }
        if (provider != null) {
            if (!details.isEmpty()) {
                details += " ";
            }
            details += String.format(Locale.getDefault(), "(%s)", provider);
        }
        return details;
    }

    /**
     * Get provider string
     * @param context Context
     * @return Text
     */
    @Nullable
    private String getProvider(@NonNull Context context) {
        String provider;
        if (LocationHelper.isGps(location)) {
            provider = context.getString(R.string.provider_gps);
        } else if (LocationHelper.isNetwork(location)) {
            provider = context.getString(R.string.provider_network).toLowerCase(Locale.getDefault());
        } else {
            provider = location.getProvider();
        }
        return provider;
    }

    /**
     * Get accuracy string
     * @return Text
     */
    @Nullable
    private String getAccuracy(@NonNull Context context) {
        String accuracy = null;
        if (location.hasAccuracy()) {
            accuracy = String.format(Locale.getDefault(),
                    context.getString(R.string.accuracy_meters), (int) location.getAccuracy());
        }
        return accuracy;
    }

    /**
     * Get altitude string
     * @return Text
     */
    @Nullable
    private String getAltitude(@NonNull Context context) {
        String altitude = null;
        if (location.hasAltitude()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
                altitude = String.format(Locale.getDefault(),
                        "%d±%d", (int) location.getAltitude(),
                        (int) location.getVerticalAccuracyMeters());
            } else {
                altitude = String.format(Locale.getDefault(),
                        "%d", (int) location.getAltitude());
            }
            altitude = String.format(Locale.getDefault(),context.getString(R.string.meters_asl), altitude);
        }
        return altitude;
    }

    /**
     * Convert to DMS
     */
    private void convert() {
        lat = toDegrees(location.getLatitude());
        lon = toDegrees(location.getLongitude());
        latHemisphere = (location.getLatitude() >= 0) ? 'N' : 'S';
        lonHemisphere = (location.getLongitude() >= 0) ? 'E' : 'W';
    }

    /**
     * Split double to degrees
     * @param input Location coordinate
     * @return Array of [ degree, minute, second, remainder/10000 ]
     */
    private static int[] toDegrees(double input) {
        int[] output = new int[4];
        input = abs(input);
        for (int i = 0; i < 3; i++) {
            output[i] = (int) floor(input);
            input -= output[i];
            input *= (i == 2) ? 10000.0 : 60.0;
        }
        output[3] = (int) round(input);
        return output;
    }
}
