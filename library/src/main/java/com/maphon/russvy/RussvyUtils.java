package com.maphon.russvy;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class RussvyUtils {
    @Nullable
    public static Date parseDate(@NonNull String format, @Nullable String date) {
        if (date == null) {
            return null;
        }
        final SimpleDateFormat formatter = new SimpleDateFormat(format, Locale.US);
        formatter.setLenient(false);
        try {
            return formatter.parse(date);
        } catch (ParseException e) {
            return null;
        }
    }

    public static void closeStream(@Nullable Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                // intentionally blank
            }
        }
    }

    // Cursor does not implement Closeable in API < 16.
    public static void closeCursor(@Nullable Cursor c) {
        if (c != null) c.close();
    }

    private RussvyUtils() {
    }
}
