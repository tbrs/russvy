package com.maphon.russvy;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads data from assets shipped with the library and stores them into the database.
 * Asset files must be placed all in one directory. There are three of them in total:
 * "ranges": binary data containing information about phone number ranges.
 * "operators": contains strings with operator names.
 * "regions": contains strings with region names.
 * If library assets/resources can not be found, complains about it to logs.
 */
public class RussvyAssetReader {
    private static String TAG = "Russvy";

    private static final String OPERATORS_FILE = "operators";
    private static final String REGIONS_FILE = "regions";
    private static final String RANGES_FILE = "ranges";
    private static final String DATE_FORMAT = "yyyyMMdd";
    private static final String DATA_CHARSET = "UTF-8";

    public static final int DEFAULT_BATCH_COUNT = 1000;

    /** Gets notified about the data reading progress (in %). */
    public interface Listener {
        void onReadProgress(int percentComplete);
    }

    @Nullable
    final private String mAssetDirectory;
    @NonNull
    final private RussvyDatabaseManager mManager;

    @Nullable
    private final Listener mListener;

    /**
     * Initializes reader.
     * @param path path in asset directory the the Rossvyaz data files.
     * @param manager thingy used to access the database.
     * @param listener object which will be notified about the progress of the reading operation.
     */
    public RussvyAssetReader(@Nullable String path, @NonNull RussvyDatabaseManager manager,
                             @Nullable Listener listener) {
        mAssetDirectory = path;
        mManager = manager;
        mListener = listener;
    }

    /**
     * Returns date when the data was published by Rossvyaz.
     */
    @Nullable
    public static Date getDataAge(@NonNull Context context) {
        try {
            return RussvyUtils.parseDate(DATE_FORMAT, context.getString(R.string.russvy_assets_age));
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Data not found (resources).");
            return null;
        }
    }

    /**
     * Does all preparations required for copying data from assets.
     * @return true if everything went fine.
     */
    public boolean open(@NonNull Context context) {
        return mManager.clear() && mManager.prepareInsert();
    }

    /**
     * Extracts data from assets.
     * Reads ranges, operators and regions from asset files and writes all this information to the
     * database. Insertion of the ranges is done in batches. You can tune this providing the
     * appropriate batchCount value.
     * @param context you know, what it is
     * @param batchCount positive number of ranges read from the assets after which a) data is
     *                   flushed to the database b) listener is notified about current progress.
     *                   It's a good idea to use {@link #DEFAULT_BATCH_COUNT}. If you decide to go
     *                   on your own, take into account that the overall number of ranges usually
     *                   is a little less than 300K.
     * @return true if everything went fine.
     */
    public boolean read(@NonNull Context context, int batchCount) {
        if (batchCount <= 0) {
            batchCount = DEFAULT_BATCH_COUNT;
        }
        final long totalRecords = getRecordCount(context);

        long recordCounter = 0;
        try {
            DataInputStream os = getFileInputStream(context, getAssetName(RANGES_FILE));
            if (os == null) {
                return false;
            }
            while(true) {
                try {
                    mManager.addRange(String.valueOf(os.readShort()),
                            String.valueOf(os.readInt()),
                            String.valueOf(os.readInt()),
                            os.readShort(), os.readShort());
                } catch (IOException e) {
                    break;
                }
                recordCounter++;
                if (recordCounter % batchCount == 0) {
                    mManager.flush();
                    if (mListener != null) {
                        mListener.onReadProgress((int) (100 * recordCounter / totalRecords));
                    }
                }
            }
        } catch (OutOfMemoryError e) {
            return false;
        }
        mManager.flush();

        final Map<String, Integer> names = new HashMap<String, Integer>();
        readNames(context, OPERATORS_FILE, names);
        mManager.setOperators(names);
        readNames(context, REGIONS_FILE, names);
        mManager.setRegions(names);

        if (mListener != null) {
            mListener.onReadProgress(100);
        }

        return true;
    }

    /**
     *  Reads lines from file to map.
     *  Lines are saved as keys and line numbers as values.
     *  You get an empty map if something goes wrong.
     */
    private void readNames(@NonNull Context context, @NonNull String file,
                          @NonNull Map<String, Integer> map) {
        map.clear();
        BufferedReader reader = getFileReader(context, getAssetName(file));
        if (reader == null) {
            return;
        }
        String line;
        try {
            while((line = reader.readLine()) != null) {
                map.put(line, map.size());
            }
        } catch (IOException e) {
            map.clear();
            return;
        }
        RussvyUtils.closeStream(reader);
    }

    private static long getRecordCount(@NonNull Context context) {
        try {
            return context.getResources().getInteger(R.integer.russvy_assets_range_count);
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Data not found (resources).");
            return 0;
        }
    }

    @Nullable
    private static BufferedReader getFileReader(Context context, String file) {
        InputStream is = null;
        try {
            is = context.getAssets().open(file);
            return new BufferedReader(new InputStreamReader(is, DATA_CHARSET));
        } catch (FileNotFoundException e) {
            // No need to close stream since opening it was the thing that threw.
            Log.e(TAG, "Data not found (assets).");
            return null;
        } catch (IOException e) {
            RussvyUtils.closeStream(is);
            return null;
        }
    }

    @Nullable
    private static DataInputStream getFileInputStream(Context context, String file) {
        try {
            return new DataInputStream(context.getAssets().open(file));
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Data not found (assets).");
            return null;
        } catch (IOException e) {
            // No need to close stream
            return null;
        }
    }

    private String getAssetName(@NonNull String file) {
        return getAssetName(file, mAssetDirectory);
    }

    private static String getAssetName(@NonNull String file, @Nullable String path) {
        return path == null || path.isEmpty() ? file : path + "/" + file;
    }
}
