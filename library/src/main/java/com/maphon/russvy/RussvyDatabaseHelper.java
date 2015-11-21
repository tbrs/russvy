package com.maphon.russvy;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Hides dirty details of database management.
 * Not suited to be used from outside.
 * I am really sorry for some of the things you can see here.
 */
final class RussvyDatabaseHelper {
    private static final String TABLE_OPERATOR = "operator";
    private static final String TABLE_REGION = "region";
    private static final String TABLE_RANGE = "range";

    static final String COLUMN_ID = "id";
    static final String COLUMN_NAME = "name";
    static final String COLUMN_REGION_CODE = "code";
    static final String COLUMN_RANGE_START = "start";
    static final String COLUMN_RANGE_CAPACITY = "capacity";
    static final String COLUMN_OPERATOR = "operator";
    static final String COLUMN_REGION = "region";

    static final String SQL_CREATE_TABLE_RANGE = "CREATE TABLE " + TABLE_RANGE + " ("
            + COLUMN_REGION_CODE + " INTEGER, " + COLUMN_RANGE_START + " INTEGER, "
            + COLUMN_RANGE_CAPACITY + " INTEGER, " + COLUMN_OPERATOR + " INTEGER, "
            + COLUMN_REGION +" INTEGER);";
    static final String SQL_CREATE_TABLE_OPERATOR = "CREATE TABLE " + TABLE_OPERATOR + " "
            + "(" + COLUMN_ID + " INTEGER, " + COLUMN_NAME + " TEXT);";
    static final String SQL_CREATE_TABLE_REGION = "CREATE TABLE " + TABLE_REGION + " ("
            + COLUMN_ID + " INTEGER, " + COLUMN_NAME + " TEXT);";

    // Arguments: table name
    static final String SQL_FORMAT_GET_COUNT = "SELECT COUNT(*) FROM %s;";
    // Arguments: table name
    static final String SQL_FORMAT_GET_NAME = "SELECT TOP(1) " + COLUMN_NAME
            + " FROM %s where " + COLUMN_ID + "= %s;";
    // Arguments: table name
    static final String SQL_FORMAT_DROP_TABLE = "DROP TABLE IF EXISTS %s;";
    // Arguments: table name, id, name
    static final String SQL_FORMAT_INSERT_NAME = "INSERT INTO %s (" + COLUMN_ID + ", "
            + COLUMN_NAME + ") VALUES (?, ?);";
    // Arguments: code, range start, range capacity, operator id, region id
    static final String SQL_FORMAT_INSERT_RANGE = "INSERT INTO " + TABLE_RANGE + " ("
            + COLUMN_REGION_CODE + ", " + COLUMN_RANGE_START + ", " + COLUMN_RANGE_CAPACITY
            + ", " + COLUMN_OPERATOR + ", " + COLUMN_REGION + ") " + " VALUES (?, ?, ?, ?, ?);";

    enum Table {
        OPERATOR (TABLE_OPERATOR),
        REGION (TABLE_REGION),
        RANGE (TABLE_RANGE);

        final String name;
        Table(String name) {
            this.name = name;
        }
    }

    static class NumberRange {
        public String code;
        public String start;
        public String capacity;
        public int operatorId;
        public int regionId;

        public NumberRange(String code, String start, String capacity,
                           int operatorId, int regionId) {
            this.code = code;
            this.start = start;
            this.capacity = capacity;
            this.operatorId = operatorId;
            this.regionId = regionId;
        }
    }

    @Nullable
    static String getNameColumn(@NonNull SQLiteDatabase db, @NonNull Table table, int recordId) {
        Cursor cursor = db.query(table.name, new String[]{COLUMN_NAME}, COLUMN_ID + "=?",
                new String[]{String.valueOf(recordId)}, null, null, null, null);
        String name = null;
        if (cursor != null && cursor.moveToFirst()) {
            name = cursor.getString(0);
        }
        RussvyUtils.closeStream(cursor);

        return name;
    }

    // Code is not selected.
    @Nullable
    static Cursor getRegionRanges(@NonNull SQLiteDatabase db, int regionCode) {
        return db.query(TABLE_RANGE, new String[] {
                        COLUMN_RANGE_START, COLUMN_RANGE_CAPACITY,
                        COLUMN_OPERATOR, COLUMN_REGION},
                COLUMN_REGION_CODE + "=?", new String[] { String.valueOf(regionCode) },
                null, null, null, null);
    }

    static long getRecordCount(@NonNull SQLiteDatabase db, @NonNull Table table) {
        try {
            SQLiteStatement statement
                    = db.compileStatement(String.format(SQL_FORMAT_GET_COUNT, table.name));
            return statement.simpleQueryForLong();
        } catch (SQLiteException e) {
            return 0;
        }
    }
}
