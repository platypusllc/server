package com.platypus.android.server.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Simple conversion class to generate ISO-8601 date strings from Date().
 * Based on: http://entzeroth.com/android-convert-to-iso8601/
 */
public class ISO8601Date {
    /**
     * Date format representing the ISO-8601 specification.
     */
    static final SimpleDateFormat ISO8601_FORMAT =
            new SimpleDateFormat("yyyy/MM/dd'T'HH:mm:ssZ", Locale.ENGLISH);

    /**
     * Returns an ISO-8601 compliant string that represents the current date.
     *
     * @return an ISO-8601 compliant string representing the current date
     */
    public static String now() {
        return convert(new Date());
    }

    /**
     * Converts a Java Date into an ISO-8601 compliant string.
     *
     * @param date the date that should be converted
     * @return an ISO-8601 compliant string representing the given date
     */
    public static String convert(Date date) {
        TimeZone timezone = TimeZone.getTimeZone("UTC");
        synchronized (ISO8601_FORMAT) {
            ISO8601_FORMAT.setTimeZone(timezone);
            return ISO8601_FORMAT.format(date);
        }
    }
}
