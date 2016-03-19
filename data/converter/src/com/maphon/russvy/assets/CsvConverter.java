package com.maphon.russvy.assets;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class CsvConverter {
    /** All data files provided by Rossvyaz. */
    private static final String[] CVS_FILES = {"Kody_ABC-3kh.csv", "Kody_ABC-4kh.csv", "Kody_ABC-8kh.csv", "Kody_DEF-9kh.csv"};
    /** Charset used in data files. */
    private static final String CSV_CHARSET = "windows-1251";
    // Data file format specification.
    private static final char CSV_SEPARATOR = ';';
    private static final int CSV_INDEX_CODE = 0;
    private static final int CSV_INDEX_START = 1;
    private static final int CSV_INDEX_END = 2;
    private static final int CSV_INDEX_CAPACITY = 3;
    private static final int CSV_INDEX_OPERATOR = 4;
    private static final int CSV_INDEX_REGION = 5;
    private static final int CSV_COLUMN_COUNT = 6;
    /** Charset used when creating asset files. */
    private static final String ASSET_DATA_CHARSET = "UTF-8";

    private static final String RESOURCE_FORMAT = "<resources>\n" +
            "    <string name=\"russvy_assets_age\">%s</string>\n" +
            "    <integer name=\"russvy_assets_range_count\">%d</integer>\n" +
            "</resources>";

    private final String mCsvDirectory;
    private final String mAssetDirectory;
    private final String mResourceDirectory;

    private int mRecordCount;

    /**
     * Converts raw CSV files from Rossvyaz to the assets for Russvy library.
     * @param args command line arguments:
     *             - source directory with CSV files
     *             - output directory for the assets
     *             - output directory for the resources
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Insufficient command line arguments.");
        }
        final String srcDir = args[0];
        final String assetDir = args[1];
        final String resourceDir = args[2];

        for (String dir : args) {
            if (!isDirectory(dir)) {
                System.err.println("\"" + dir + "\" does not exists or is not a directory.");
            }
        }

        final CsvConverter converter = new CsvConverter(srcDir, assetDir, resourceDir);
        if (converter.generateAssets()) {
            System.out.println("Ranges processed:");
            System.out.println(converter.getRecordCount());
            converter.generateResources();
        }
    }

    public int getRecordCount() {
        return mRecordCount;
    }

    private CsvConverter(String csvDir, String assetDir, String resourceDir) {
        mCsvDirectory = csvDir;
        mAssetDirectory = assetDir;
        mResourceDirectory = resourceDir;
    }

    private boolean generateAssets() {
        mRecordCount = 0;
        // We need the keys to be in the order of insertion, but TreeMap is a slower option.
        final Map<String, Integer> operators = new LinkedHashMap<>();
        final Map<String, Integer> regions = new LinkedHashMap<>();

        // Write ranges
        final DataOutputStream os = getFileOutputStream(mAssetDirectory + "/" + "ranges");
        if (os == null) {
            System.err.println("Failed to create output file for ranges.");
            return false;
        }
        final String[] data = new String[CSV_COLUMN_COUNT];
        String line;
        for(String file : CVS_FILES) {
            try {
                final BufferedReader reader = getDataFileReader(mCsvDirectory + "/" + file);
                if (reader == null) {
                    System.err.println("Skipped CSV file: " + file);
                    continue;
                }
                // Skip header
                reader.readLine();
                while ((line = reader.readLine()) != null) {
                    parseRecord(line, data);
                    final String operator = data[CSV_INDEX_OPERATOR];
                    if (!operators.keySet().contains(operator)) {
                        operators.put(operator, operators.size());
                    }
                    final String region = data[CSV_INDEX_REGION];
                    if (!regions.keySet().contains(region)) {
                        regions.put(region, regions.size());
                    }
                    // Code is less than 1000, so 2 bytes should be enough
                    os.writeShort(Integer.valueOf(data[CSV_INDEX_CODE]));
                    os.writeInt(Integer.valueOf(data[CSV_INDEX_START]));
                    os.writeInt(Integer.valueOf(data[CSV_INDEX_CAPACITY]));
                    // Presumably there will be no more than 10k operators/regions, so short again
                    os.writeShort(operators.get(operator));
                    os.writeShort(regions.get(region));
                    mRecordCount++;
                }
            } catch (IOException e) {
                System.err.println("Failed to create output file for ranges.");
                e.printStackTrace();
                return false;
            } catch (OutOfMemoryError e) {
                System.err.println("Failed to create output file for ranges.");
                e.printStackTrace();
                return false;
            }
        }
        closeStream(os);
        // Write operators
        if (!writeKeysToFile(operators, "operators")) {
            System.err.println("Failed to create output file for operators.");
            return false;
        }
        // Write regions
        if (!writeKeysToFile(regions, "regions")) {
            System.err.println("Failed to create output file for regions.");
            return false;
        }

        return true;
    }

    private void generateResources() {
        final BufferedWriter writer = getAssetFileWriter(mResourceDirectory + "/russvy.xml");
        if (writer == null) {
            System.err.println("Failed to create resource file.");
            return;
        }
        try {
            writer.write(String.format(RESOURCE_FORMAT, getResourceTimestamp(), mRecordCount));
        } catch (IOException e) {
            System.err.println("Failed to update resource output file.");
        } finally {
            closeStream(writer);
        }
    }

    private String getResourceTimestamp() {
        final Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd", Locale.US);
        return format.format(calendar.getTime());
    }

    private void parseRecord(String record, String[] values) {
        int i = 0;
        int pos = 0;
        int nextPos;
        final int lineLength = record.length();
        while (i < CSV_COLUMN_COUNT - 1 && pos < lineLength) {
            nextPos = record.indexOf(CSV_SEPARATOR, pos);
            values[i] = record.substring(pos, nextPos).trim();
            pos = nextPos;
            pos++;
            i++;
        }
        if (pos < lineLength) {
            values[CSV_COLUMN_COUNT - 1] = record.substring(pos).trim();
        }
    }

    private boolean writeKeysToFile(Map<String, Integer> map, String file) {
        final BufferedWriter writer = getAssetFileWriter(mAssetDirectory + "/" + file);
        if (writer == null) {
            return false;
        }
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            try {
                writer.write(entry.getKey());
                writer.newLine();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        closeStream(writer);
        return true;
    }

    private static BufferedReader getDataFileReader(String file) {
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            return new BufferedReader(new InputStreamReader(is, CSV_CHARSET));
        } catch (IOException e) {
            closeStream(is);
            return null;
        }
    }

    private static DataOutputStream getFileOutputStream(String file) {
        try {
            return new DataOutputStream(new FileOutputStream(file));
        } catch (IOException e) {
            return null;
        }
    }

    private static BufferedWriter getAssetFileWriter(String file) {
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            return new BufferedWriter(new OutputStreamWriter(os, ASSET_DATA_CHARSET));
        } catch (IOException e) {
            closeStream(os);
            return null;
        }
    }

    private static void closeStream(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                // nop
            }
        }
    }

    private static boolean isDirectory(String path) {
        final Path p = Paths.get(path);
        return Files.exists(p) && Files.isDirectory(p);
    }
}