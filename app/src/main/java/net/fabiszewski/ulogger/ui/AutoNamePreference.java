/*
 * Copyright (c) 2019 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import net.fabiszewski.ulogger.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoNamePreference extends TrimmedEditTextPreference {
    private static final Pattern PATTERN = Pattern.compile("%[%ymdHMS]|'");
    private static final Map<String, String> PLACEHOLDERS;
    static {
        PLACEHOLDERS = Map.of(
                "%%", "%",
                "%y", "yyyy",
                "%m", "MM",
                "%d", "dd",
                "%H", "HH",
                "%M", "mm",
                "%S", "ss",
                "'", "''"
        );
    }

    public AutoNamePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public AutoNamePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AutoNamePreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AutoNamePreference(@NonNull Context context) {
        super(context);
    }

    /**
     * Returns the summary of this preference. If a {@link SummaryProvider} has been set for this
     * preference, it will be used to provide the summary returned by this method.
     *
     * @return The summary
     * @see #setSummary(CharSequence)
     * @see #setSummaryProvider(SummaryProvider)
     */
    @Nullable
    @Override
    public CharSequence getSummary() {
        if ((getSummaryProvider() != null)) {
            final String text = super.getText();
            if (text != null && !TextUtils.isEmpty(text)) {
                return getAutoName(text, Calendar.getInstance().getTime());
            }
        }
        return super.getSummary();
    }


    /**
     * Convert user template to SimpleDateFormat pattern
     * User template placeholders: %y (year), %m (month), %d (day),
     * %H (hour), %M (minute), %S (second)
     *
     * @param template User template
     * @return SimpleDateFormat pattern
     */
    @NonNull
    private static String getDateTemplate(@NonNull String template) {
        Matcher matcher = PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        int startPos = 0;
        int endPos;
        final char quot = '\'';
        while (matcher.find()) {
            endPos = matcher.start();
            if (endPos > startPos) {
                sb.append(quot).append(template.substring(startPos, endPos)).append(quot);
            }
            sb.append(PLACEHOLDERS.get(matcher.group()));
            startPos = matcher.end();
        }
        if (startPos < template.length()) {
            sb.append(quot).append(template.substring(startPos)).append(quot);
        }
        return sb.toString();
    }

    /**
     * Get name with pattern format replaced with given date
     *
     * @param template User track name template
     * @param date Date
     * @return String with pattern substituted with current time
     * @throws IllegalArgumentException On illegal pattern
     */
    @NonNull
    static String getAutoName(@NonNull String template, @NonNull Date date) {
        final String pattern = getDateTemplate(template);
        final SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(date);
    }

    /**
     * Get auto-generated track name
     * Default template: Auto_%y.%m.%d_%H.%M.%S
     *
     * @param context Context
     * @return Track name
     */
    @NonNull
    public static String getAutoTrackName(@NonNull Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String template = prefs.getString(SettingsActivity.KEY_AUTO_NAME, context.getString(R.string.pref_auto_name_default));
        return getAutoName(template, Calendar.getInstance().getTime());
    }
}
