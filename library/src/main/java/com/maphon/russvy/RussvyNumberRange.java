package com.maphon.russvy;

import android.database.Cursor;
import android.support.annotation.NonNull;

/** Utilities for getting the range data. */
public final class RussvyNumberRange {
    /** Returns range code or -1 if cursor does not contain necessary data. */
    public static int getCode(@NonNull Cursor cursor) {
        return getInt(cursor, RussvyDatabaseHelper.COLUMN_REGION_CODE);
    }

    /** Returns range start or -1 if cursor does not contain necessary data. */
    public static int getStart(@NonNull Cursor cursor) {
        return getInt(cursor, RussvyDatabaseHelper.COLUMN_RANGE_START);
    }

    /** Returns range capacity or -1 if cursor does not contain necessary data. */
    public static int getCapacity(@NonNull Cursor cursor) {
        return getInt(cursor, RussvyDatabaseHelper.COLUMN_RANGE_CAPACITY);
    }

    /** Returns range operator id or -1 if cursor does not contain necessary data. */
    public static int getOperatorId(@NonNull Cursor cursor) {
        return getInt(cursor, RussvyDatabaseHelper.COLUMN_OPERATOR);
    }

    /** Returns range region id or -1 if cursor does not contain necessary data. */
    public static int getRegionId(@NonNull Cursor cursor) {
        return getInt(cursor, RussvyDatabaseHelper.COLUMN_REGION);
    }

    private static int getInt(@NonNull Cursor cursor, @NonNull String column) {
        final int columnIndex = cursor.getColumnIndex(column);
        return columnIndex >= 0 ? cursor.getInt(columnIndex) : -1;
    }
}
