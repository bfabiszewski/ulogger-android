/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

import android.location.Location;

import androidx.annotation.NonNull;

import java.text.DecimalFormatSymbols;
import java.util.Locale;

import static java.lang.Math.abs;
import static java.lang.Math.floor;
import static java.lang.Math.round;


class LocationFormatter {

    private final Location location;

    private int[] lat;
    private int[] lon;
    private char latHemisphere;
    private char lonHemisphere;
    private static char separator;

    LocationFormatter(@NonNull Location location) {
        this.location = location;
        convert();
        getSeparator();
    }

    private void getSeparator() {
        DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance();
        separator = dfs.getDecimalSeparator();
    }

    String getLatitudeDMS() {
        return getDMS(lat, latHemisphere);
    }

    String getLongitudeDMS() {
        return getDMS(lon, lonHemisphere);
    }

    @NonNull
    private static String getDMS(int[] coordinate, char hemisphere) {
        return String.format(Locale.US, "%d°%d′%d%c%04d″%c", coordinate[0], coordinate[1], coordinate[2], separator, coordinate[3], hemisphere);
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
