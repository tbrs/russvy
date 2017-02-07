package com.maphon.russvy.demo;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.maphon.russvy.RussvyAssetReader;
import com.maphon.russvy.RussvyDatabaseManager;
import com.maphon.russvy.RussvyNumberRange;

import java.util.LinkedList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String RUSSVY_DB_NAME = "russvy";
    private static final String SHARED_PREFS_NAME = "russvy_demo";
    private static final String KEY_DATA_EXTRACTED = "data_extracted";

    @Nullable
    private SharedPreferences mPrefs;
    @Nullable
    private RussvyDatabaseManager mRussvyManager;
    @Nullable
    private ProgressBar mExtractProgressBar;
    @Nullable
    private EditText mSearchEdit;

    /**
     * Looks up all number ranges that belong to the specified region.
     */
    public void onRegionSearch(View v) {
        final String searchTerm = getSearchTerm();
        final int code;
        try {
            code = Integer.parseInt(searchTerm);
        } catch (NumberFormatException e) {
            Toast.makeText(MainActivity.this,
                    String.format(getString(R.string.message_search_not_a_region), searchTerm),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        new AsyncTask<Void, Void, List<String>>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                MainActivity.this.findViewById(R.id.search_progress).setVisibility(View.VISIBLE);
                MainActivity.this.findViewById(R.id.search_results).setVisibility(View.GONE);
                MainActivity.this.findViewById(R.id.button_region).setEnabled(false);
            }

            @Override
            protected void onPostExecute(List<String> items) {
                super.onPostExecute(items);
                if (items.isEmpty()) {
                    Toast.makeText(MainActivity.this, getString(R.string.message_search_empty),
                            Toast.LENGTH_SHORT).show();
                }
                ((ListView) findViewById(R.id.search_results)).setAdapter(
                        new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1,
                                items));
                MainActivity.this.findViewById(R.id.search_progress).setVisibility(View.GONE);
                MainActivity.this.findViewById(R.id.search_results).setVisibility(View.VISIBLE);
                MainActivity.this.findViewById(R.id.button_region).setEnabled(true);
            }

            @Override
            protected List<String> doInBackground(Void... voids) {
                final Cursor cursor = mRussvyManager.getRanges(code);
                if (cursor == null || cursor.getCount() == 0) return null;
                List<String> items = new LinkedList<>();
                while (cursor.moveToNext()) {
                    items.add(formatRange(cursor));
                }
                return items;
            }
        }.execute();
    }

    /** Extracts data from Russvy and stores it into the DB. */
    public void onExtractData(View v) {
        v.setEnabled(false);
        final RussvyAssetReader reader = new RussvyAssetReader(null, mRussvyManager,
                new RussvyAssetReader.Listener() {
                    @Override
                    public void onReadProgress(int percentComplete) {
                        if (mExtractProgressBar != null) {
                            mExtractProgressBar.setProgress(percentComplete);
                        }
                    }
                });
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                fadeInProgressBar();
            }

            @Override
            protected Void doInBackground(Void... params) {
                reader.open(MainActivity.this);
                reader.read(MainActivity.this, RussvyAssetReader.DEFAULT_BATCH_COUNT);
                return null;
            }

            @Override
            protected void onPostExecute(Void param) {
                super.onPostExecute(param);

                setDataExtracted();
                Toast.makeText(MainActivity.this, R.string.message_extract_complete,
                        Toast.LENGTH_LONG).show();
                fadeOutExtractControls();
            }
        }.execute();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPrefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
        mRussvyManager = new RussvyDatabaseManager(this, RUSSVY_DB_NAME);

        setupControls();
    }

    private void setupControls() {
        final boolean dataReady = isDataExtracted();
        final int id = dataReady ? R.id.search_stub : R.id.extract_stub;
        final ViewStub uiStub = (ViewStub) findViewById(id);
        if (uiStub != null) {
            uiStub.inflate();
            if (dataReady) {
                final long rangeCount = mRussvyManager.getRangeCount();
                final long regionCount = mRussvyManager.getRegionCount();
                final long operatorCount = mRussvyManager.getOperatorCount();
                ((TextView) findViewById(R.id.toolbar_ranges_count))
                        .setText(String.format(getString(R.string.label_ranges), rangeCount));
                ((TextView) findViewById(R.id.toolbar_regions_count))
                        .setText(String.format(getString(R.string.label_regions), regionCount));
                ((TextView) findViewById(R.id.toolbar_operators_count))
                        .setText(String.format(getString(R.string.label_operators), operatorCount));
                mSearchEdit = (EditText) findViewById(R.id.edit_search);
                findViewById(R.id.button_region).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onRegionSearch(null);
                    }
                });
                removeExtractControls();
            } else {
                mExtractProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
            }
        }
    }

    private void fadeOutExtractControls() {
        final AlphaAnimation animation = new AlphaAnimation(1f, 0f);
        animation.setDuration(
                getResources().getInteger(R.integer.fadeout_animation_duration));
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                setupControls();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        findViewById(R.id.extract_controls).startAnimation(animation);
    }

    private void removeExtractControls() {
        final View controls = findViewById(R.id.extract_controls);
        if (controls != null) ((ViewGroup) controls.getParent()).removeView(controls);
    }

    private void fadeInProgressBar() {
        mExtractProgressBar.setVisibility(View.INVISIBLE);
        final AlphaAnimation animation = new AlphaAnimation(0f, 1f);
        animation.setDuration(
                getResources().getInteger(R.integer.fadeout_animation_duration));
        animation.setFillAfter(true);
        mExtractProgressBar.startAnimation(animation);
    }

    private String getSearchTerm() {
        return mSearchEdit.getText().toString();
    }

    private void setDataExtracted() {
        if (mPrefs != null) {
            mPrefs.edit().putBoolean(KEY_DATA_EXTRACTED, true).apply();
        }
    }

    private boolean isDataExtracted() {
        return mPrefs != null && mPrefs.contains(KEY_DATA_EXTRACTED);
    }

    private String formatRange(Cursor cursor) {
        final StringBuilder sb = new StringBuilder();
        final int start = RussvyNumberRange.getStart(cursor);
        sb.append(start);
        sb.append(" - ");
        sb.append(start + RussvyNumberRange.getCapacity(cursor) - 1);
        sb.append(": ");
        final int operatorId = RussvyNumberRange.getOperatorId(cursor);
        sb.append(mRussvyManager.getOperatorName(operatorId));
        return sb.toString();
    }
}
