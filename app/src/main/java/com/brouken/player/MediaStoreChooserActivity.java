package com.brouken.player;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MediaStoreChooserActivity extends Activity {

    public static final String BUCKET_ID = "BUCKET_ID";
    public static final String SUBTITLES = "SUBTITLES";
    public static final String TITLE = "TITLE";

    final int REQUEST_PERMISSION_STORAGE = 0;

    Integer bucketId;
    boolean subtitles;
    String title;

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        if (intent.hasExtra(BUCKET_ID)) {
            this.bucketId = intent.getIntExtra(BUCKET_ID, Integer.MIN_VALUE);
        }
        this.subtitles = intent.getBooleanExtra(SUBTITLES, false);
        this.title = intent.getStringExtra(TITLE);

        String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        if (Build.VERSION.SDK_INT >= 33 && getApplicationContext().getApplicationInfo().targetSdkVersion >= 33) {
            permission = Manifest.permission.READ_MEDIA_VIDEO;
        }

        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            start();
        } else {
            requestPermissions(new String[]{permission}, REQUEST_PERMISSION_STORAGE);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            setResult(RESULT_OK, data);
            finish();
        } else {
            start();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void start() {
        if (bucketId == null) {
            Utils.scanMediaStorage(this);
            showBuckets();
        } else {
            showFiles(bucketId);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    start();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    HashMap<Integer, String> query(String projectionId, String projectionName, String selection) {
        Uri collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        if (subtitles) {
            collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
        }
        HashMap<Integer, String> hashMap = new HashMap<>();
        try (Cursor cursor = getContentResolver().query(collection, new String[] { projectionId, projectionName }, selection, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnId = cursor.getColumnIndex(projectionId);
                int columnName = cursor.getColumnIndex(projectionName);

                do {
                    int id = cursor.getInt(columnId);
                    String name = cursor.getString(columnName);
                    if (name == null) {
                        continue;
                    }
                    if (!hashMap.containsKey(id)) {
                        hashMap.put(id, name);
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception x) {
            x.printStackTrace();
        }
        // Sort map by value
        List<Map.Entry<Integer, String>> list = new LinkedList<>(hashMap.entrySet());
        Collections.sort(list, (o1, o2) -> o1.getValue().compareToIgnoreCase(o2.getValue()));
        HashMap<Integer, String> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> map : list) {
            sortedMap.put(map.getKey(), map.getValue());
        }
        return sortedMap;
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    void showBuckets() {
        String selection = "";
        if (subtitles) {
            selection += MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_SUBTITLE;
        }

        HashMap<Integer, String> buckets = query(MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME, selection);

        Integer[] bucketIds = buckets.keySet().toArray(new Integer[0]);
        String[] bucketDisplayNames = buckets.values().toArray(new String[0]);

        AlertDialog.Builder alertDialogBuilder;
        if (buckets.size() == 0) {
            alertDialogBuilder = new AlertDialog.Builder(this);
            alertDialogBuilder.setMessage(R.string.mediastore_empty);
        } else {
            alertDialogBuilder = new AlertDialog.Builder(this, R.style.MediaStoreChooserDialog);
            alertDialogBuilder.setTitle(getString(R.string.choose_file));
            alertDialogBuilder.setView(cardList(bucketDisplayNames, bucketIds, true));
            /*alertDialogBuilder.setItems(bucketDisplayNames, (dialogInterface, i) -> {
                Intent intent = new Intent(MediaStoreChooserActivity.this, MediaStoreChooserActivity.class);
                intent.putExtra(SUBTITLES, subtitles);
                intent.putExtra(BUCKET_ID, bucketIds[i]);
                intent.putExtra(TITLE, bucketDisplayNames[i]);
                startActivityForResult(intent, 0);
            });*/
        }
        alertDialogBuilder.setOnCancelListener(dialogInterface -> finish());
        alertDialogBuilder.show();
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    void showFiles(int bucketId) {
        String selection = MediaStore.MediaColumns.BUCKET_ID + "=" + bucketId;

        if (subtitles) {
            selection += " AND " + MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_SUBTITLE;
        }

        HashMap<Integer, String> files = query(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, selection);

        Integer[] ids = files.keySet().toArray(new Integer[0]);
        String[] displayNames = files.values().toArray(new String[0]);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this, R.style.MediaStoreChooserDialog);
        if (title != null) {
            alertDialogBuilder.setTitle(title);
        }
        alertDialogBuilder.setView(cardList(displayNames, ids, false));
        /*alertDialogBuilder.setItems(displayNames, (dialogInterface, i) -> {
            Uri contentUri;
            if (subtitles) {
                contentUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL, ids[i]);
            } else {
                contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ids[i]);
            }
            Intent data = new Intent("RESULT", contentUri);
            setResult(RESULT_OK, data);
            finish();
        });*/
        alertDialogBuilder.setOnCancelListener(dialogInterface -> finish());
        alertDialogBuilder.show();
    }

    private View cardList(String[] names, Integer[] ids, boolean buckets) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(24, 8, 24, 8);
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(24, 12, 20, 8);
            TextView titleView = new TextView(this);
            titleView.setText(names[i]);
            titleView.setTextColor(Color.WHITE);
            titleView.setTextSize(18);
            titleView.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(24, 0, 20, 0);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(36, 39, 43));
            bg.setCornerRadius(14);
            card.setBackground(bg);
            if (!buckets) {
                Uri videoUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ids[i]);
                long position = Prefs.savedPosition(this, videoUri);
                card.setContentDescription(names[i] + (position > 0 ? "  •  продолжить" : ""));
            }
            card.addView(titleView, new LinearLayout.LayoutParams(-1, 48));
            ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);
            int percent = positionPercent(names[i], ids[i], buckets);
                progress.setProgress(percent);
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(214, 168, 79)));
            card.addView(progress, new LinearLayout.LayoutParams(-1, 6));
            if (!buckets && progress.getProgress() >= 95) {
                titleView.setText("✓  " + names[i]);
                titleView.setTextColor(Color.rgb(214, 168, 79));
                bg.setStroke(2, Color.rgb(214, 168, 79));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 72);
            lp.setMargins(0, 6, 0, 6);
            list.addView(card, lp);
            card.setOnClickListener(v -> {
                if (buckets) {
                    Intent intent = new Intent(MediaStoreChooserActivity.this, MediaStoreChooserActivity.class);
                    intent.putExtra(SUBTITLES, subtitles);
                    intent.putExtra(BUCKET_ID, ids[index]);
                    intent.putExtra(TITLE, names[index]);
                    startActivityForResult(intent, 0);
                } else {
                    Uri contentUri = subtitles
                            ? MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL, ids[index])
                            : ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ids[index]);
                    setResult(RESULT_OK, new Intent("RESULT", contentUri));
                    finish();
                }
            });
        }
        return list;
    }

    private int positionPercent(String name, int id, boolean buckets) {
        if (buckets) return 0;
        Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
        long position = Prefs.savedPosition(this, uri);
        if (position <= 0) return 0;
        long duration = 0;
        try (Cursor c = getContentResolver().query(uri,
                new String[]{MediaStore.Video.VideoColumns.DURATION}, null, null, null)) {
            if (c != null && c.moveToFirst()) duration = c.getLong(0);
        } catch (Exception ignored) { }
        if (duration <= 0) {
            try {
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                r.setDataSource(this, uri);
                String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                duration = d == null ? 0 : Long.parseLong(d);
                r.release();
            } catch (Exception ignored) { }
        }
        return duration > 0 ? Math.min(100, (int) ((position * 100L) / duration)) : 0;
    }
}
