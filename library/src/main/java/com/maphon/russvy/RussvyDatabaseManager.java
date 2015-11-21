package com.maphon.russvy;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.maphon.russvy.RussvyDatabaseHelper.NumberRange;
import com.maphon.russvy.RussvyDatabaseHelper.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Provides access to the data: number ranges, operators and regions.
 * Number range is a continuous sequence of telephone numbers granted to one operator at a
 * a particular region. Range contains the following data:
 * - code
 * - start
 * - capacity
 * - region id
 * - operator id
 * See {@link #addRange(String, String, String, int, int)} for more details about them.
 * Operator is a name of mobile operator accredited in Russian Federation. One operator can
 * hold multiple number ranges.
 * Region is a name of a territorial district in Russian Federation which a particular number
 * range belongs to.
 */
public class RussvyDatabaseManager extends SQLiteOpenHelper {
    /**
     * Database version, used for proper migration.
     * The following versions are supported:
     * 1 - first DB version
     */
    private static final int DB_VERSION = 1;

    /** Temporary storage for inserted number ranges. Used for batching insertions. */
    @Nullable
    private List<NumberRange> mPendingInserts;
    @Nullable
    private SQLiteStatement mInsertRangeStatement;

    /**
     * Creates database manager.
     * @param context application context
     * @param databaseName unique name used by application for the database
     *                     that should keep all the data.
     */
    public RussvyDatabaseManager(Context context, @NonNull String databaseName) {
        super(context, databaseName, null, DB_VERSION);
    }

    /**
     * Removes all data from the database.
     * @return true if database was successfully cleaned, false otherwise.
     */
    public boolean clear() {
        for (Table table : Table.values()) {
            if (!clearTable(table)) {
                return false;
            }
        }
        if (mPendingInserts != null) {
            mPendingInserts.clear();
        }
        return true;
    }

    /** Removes all ranges from database. No chaining. */
    public boolean clearRanges() {
        return clearTable(Table.RANGE);
    }

    /** Removes all operators from database. No chaining. */
    public boolean clearOperators() {
        return clearTable(Table.OPERATOR);
    }

    /** Removes all regions from database. No chaining. */
    public boolean clearRegions() {
        return clearTable(Table.REGION);
    }

    /**
     * Initializes manager for inserting new data.
     * @throws IllegalStateException if called when there are pending inserts.
     */
    public boolean prepareInsert() throws IllegalStateException {
        if (mPendingInserts != null && mPendingInserts.size() != 0) {
            throw new IllegalStateException("Initialization on dirty state.");
        }
        mPendingInserts = new ArrayList<NumberRange>();
        mInsertRangeStatement = getReadableDatabase().compileStatement(
                RussvyDatabaseHelper.SQL_FORMAT_INSERT_RANGE);
        return true;
    }

    /**
     * Inserts information about operators in the database.
     * @param names map "operator name"->"operator id", where id is the unique identifier of
     *              the operator in the database used as a foreign key in number ranges.
     */
    public boolean setOperators(Map<String, Integer> names) {
        return setTableData(names, Table.OPERATOR);
    }

    /**
     * Inserts information about regions in the database.
     * @param names map "region name"->"region id", where id is the unique identifier of
     *              the region in the database used as a foreign key in number range record.
     */
    public boolean setRegions(Map<String, Integer> names) {
        return setTableData(names, Table.REGION);
    }

    /**
     * Inserts new number range record in the database.
     * @param code operator/region code a set of regional numbers belong to. E.g. '921'.
     * @param start first telephone number in range. E.g. '5000000'.
     * @param capacity the count of consequent telephone numbers, beginning with 'start', which
     *                 belong to this range. For example, if start is 1000000, and capacity is 10,
     *                 then last number in this range is 1000009.
     * @param operatorId id of the operator which owns the range, makes sense only inside the code.
     *                   Use {@link #setOperators(Map)} to add new operators to the database,
     *                   and {@link #getOperatorName(int)} to get name of the operator which can be
     *                   displayed to the user.
     * @param regionId id of the region which a particular number range belongs to, makes sense
     *                 only inside the code. Use {@link #setRegions(Map)} to add new regions to the
     *                 database and {@link #getRegionName(int)} to get name of the region which can
     *                 be displayed to the user.
     * @throws IllegalStateException if called before {@link #prepareInsert()}
     */
    public void addRange(String code, String start, String capacity, int operatorId, int regionId)
            throws IllegalStateException {
        if (mPendingInserts == null) {
            throw new IllegalStateException("Database not ready for new data.");
        }
        mPendingInserts.add(new NumberRange(code, start, capacity, operatorId, regionId));
    }

    /**
     * Writes all pending inserts into the database.
     * Should be called at least once at the end of all insertions.
     */
    public boolean flush() {
        if (mInsertRangeStatement == null || mPendingInserts == null || mPendingInserts.size() == 0) {
            return true;
        }
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            // We need explicit transactions here since it's outside SQLiteOpenHelper's callbacks
            db.beginTransaction();
            for (NumberRange range : mPendingInserts) {
                // It appears that bindString is much faster than bindLong
                mInsertRangeStatement.bindString(1, range.code);
                mInsertRangeStatement.bindString(2, range.start);
                mInsertRangeStatement.bindString(3, range.capacity);
                mInsertRangeStatement.bindLong(4, range.operatorId);
                mInsertRangeStatement.bindLong(5, range.regionId);
                mInsertRangeStatement.execute();
            }
            mPendingInserts.clear();
            db.setTransactionSuccessful();
        } catch (SQLiteException e) {
            return false;
        } finally {
            if (db != null) {
                db.endTransaction();
            }
        }
        return true;
    }

    /** Returns total amount of phone number ranges in database. */
    public long getRangeCount() {
        return RussvyDatabaseHelper.getRecordCount(getReadableDatabase(), Table.RANGE);
    }

    /** Returns total amount of operators in database. */
    public long getOperatorCount() {
        return RussvyDatabaseHelper.getRecordCount(getReadableDatabase(), Table.OPERATOR);
    }

    /** Returns total amount of regions in database. */
    public long getRegionCount() {
        return RussvyDatabaseHelper.getRecordCount(getReadableDatabase(), Table.REGION);
    }

    /**
     * Returns all ranges for the selected region code.
     * Cursor data does not contain region code column.
     */
    public Cursor getRanges(int regionCode) {
        return RussvyDatabaseHelper.getRegionRanges(getReadableDatabase(), regionCode);
    }

    /**
     * Returns name of the operator.
     * @param id operator id used in database to assign an operator for a range. Can be retrieved,
     *           for example, by calling {@link #getRanges(int)}.
     * @return operator name or null if there is no operator with the id specified
     */
    @Nullable
    public String getOperatorName(int id) {
        return getNameFromTable(Table.OPERATOR, id);
    }

    /**
     * Returns name of the region.
     * @param id region id used in database to assign an operator for a range. Can be retrieved,
     *           for example, by calling {@link #getRanges(int)}.
     * @return region name or null if there is no region with the id specified.
     */
    @Nullable
    public String getRegionName(int id) {
        return getNameFromTable(Table.REGION, id);
    }

    /** Called automatically when opening database after a clean install/data wipe. */
    @Override
    public void onCreate(SQLiteDatabase db) {
        createTables(db);
    }

    /** Called automatically when opening database of version older than {@link #DB_VERSION}. */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Intentionally blank. Add code here when database scheme changes in the future.
    }

    private void createTables(SQLiteDatabase db) {
        db.execSQL(RussvyDatabaseHelper.SQL_CREATE_TABLE_REGION);
        db.execSQL(RussvyDatabaseHelper.SQL_CREATE_TABLE_OPERATOR);
        db.execSQL(RussvyDatabaseHelper.SQL_CREATE_TABLE_RANGE);
    }

    private boolean clearTable(Table table) {
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            db.beginTransaction();
            db.execSQL(String.format(RussvyDatabaseHelper.SQL_FORMAT_DROP_TABLE, table.name));
            switch (table) {
                case RANGE:
                    db.execSQL(RussvyDatabaseHelper.SQL_CREATE_TABLE_RANGE);
                    break;
                case OPERATOR:
                    db.execSQL(RussvyDatabaseHelper.SQL_CREATE_TABLE_OPERATOR);
                    break;
                case REGION:
                    db.execSQL(RussvyDatabaseHelper.SQL_CREATE_TABLE_REGION);
                    break;
            }
            db.setTransactionSuccessful();
        } catch (SQLiteException e) {
            return false;
        } finally {
            if (db != null) {
                db.endTransaction();
            }
        }
        return true;
    }

    @Nullable
    private String getNameFromTable(@NonNull Table table, int id) {
        return RussvyDatabaseHelper.getNameColumn(getReadableDatabase(), table, id);
    }

    // Map (instead of SparseArray) is used since we expect thousands of names
    private boolean setTableData(Map<String, Integer> names, Table table) {
        if (!table.equals(Table.OPERATOR) && !table.equals(Table.REGION)) {
            return false;
        }
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            db.beginTransaction();
            final SQLiteStatement statement = db.compileStatement(
                    String.format(RussvyDatabaseHelper.SQL_FORMAT_INSERT_NAME, table.name));
            for (Map.Entry<String, Integer> e : names.entrySet()) {
                statement.bindLong(1, e.getValue());
                statement.bindString(2, e.getKey());
                statement.execute();
            }
            db.setTransactionSuccessful();
        } catch (SQLiteException e) {
            return false;
        } finally {
            if (db != null) {
                db.endTransaction();
            }
        }
        return true;
    }
}
