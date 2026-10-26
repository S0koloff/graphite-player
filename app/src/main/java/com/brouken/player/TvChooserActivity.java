package com.brouken.player;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.provider.MediaStore;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public final class TvChooserActivity extends Activity {
    private static final String TAG = "TvChooser";
    private final List<Video> videos = new ArrayList<>();
    private final List<String> folders = new ArrayList<>();
    private GridView grid;
    private boolean showingFolders = true;
    private View selectedStorage;
    private TextView titleView;
    private final class Video { Uri uri; String name; Video(Uri u, String n) { uri=u; name=n; } }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_tv_chooser);
        grid = findViewById(R.id.grid);
        titleView = findViewById(R.id.title);
        View internal = findViewById(R.id.internal);
        View usb = findViewById(R.id.usb);
        internal.setBackgroundResource(R.drawable.sidebar_item);
        usb.setBackgroundResource(R.drawable.sidebar_item);
        internal.setOnClickListener(v -> selectStorage(v, MediaStore.VOLUME_EXTERNAL_PRIMARY));
        usb.setOnClickListener(v -> selectStorage(v, MediaStore.VOLUME_EXTERNAL));
        String permission = android.os.Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_VIDEO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{permission}, 42);
        else selectStorage(findViewById(R.id.usb), MediaStore.VOLUME_EXTERNAL);
    }

    private void selectStorage(View item, String volume) {
        if (selectedStorage != null) selectedStorage.setSelected(false);
        selectedStorage = item;
        item.setSelected(true);
        item.setBackgroundResource(R.drawable.sidebar_item);
        item.setClipToOutline(true);
        item.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 8);
            }
        });
        load(volume);
    }

    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                || event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER) {
            Log.d(TAG, "key code=" + event.getKeyCode() + " action=" + event.getAction()
                    + " focus=" + (getCurrentFocus() == null ? "none" : getCurrentFocus().getClass().getSimpleName()));
        }
        if ((event.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                || event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)
                && event.getAction() == android.view.KeyEvent.ACTION_UP) {
            View focused = getCurrentFocus();
            if (focused != null && focused.getTag() instanceof String) {
                Log.d(TAG, "dispatch opening folder=" + focused.getTag());
                showFolder((String) focused.getTag());
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        if ((keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == android.view.KeyEvent.KEYCODE_ENTER)
                && showingFolders && grid != null) {
            int position = grid.getSelectedItemPosition();
            Log.d(TAG, "grid position=" + position + " folders=" + folders.size());
            if (position >= 0 && position < folders.size()) {
                showFolder(folders.get(position));
                return true;
            }
        }
        if ((keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == android.view.KeyEvent.KEYCODE_ENTER) && !showingFolders && grid != null) {
            int position = grid.getSelectedItemPosition();
            if (position >= 0 && position < videos.size()) {
                Video video = videos.get(position);
                Log.d(TAG, "open video position=" + position + " uri=" + video.uri);
                setResult(RESULT_OK, new Intent("RESULT", video.uri));
                finish();
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 42 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
            load(MediaStore.VOLUME_EXTERNAL);
    }

    private void load(String volume) {
        videos.clear();
        folders.clear();
        showingFolders = true;
        titleView.setText("GRAPHITE PLAYER");
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Uri collection = MediaStore.Video.Media.getContentUri(volume);
        try (Cursor c = getContentResolver().query(collection,
                new String[]{MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.BUCKET_DISPLAY_NAME},
                null, null, MediaStore.Video.Media.DISPLAY_NAME + " COLLATE NOCASE")) {
            if (c != null) while (c.moveToNext()) {
                String folder = c.getString(2);
                if (folder != null && folder.startsWith("$")) continue;
                if (folder != null && !folders.contains(folder)) folders.add(folder);
                videos.add(new Video(ContentUris.withAppendedId(collection, c.getLong(0)), c.getString(1)));
            }
        } catch (Exception ignored) { }
        grid.setAdapter(new FolderAdapter());
        grid.requestFocus();
    }

    private final class FolderAdapter extends BaseAdapter {
        public int getCount() { return folders.size(); }
        public Object getItem(int p) { return folders.get(p); }
        public long getItemId(int p) { return p; }
        public View getView(int p, View old, ViewGroup parent) {
            LinearLayout card = new LinearLayout(TvChooserActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(12, 8, 12, 8);
            card.setFocusable(true);
            card.setClickable(true);
            card.setFocusableInTouchMode(false);
            card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            card.setBackgroundResource(R.drawable.chooser_card);
            card.setDefaultFocusHighlightEnabled(false);
            card.setStateListAnimator(null);
            ImageView icon = new ImageView(TvChooserActivity.this);
            icon.setImageResource(R.drawable.folder_icon);
            icon.setScaleType(ImageView.ScaleType.CENTER);
            icon.setClipToOutline(true);
            icon.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 8);
                }
            });
            card.addView(icon, new LinearLayout.LayoutParams(-1, 210));

            TextView t = new TextView(TvChooserActivity.this);
            t.setText(folders.get(p));
            t.setTextColor(Color.WHITE);
            t.setTextSize(14);
            t.setGravity(16);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(t, new LinearLayout.LayoutParams(-1, 78));
            String folder = folders.get(p);
            card.setTag(folder);
            card.setSelected(false);
            card.setOnFocusChangeListener((view, focused) -> view.setSelected(focused));
            card.setOnClickListener(v -> openFolder(card, folder));
            card.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                        && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    openFolder(card, folder);
                    return true;
                }
                return false;
            });
            return card;
        }

        private void openFolder(View card, String folder) {
            Log.d(TAG, "openFolder: " + folder);
            card.setSelected(true);
            card.requestFocus();
            showFolder(folder);
        }
    }

    private void showFolder(String folder) {
        List<Video> selected = new ArrayList<>();
        for (Video v : videos) {
            String name = v.name;
            try (Cursor c = getContentResolver().query(v.uri, new String[]{MediaStore.Video.Media.BUCKET_DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst() && folder.equals(c.getString(0))) selected.add(v);
            } catch (Exception ignored) { }
        }
        videos.clear(); videos.addAll(selected);
        showingFolders = false;
        titleView.setText(folder);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        grid.setAdapter(new Adapter());
        grid.requestFocus();
    }

    private void resetGridAfterStorageChange(String volume) {
        load(volume);
        grid.post(() -> {
            grid.setFocusable(true);
            grid.setFocusableInTouchMode(true);
            grid.requestFocus();
        });
    }

    private final class Adapter extends BaseAdapter {
        public int getCount() { return videos.size(); }
        public Object getItem(int p) { return videos.get(p); }
        public long getItemId(int p) { return p; }
        public View getView(int p, View old, ViewGroup parent) {
            LinearLayout v = new LinearLayout(TvChooserActivity.this);
            v.setOrientation(LinearLayout.VERTICAL);
            v.setPadding(12, 8, 12, 8);
            v.setFocusable(true);
            v.setFocusableInTouchMode(false);
            v.setBackgroundResource(R.drawable.chooser_card);
            v.setDefaultFocusHighlightEnabled(false);
            v.setStateListAnimator(null);
            v.setOnFocusChangeListener((view, focused) -> view.setSelected(focused));
            v.setPadding(12, 8, 12, 10);
            Video video = videos.get(p);
            ImageView preview = new ImageView(TvChooserActivity.this);
            preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int watchedPercent = progress(video.uri);
            preview.setImageResource(watchedPercent >= 95
                    ? R.drawable.video_watched : R.drawable.video_placeholder);
            preview.setPadding(0, 12, 0, 12);
            preview.setClipToOutline(true);
            preview.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 8);
                }
            });
            v.addView(preview, new LinearLayout.LayoutParams(-1, 155));
            TextView t = new TextView(TvChooserActivity.this);
            t.setText(video.name);
            t.setTextColor(Color.WHITE);
            t.setTextSize(14);
            t.setSingleLine(false);
            t.setMaxLines(3);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            v.addView(t, new LinearLayout.LayoutParams(-1, 125));
            ProgressBar progress = new ProgressBar(TvChooserActivity.this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);
            progress.setProgressDrawable(getDrawable(R.drawable.progress_track));
            int percent = watchedPercent;
            progress.setProgress(percent);
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(214, 168, 79)));
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, 10);
            progressParams.setMargins(0, 8, 0, 0);
            v.addView(progress, progressParams);
            v.setTag(video);
            v.setOnClickListener(x -> openVideo(video));
            v.setFocusable(true);
            v.setClickable(true);
            v.setOnFocusChangeListener((view, focused) -> {
                view.setSelected(focused);
            });
            v.setOnKeyListener((view, keyCode, event) -> {
                if ((keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER)
                        && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    openVideo(video);
                    return true;
                }
                return false;
            });
            return v;
        }

        private void openVideo(Video video) {
            Log.d(TAG, "openVideo: " + video.uri);
            setResult(RESULT_OK, new Intent("RESULT", video.uri));
            finish();
        }
    }

    private int progress(Uri uri) {
        long pos = Prefs.savedPosition(this, uri);
        if (pos <= 0) return 0;
        try (Cursor c = getContentResolver().query(uri, new String[]{MediaStore.Video.VideoColumns.DURATION}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                long duration = c.getLong(0);
                return duration > 0 ? Math.min(100, (int) (pos * 100L / duration)) : 0;
            }
        } catch (Exception ignored) { }
        return 0;
    }
}
