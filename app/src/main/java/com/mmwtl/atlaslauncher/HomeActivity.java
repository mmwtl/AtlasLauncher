package com.mmwtl.atlaslauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.io.InputStream;
import java.io.IOException;

public final class HomeActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(5, 17, 34);
    private static final int SURFACE = Color.argb(220, 9, 26, 47);
    private static final int SURFACE_RAISED = Color.argb(224, 27, 47, 73);
    private static final int ACCENT = Color.rgb(46, 150, 246);
    private static final int TEXT = Color.rgb(250, 252, 255);
    private static final int MUTED = Color.rgb(188, 204, 224);
    private static final int HOST_ID = 240925;
    private static final int BIND_WIDGET = 1;
    private static final int CONFIGURE_WIDGET = 2;
    private static final int PICK_WALLPAPER = 3;
    private static final int CLOCK_WIDGET_ID = -2;
    private static final int WIDGET_CELL_DP = 96;
    private static final String PREFS = "home";
    private static final String FAVORITES = "favorites";
    private static final String WIDGETS = "widgets";
    private static final String WIDGET_PADDING_MIGRATED = "widget_padding_migrated";
    private static final String WALLPAPER = "wallpaper";
    private static final String DOCK_VISIBLE = "dock_visible";
    private static final String DOCK_APPS = "dock_apps";
    private static final String DOCK_COMPACT = "dock_compact";
    private static final String CLOCK_FORMAT = "clock_format";
    private static final String CLOCK_WEIGHT = "clock_weight";
    private static final String CLOCK_FONT = "clock_font";
    private static final String CLOCK_DATE = "clock_date";
    private final List<AppEntry> apps = new ArrayList<>();
    private final List<String> favorites = new ArrayList<>();
    private final List<WidgetPlacement> widgets = new ArrayList<>();
    private AppWidgetManager widgetManager;
    private AppWidgetHost widgetHost;
    private LinearLayout favoriteRow;
    private FrameLayout widgetRow;
    private LinearLayout widgetControls;
    private ImageView wallpaperView;
    private LinearLayout favoritePanel;
    private LinearLayout appsTile;
    private boolean editingWidgets;
    private int pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private final Runnable renderWidgets = this::showWidgets;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        widgetManager = AppWidgetManager.getInstance(this);
        widgetHost = new AppWidgetHost(this, HOST_ID);
        loadSavedState();
        buildHome();
    }

    @Override public void onStart() {
        super.onStart();
        widgetHost.startListening();
    }

    @Override public void onResume() {
        super.onResume();
        loadApps();
        showFavorites();
        if (widgetRow.getWidth() > 0 && widgetRow.getHeight() > 0) scheduleShowWidgets();
    }

    @Override public void onStop() {
        widgetHost.stopListening();
        super.onStop();
    }

    @Override public void onBackPressed() {
        if (editingWidgets) setEditingWidgets(false);
        else super.onBackPressed();
    }

    private void buildHome() {
        getWindow().setStatusBarColor(Color.rgb(8, 24, 45));
        getWindow().setNavigationBarColor(BACKGROUND);
        FrameLayout backdrop = new FrameLayout(this);
        wallpaperView = new ImageView(this);
        wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.addView(wallpaperView, new FrameLayout.LayoutParams(-1, -1));
        showWallpaper();
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(45, 2, 10, 24), Color.argb(8, 2, 10, 24), Color.argb(175, 2, 9, 21)}));
        backdrop.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        setContentView(backdrop);
        widgetRow = new FrameLayout(this);
        backdrop.addView(widgetRow, new FrameLayout.LayoutParams(-1, -1));
        widgetRow.setClickable(true);
        widgetRow.setOnLongClickListener(v -> { setEditingWidgets(true); return true; });
        widgetRow.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)
                scheduleShowWidgets();
        });

        Button settings = button("⚙");
        settings.setContentDescription("Настройки и возврат к штатному HOME");
        settings.setTextSize(28);
        settings.setBackground(round(SURFACE, Color.TRANSPARENT, 38));
        settings.setOnClickListener(v -> showSettings());
        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(dp(76), dp(76), Gravity.TOP | Gravity.RIGHT);
        settingsParams.setMargins(0, dp(16), dp(20), 0);
        backdrop.addView(settings, settingsParams);

        widgetControls = new LinearLayout(this);
        widgetControls.setGravity(Gravity.CENTER_VERTICAL);
        widgetControls.setVisibility(View.GONE);
        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(-2, dp(76), Gravity.TOP | Gravity.LEFT);
        controlsParams.setMargins(dp(20), dp(16), 0, 0);
        backdrop.addView(widgetControls, controlsParams);
        Button add = button("＋");
        add.setContentDescription("Добавить виджет");
        add.setTextSize(30);
        add.setBackground(round(SURFACE, Color.TRANSPARENT, 38));
        add.setOnClickListener(v -> chooseWidget());
        widgetControls.addView(add, new LinearLayout.LayoutParams(dp(76), dp(76)));
        Button done = button("✓");
        done.setContentDescription("Завершить редактирование");
        done.setTextSize(28);
        done.setBackground(round(SURFACE, Color.TRANSPARENT, 38));
        done.setOnClickListener(v -> setEditingWidgets(false));
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        editParams.setMargins(dp(8), 0, 0, 0);
        widgetControls.addView(done, editParams);

        favoritePanel = panel();
        favoritePanel.setOrientation(LinearLayout.HORIZONTAL);
        favoritePanel.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams favoritePanelParams = new FrameLayout.LayoutParams(-1, dp(184), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        favoritePanelParams.setMargins(dp(20), 0, dp(20), dp(16));
        backdrop.addView(favoritePanel, favoritePanelParams);
        HorizontalScrollView favoriteScroll = new HorizontalScrollView(this);
        favoriteScroll.setFillViewport(true);
        favoriteScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams favoriteScrollParams = new LinearLayout.LayoutParams(0, -1, 1);
        favoritePanel.addView(favoriteScroll, favoriteScrollParams);
        favoriteRow = new LinearLayout(this);
        favoriteRow.setGravity(Gravity.CENTER_VERTICAL);
        favoriteScroll.addView(favoriteRow);
        appsTile = new LinearLayout(this);
        appsTile.setOrientation(LinearLayout.VERTICAL);
        appsTile.setGravity(Gravity.CENTER);
        TextView appsIcon = label("▦", 52, TEXT, false);
        appsIcon.setGravity(Gravity.CENTER);
        appsIcon.setBackground(round(SURFACE_RAISED, Color.TRANSPARENT, 20));
        appsTile.addView(appsIcon, new LinearLayout.LayoutParams(dp(88), dp(88)));
        TextView appsLabel = label("Приложения", 16, TEXT, false);
        appsLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams appsLabelParams = new LinearLayout.LayoutParams(-1, -2);
        appsLabelParams.topMargin = dp(8);
        appsTile.addView(appsLabel, appsLabelParams);
        appsTile.setContentDescription("Все приложения");
        appsTile.setOnClickListener(v -> showAppDrawer());
        favoritePanel.addView(appsTile, new LinearLayout.LayoutParams(dp(124), dp(140)));
        updateDock();
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(18));
        panel.setBackground(round(Color.argb(223, 6, 19, 37), Color.argb(120, 110, 152, 199), 28));
        return panel;
    }

    private Button button(String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(TEXT);
        button.setBackground(round(SURFACE_RAISED, Color.TRANSPARENT, 16));
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTypeface(null, Typeface.BOLD);
        button.setMinimumHeight(dp(76));
        return button;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private GradientDrawable round(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(intent, 0);
        apps.clear();
        for (ResolveInfo info : found) {
            if (info.activityInfo == null || !info.activityInfo.exported) continue;
            ComponentName component = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
            if (component.getPackageName().equals(getPackageName())) continue;
            apps.add(new AppEntry(component, info.loadLabel(pm).toString(), info.loadIcon(pm)));
        }
        Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(apps, (a, b) -> collator.compare(a.label, b.label));
        boolean changed = favorites.removeIf(name -> findApp(name) == null);
        if (changed) saveFavorites();
    }

    private AppEntry findApp(String flattened) {
        for (AppEntry app : apps) if (app.component.flattenToString().equals(flattened)) return app;
        return null;
    }

    private void showFavorites() {
        favoriteRow.removeAllViews();
        if (favorites.isEmpty()) {
            TextView empty = label("Удерживайте приложение в каталоге, чтобы закрепить его здесь", 15, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(14), dp(14), dp(14), dp(14));
            favoriteRow.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        for (String name : favorites) {
            AppEntry app = findApp(name);
            if (app == null) continue;
            View item = appTile(app, false);
            item.setOnClickListener(v -> launch(app));
            item.setOnLongClickListener(v -> { favoriteActions(app); return true; });
            LinearLayout.LayoutParams itemParams = favorites.size() <= 4
                    ? new LinearLayout.LayoutParams(0, dp(140), 1)
                    : new LinearLayout.LayoutParams(dp(124), dp(140));
            favoriteRow.addView(item, itemParams);
        }
    }

    private View appTile(AppEntry app, boolean card) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(6), dp(6), dp(6), dp(6));
        if (card) tile.setBackground(round(SURFACE_RAISED, Color.TRANSPARENT, 18));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        if (card) {
            tile.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        } else {
            FrameLayout iconSurface = new FrameLayout(this);
            iconSurface.setBackground(round(SURFACE_RAISED, Color.TRANSPARENT, 20));
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(68), dp(68), Gravity.CENTER);
            iconSurface.addView(icon, iconParams);
            tile.addView(iconSurface, new LinearLayout.LayoutParams(dp(88), dp(88)));
        }
        TextView label = label(app.label, 16, TEXT, false);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.setMargins(0, dp(card ? 6 : 8), 0, 0);
        tile.addView(label, labelParams);
        return tile;
    }

    private void showAppDrawer() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(20), dp(22), dp(20));
        content.setBackground(round(Color.rgb(9, 25, 46), Color.argb(130, 96, 143, 192), 26));
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(titleRow, new LinearLayout.LayoutParams(-1, dp(76)));
        LinearLayout titleText = new LinearLayout(this);
        titleText.setOrientation(LinearLayout.VERTICAL);
        titleText.addView(label("Приложения", 22, TEXT, true));
        titleText.addView(label("Удерживайте значок, чтобы закрепить", 13, MUTED, false));
        titleRow.addView(titleText, new LinearLayout.LayoutParams(0, -2, 1));
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Button close = button("×");
        close.setContentDescription("Закрыть список приложений");
        close.setTextSize(22);
        close.setOnClickListener(v -> dialog.dismiss());
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(76), dp(76)));
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Поиск приложений");
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setTextSize(16);
        search.setPadding(dp(18), 0, dp(18), 0);
        search.setBackground(round(SURFACE_RAISED, Color.TRANSPARENT, 16));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(76));
        searchParams.setMargins(0, dp(8), 0, dp(18));
        content.addView(search, searchParams);
        GridView grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(135));
        grid.setHorizontalSpacing(dp(10));
        grid.setVerticalSpacing(dp(10));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalScrollBarEnabled(false);
        content.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        List<AppEntry> visible = new ArrayList<>(apps);
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return visible.size(); }
            @Override public Object getItem(int position) { return visible.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View old, ViewGroup parent) {
                View tile = appTile(visible.get(position), true);
                tile.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, dp(112)));
                return tile;
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            AppEntry app = visible.get(position);
            dialog.dismiss();
            launch(app);
        });
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            AppEntry app = visible.get(position);
            String key = app.component.flattenToString();
            if (favorites.contains(key)) favorites.remove(key);
            else favorites.add(key);
            saveFavorites();
            showFavorites();
            Toast.makeText(this, favorites.contains(key) ? "Добавлено в избранное" : "Удалено из избранного", Toast.LENGTH_SHORT).show();
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().toLowerCase(Locale.getDefault());
                visible.clear();
                for (AppEntry app : apps) if (app.label.toLowerCase(Locale.getDefault()).contains(query)) visible.add(app);
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        dialog.setContentView(content);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(1100)),
                    Math.min(getResources().getDisplayMetrics().heightPixels - dp(32), dp(620)));
        }
    }

    private void favoriteActions(AppEntry app) {
        String key = app.component.flattenToString();
        int index = favorites.indexOf(key);
        String[] actions = {"Передвинуть влево", "Передвинуть вправо", "Убрать из избранного"};
        new AlertDialog.Builder(this).setTitle(app.label).setItems(actions, (dialog, which) -> {
            if (which == 2) favorites.remove(index);
            else {
                int target = index + (which == 0 ? -1 : 1);
                if (target < 0 || target >= favorites.size()) return;
                Collections.swap(favorites, index, target);
            }
            saveFavorites();
            showFavorites();
        }).show();
    }

    private void launch(AppEntry app) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(app.component);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Не удалось открыть " + app.label, Toast.LENGTH_SHORT).show();
            loadApps();
            showFavorites();
        }
    }

    private void showSettings() {
        String[] choices = {"Редактировать рабочий стол", "Сменить фон", "Настроить нижний док",
                "Приложения", "Настройки HOME в Android", "Открыть штатный Launcher3", "Настройки устройства"};
        new AlertDialog.Builder(this).setTitle("AtlasLauncher").setItems(choices, (d, which) -> {
            if (which == 0) {
                setEditingWidgets(true);
            } else if (which == 1) {
                showWallpaperSettings();
            } else if (which == 2) {
                showDockSettings();
            } else if (which == 3) {
                showAppDrawer();
            } else if (which == 4) {
                try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
                catch (ActivityNotFoundException e) { Toast.makeText(this, "Настройки HOME недоступны", Toast.LENGTH_SHORT).show(); }
            } else if (which == 5) {
                Intent intent = getPackageManager().getLaunchIntentForPackage("com.android.launcher3");
                if (intent != null) startActivity(intent);
                else Toast.makeText(this, "Штатный Launcher3 недоступен", Toast.LENGTH_SHORT).show();
            } else {
                try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                catch (ActivityNotFoundException e) { Toast.makeText(this, "Настройки недоступны", Toast.LENGTH_SHORT).show(); }
            }
        }).show();
    }

    private void setEditingWidgets(boolean editing) {
        editingWidgets = editing;
        widgetControls.setVisibility(editing ? View.VISIBLE : View.GONE);
        updateDock();
        showWidgets();
    }

    private void showWallpaperSettings() {
        String[] choices = {"Выбрать изображение", "Вернуть стандартный фон"};
        new AlertDialog.Builder(this).setTitle("Фон рабочего стола").setItems(choices, (dialog, which) -> {
            if (which == 0) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                try { startActivityForResult(intent, PICK_WALLPAPER); }
                catch (ActivityNotFoundException e) { Toast.makeText(this, "Выбор изображения недоступен", Toast.LENGTH_SHORT).show(); }
            } else {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(WALLPAPER).apply();
                showWallpaper();
            }
        }).show();
    }

    private void showWallpaper() {
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(WALLPAPER, null);
        if (saved == null) {
            wallpaperView.setImageResource(R.drawable.coastal_twilight);
            return;
        }
        try {
            Uri uri = Uri.parse(saved);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(stream, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(stream, null, options);
            }
            if (bitmap == null) throw new IOException("Изображение не удалось прочитать");
            wallpaperView.setImageBitmap(bitmap);
        } catch (IOException | SecurityException e) {
            wallpaperView.setImageResource(R.drawable.coastal_twilight);
            Toast.makeText(this, "Не удалось загрузить фон", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDockSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String[] choices = {"Показывать док", "Кнопка приложений", "Компактный док"};
        boolean[] selected = {prefs.getBoolean(DOCK_VISIBLE, true), prefs.getBoolean(DOCK_APPS, true),
                prefs.getBoolean(DOCK_COMPACT, false)};
        new AlertDialog.Builder(this).setTitle("Нижний док")
                .setMultiChoiceItems(choices, selected, (dialog, which, checked) -> {
                    selected[which] = checked;
                    prefs.edit().putBoolean(which == 0 ? DOCK_VISIBLE : which == 1 ? DOCK_APPS : DOCK_COMPACT, checked).apply();
                    updateDock();
                })
                .setNeutralButton("Приложения дока", (dialog, which) -> showAppDrawer())
                .setPositiveButton("Готово", null).show();
    }

    private void updateDock() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean compact = prefs.getBoolean(DOCK_COMPACT, false);
        favoritePanel.setVisibility(!editingWidgets && prefs.getBoolean(DOCK_VISIBLE, true) ? View.VISIBLE : View.GONE);
        favoritePanel.setPadding(dp(16), dp(compact ? 4 : 14), dp(16), dp(compact ? 4 : 14));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) favoritePanel.getLayoutParams();
        params.height = dp(compact ? 156 : 184);
        favoritePanel.setLayoutParams(params);
        appsTile.setVisibility(prefs.getBoolean(DOCK_APPS, true) ? View.VISIBLE : View.GONE);
    }

    private void chooseWidget() {
        List<AppWidgetProviderInfo> providers = widgetManager.getInstalledProviders();
        Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(providers, (a, b) -> collator.compare(String.valueOf(a.loadLabel(getPackageManager())), String.valueOf(b.loadLabel(getPackageManager()))));
        String[] names = new String[providers.size() + 1];
        names[0] = "Часы AtlasLauncher";
        for (int i = 0; i < providers.size(); i++) names[i + 1] = String.valueOf(providers.get(i).loadLabel(getPackageManager()));
        new AlertDialog.Builder(this).setTitle("Добавить виджет").setItems(names, (dialog, which) -> {
            if (which == 0) { addClockWidget(); return; }
            AppWidgetProviderInfo provider = providers.get(which - 1);
            pendingWidgetId = widgetHost.allocateAppWidgetId();
            if (widgetManager.bindAppWidgetIdIfAllowed(pendingWidgetId, provider.provider)) {
                configureWidget(provider);
            } else {
                Intent bind = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId);
                bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider);
                try { startActivityForResult(bind, BIND_WIDGET); }
                catch (ActivityNotFoundException | SecurityException e) { cancelPendingWidget(); Toast.makeText(this, "Привязка виджета недоступна", Toast.LENGTH_SHORT).show(); }
            }
        }).show();
    }

    private void configureWidget(AppWidgetProviderInfo provider) {
        if (provider.configure == null) {
            finishAddingWidget();
            return;
        }
        Intent configure = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
        configure.setComponent(provider.configure);
        configure.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId);
        try { startActivityForResult(configure, CONFIGURE_WIDGET); }
        catch (ActivityNotFoundException | SecurityException e) { cancelPendingWidget(); Toast.makeText(this, "Настройка виджета недоступна", Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == BIND_WIDGET) {
            if (result != RESULT_OK) { cancelPendingWidget(); return; }
            int id = data == null ? pendingWidgetId : data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId);
            pendingWidgetId = id;
            AppWidgetProviderInfo provider = widgetManager.getAppWidgetInfo(id);
            if (provider != null) configureWidget(provider);
            else cancelPendingWidget();
        } else if (request == CONFIGURE_WIDGET) {
            if (result == RESULT_OK) finishAddingWidget();
            else cancelPendingWidget();
        } else if (request == PICK_WALLPAPER && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(WALLPAPER, uri.toString()).apply();
                showWallpaper();
            } catch (SecurityException e) {
                Toast.makeText(this, "Нет доступа к изображению", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void addClockWidget() {
        for (WidgetPlacement placement : widgets) {
            if (placement.id == CLOCK_WIDGET_ID) {
                Toast.makeText(this, "Часы уже добавлены", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        WidgetGrid grid = widgetGrid();
        if (grid == null) return;
        int columns = Math.min(4, grid.columns);
        int rows = Math.min(2, grid.rows);
        Point slot = findGridSlot(grid, 0, 0, columns, rows, widgets);
        if (slot == null) {
            Toast.makeText(this, "Недостаточно места для часов", Toast.LENGTH_SHORT).show();
            return;
        }
        WidgetPlacement placement = new WidgetPlacement(CLOCK_WIDGET_ID, 0, 0, 0, 0);
        setGridPlacement(placement, grid, slot, columns, rows);
        widgets.add(placement);
        saveWidgets();
        showWidgets();
    }

    private void finishAddingWidget() {
        if (pendingWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return;
        AppWidgetProviderInfo info = widgetManager.getAppWidgetInfo(pendingWidgetId);
        if (info == null) { cancelPendingWidget(); return; }
        WidgetGrid grid = widgetGrid();
        if (grid == null) { widgetRow.post(this::finishAddingWidget); return; }
        Point padding = widgetPaddingDp(info);
        int widgetWidth = pxToDp(Math.max(info.minWidth, info.minResizeWidth)) + padding.x;
        int widgetHeight = Math.max(96, pxToDp(Math.max(info.minHeight, info.minResizeHeight))) + padding.y;
        WidgetPlacement placement = new WidgetPlacement(pendingWidgetId, 0, 0, widgetWidth, widgetHeight);
        int columns = grid.span(widgetWidth, grid.cellWidth, grid.columns);
        int rows = grid.span(widgetHeight, grid.cellHeight, grid.rows);
        Point slot = findGridSlot(grid, 0, 0, columns, rows, widgets);
        if (slot == null) {
            cancelPendingWidget();
            Toast.makeText(this, "Недостаточно места для виджета", Toast.LENGTH_SHORT).show();
            return;
        }
        setGridPlacement(placement, grid, slot, columns, rows);
        widgets.add(placement);
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        saveWidgets();
        showWidgets();
    }

    private void cancelPendingWidget() {
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) widgetHost.deleteAppWidgetId(pendingWidgetId);
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    }

    private void showWidgets() {
        widgetRow.removeAllViews();
        WidgetGrid grid = widgetGrid();
        if (grid == null) return;
        boolean changed = false;
        List<WidgetPlacement> occupied = new ArrayList<>();
        Iterator<WidgetPlacement> iterator = widgets.iterator();
        while (iterator.hasNext()) {
            WidgetPlacement placement = iterator.next();
            boolean clockWidget = placement.id == CLOCK_WIDGET_ID;
            AppWidgetProviderInfo info = clockWidget ? null : widgetManager.getAppWidgetInfo(placement.id);
            if (!clockWidget && info == null) {
                widgetHost.deleteAppWidgetId(placement.id);
                iterator.remove();
                changed = true;
                continue;
            }
            int availableWidth = grid.width;
            int availableHeight = grid.height;
            Point padding = clockWidget ? new Point(0, 0) : widgetPaddingDp(info);
            int oldX = placement.x, oldY = placement.y;
            int oldWidth = placement.width, oldHeight = placement.height;
            if (placement.width == 0 || placement.height == 0) {
                placement.width = Math.min(availableWidth, clockWidget ? 4 * WIDGET_CELL_DP
                        : pxToDp(Math.max(info.minWidth, info.minResizeWidth)) + padding.x);
                placement.height = Math.min(availableHeight, clockWidget ? 2 * WIDGET_CELL_DP
                        : Math.max(96, pxToDp(Math.max(info.minHeight, info.minResizeHeight))) + padding.y);
                changed = true;
            }
            if (!clockWidget && placement.height < 96 + padding.y && availableHeight >= 96 + padding.y) {
                placement.height = 96 + padding.y;
                changed = true;
            }
            placement.width = Math.min(placement.width, availableWidth);
            placement.height = Math.min(placement.height, availableHeight);
            placement.x = Math.max(0, Math.min(placement.x, availableWidth - placement.width));
            placement.y = Math.max(0, Math.min(placement.y, availableHeight - placement.height));
            int fallbackX = placement.x, fallbackY = placement.y;
            int fallbackWidth = placement.width, fallbackHeight = placement.height;
            int minColumns = clockWidget ? 1 : grid.span(Math.max(56, pxToDp(info.minResizeWidth)) + padding.x, grid.cellWidth, grid.columns);
            int minRows = clockWidget ? 1 : grid.span(Math.max(96, pxToDp(info.minResizeHeight)) + padding.y, grid.cellHeight, grid.rows);
            int columns = Math.max(minColumns, grid.span(placement.width, grid.cellWidth, grid.columns));
            int rows = Math.max(minRows, grid.span(placement.height, grid.cellHeight, grid.rows));
            Point slot = null;
            for (int rowSpan = rows; rowSpan >= minRows && slot == null; rowSpan--) {
                for (int columnSpan = columns; columnSpan >= minColumns && slot == null; columnSpan--) {
                    slot = findGridSlot(grid, placement.x, placement.y, columnSpan, rowSpan, occupied);
                    if (slot != null) setGridPlacement(placement, grid, slot, columnSpan, rowSpan);
                }
            }
            if (slot == null) {
                // Keep a saved widget visible if an older layout cannot fit the current screen.
                placement.x = fallbackX;
                placement.y = fallbackY;
                placement.width = fallbackWidth;
                placement.height = fallbackHeight;
            }
            occupied.add(placement);
            if (placement.x != oldX || placement.y != oldY || placement.width != oldWidth || placement.height != oldHeight)
                changed = true;
            FrameLayout container = new FrameLayout(this);
            View hostView;
            if (clockWidget) {
                hostView = createClockWidget(placement);
            } else {
                AppWidgetHostView widgetView = widgetHost.createView(this, placement.id, info);
                widgetView.setAppWidget(placement.id, info);
                hostView = widgetView;
            }
            hostView.setOnLongClickListener(v -> {
                setEditingWidgets(true);
                return true;
            });
            container.addView(hostView, new FrameLayout.LayoutParams(-1, -1));
            if (editingWidgets) addWidgetEditControls(container, hostView, placement, info);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(placement.width), dp(placement.height));
            params.leftMargin = dp(placement.x);
            params.topMargin = dp(placement.y);
            widgetRow.addView(container, params);
            if (hostView instanceof AppWidgetHostView) updateWidgetSize((AppWidgetHostView) hostView, placement);
        }
        if (changed) saveWidgets();
        if (widgets.isEmpty() && editingWidgets) {
            TextView empty = label("＋  Добавить виджет", 16, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(round(SURFACE, Color.TRANSPARENT, 18));
            empty.setOnClickListener(v -> chooseWidget());
            widgetRow.addView(empty, new FrameLayout.LayoutParams(dp(220), dp(64), Gravity.CENTER));
        }
    }

    private View createClockWidget(WidgetPlacement placement) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setGravity(Gravity.CENTER);
        LinearLayout timeLine = new LinearLayout(this);
        timeLine.setGravity(Gravity.CENTER);
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("h:mm");
        clock.setTextColor(TEXT);
        clock.setGravity(Gravity.CENTER);
        timeLine.addView(clock);
        TextClock period = new TextClock(this);
        period.setFormat24Hour("a");
        period.setFormat12Hour("a");
        period.setTextColor(MUTED);
        LinearLayout.LayoutParams periodParams = new LinearLayout.LayoutParams(-2, -2);
        periodParams.leftMargin = dp(6);
        timeLine.addView(period, periodParams);
        group.addView(timeLine, new LinearLayout.LayoutParams(-1, -2));
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEE, d MMM, yyyy");
        date.setFormat12Hour("EEE, d MMM, yyyy");
        date.setTextSize(18);
        date.setTextColor(MUTED);
        date.setGravity(Gravity.CENTER);
        group.addView(date, new LinearLayout.LayoutParams(-1, -2));
        updateClockWidget(group, placement);
        return group;
    }

    private void updateClockWidget(View hostView, WidgetPlacement placement) {
        LinearLayout group = (LinearLayout) hostView;
        LinearLayout timeLine = (LinearLayout) group.getChildAt(0);
        boolean showDate = placement.height >= 2 * WIDGET_CELL_DP;
        int clockSize = Math.max(28, Math.min(placement.width / 5,
                Math.round(placement.height / (showDate ? 2.2f : 1.3f))));
        ((TextClock) timeLine.getChildAt(0)).setTextSize(clockSize);
        if (timeLine.getChildCount() > 1)
            ((TextClock) timeLine.getChildAt(1)).setTextSize(Math.max(16, clockSize / 4));
        TextClock date = (TextClock) group.getChildAt(1);
        date.setTextSize(Math.max(18, clockSize / 5));
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int format = prefs.getInt(CLOCK_FORMAT, 0);
        TextClock clock = (TextClock) timeLine.getChildAt(0);
        clock.setFormat24Hour(format == 2 ? "h:mm" : "HH:mm");
        clock.setFormat12Hour(format == 1 ? "HH:mm" : "h:mm");
        if (timeLine.getChildCount() > 1) {
            boolean periodVisible = format == 2 || (format == 0 && !android.text.format.DateFormat.is24HourFormat(this));
            timeLine.getChildAt(1).setVisibility(periodVisible ? View.VISIBLE : View.GONE);
        }
        String[] fonts = {"sans-serif", "serif", "monospace"};
        int font = Math.max(0, Math.min(2, prefs.getInt(CLOCK_FONT, 0)));
        int weight = Math.max(0, Math.min(2, prefs.getInt(CLOCK_WEIGHT, 0)));
        Typeface face = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? Typeface.create(Typeface.create(fonts[font], Typeface.NORMAL), weight == 0 ? 300 : weight == 1 ? 400 : 700, false)
                : Typeface.create(weight == 0 && font == 0 ? "sans-serif-light" : fonts[font], weight == 2 ? Typeface.BOLD : Typeface.NORMAL);
        clock.setTypeface(face);
        ((TextClock) timeLine.getChildAt(1)).setTypeface(face);
        date.setTypeface(face);
        date.setVisibility(showDate && prefs.getBoolean(CLOCK_DATE, true) ? View.VISIBLE : View.GONE);
    }

    private void showClockSettings(View hostView, WidgetPlacement placement) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String[] formats = {"Как в системе", "24 часа", "12 часов"};
        String[] weights = {"Тонкий", "Обычный", "Жирный"};
        String[] fonts = {"Без засечек", "С засечками", "Моноширинный"};
        String[] choices = {
                "Формат времени: " + formats[prefs.getInt(CLOCK_FORMAT, 0)],
                "Показывать дату: " + (prefs.getBoolean(CLOCK_DATE, true) ? "да" : "нет"),
                "Начертание: " + weights[prefs.getInt(CLOCK_WEIGHT, 0)],
                "Шрифт: " + fonts[prefs.getInt(CLOCK_FONT, 0)]
        };
        new AlertDialog.Builder(this).setTitle("Настройки часов").setItems(choices, (dialog, which) -> {
            if (which == 1) {
                prefs.edit().putBoolean(CLOCK_DATE, !prefs.getBoolean(CLOCK_DATE, true)).apply();
                updateClockWidget(hostView, placement);
            } else {
                String key = which == 0 ? CLOCK_FORMAT : which == 2 ? CLOCK_WEIGHT : CLOCK_FONT;
                String[] options = which == 0 ? formats : which == 2 ? weights : fonts;
                new AlertDialog.Builder(this).setTitle(which == 0 ? "Формат времени" : which == 2 ? "Начертание" : "Шрифт")
                        .setSingleChoiceItems(options, prefs.getInt(key, 0), (choiceDialog, selected) -> {
                            prefs.edit().putInt(key, selected).apply();
                            updateClockWidget(hostView, placement);
                            choiceDialog.dismiss();
                        }).show();
            }
        }).show();
    }

    private void scheduleShowWidgets() {
        widgetRow.removeCallbacks(renderWidgets);
        widgetRow.postDelayed(renderWidgets, 150);
    }

    private WidgetGrid widgetGrid() {
        int width = pxToDp(widgetRow.getWidth());
        int height = pxToDp(widgetRow.getHeight());
        return width > 0 && height > 0 ? new WidgetGrid(width, height) : null;
    }

    private Point findGridSlot(WidgetGrid grid, int wantedX, int wantedY, int columns, int rows,
                               List<WidgetPlacement> occupied) {
        if (columns > grid.columns || rows > grid.rows) return null;
        int wantedColumn = Math.max(0, Math.min(grid.columns - columns, Math.round((float) wantedX / grid.cellWidth)));
        int wantedRow = Math.max(0, Math.min(grid.rows - rows, Math.round((float) wantedY / grid.cellHeight)));
        Point best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int row = 0; row <= grid.rows - rows; row++) {
            for (int column = 0; column <= grid.columns - columns; column++) {
                if (!gridSlotFree(grid, column, row, columns, rows, occupied)) continue;
                int distance = Math.abs(column - wantedColumn) + Math.abs(row - wantedRow);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new Point(column, row);
                }
            }
        }
        return best;
    }

    private boolean gridSlotFree(WidgetGrid grid, int column, int row, int columns, int rows,
                                 List<WidgetPlacement> occupied) {
        int left = column * grid.cellWidth;
        int top = row * grid.cellHeight;
        int right = left + columns * grid.cellWidth;
        int bottom = top + rows * grid.cellHeight;
        for (WidgetPlacement other : occupied) {
            if (left < other.x + other.width && right > other.x &&
                    top < other.y + other.height && bottom > other.y) return false;
        }
        return true;
    }

    private void setGridPlacement(WidgetPlacement placement, WidgetGrid grid, Point slot, int columns, int rows) {
        placement.x = slot.x * grid.cellWidth;
        placement.y = slot.y * grid.cellHeight;
        placement.width = columns * grid.cellWidth;
        placement.height = rows * grid.cellHeight;
    }

    private Point widgetPaddingDp(AppWidgetProviderInfo info) {
        Rect padding = AppWidgetHostView.getDefaultPaddingForWidget(this, info.provider, null);
        return new Point(pxToDp(padding.left + padding.right), pxToDp(padding.top + padding.bottom));
    }

    @SuppressWarnings("deprecation")
    private void updateWidgetSize(AppWidgetHostView hostView, WidgetPlacement placement) {
        hostView.updateAppWidgetSize(null, placement.width, placement.height, placement.width, placement.height);
    }

    private void addWidgetEditControls(FrameLayout container, View hostView,
                                       WidgetPlacement placement, AppWidgetProviderInfo info) {
        container.setForeground(round(Color.TRANSPARENT, ACCENT, 12));
        View dragSurface = new View(this);
        String title = info == null ? "часы" : String.valueOf(info.loadLabel(getPackageManager()));
        dragSurface.setContentDescription("Перетащить " + title);
        dragSurface.setOnTouchListener(widgetTouch(container, hostView, placement, info, false));
        container.addView(dragSurface, new FrameLayout.LayoutParams(-1, -1));
        if (info == null) {
            Button settings = button("⚙");
            settings.setContentDescription("Настроить часы");
            settings.setOnClickListener(v -> showClockSettings(hostView, placement));
            container.addView(settings, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.LEFT));
        }
        Button delete = button("×");
        delete.setContentDescription("Удалить " + title);
        delete.setOnClickListener(v -> {
            widgets.remove(placement);
            if (info != null) widgetHost.deleteAppWidgetId(placement.id);
            saveWidgets();
            showWidgets();
        });
        container.addView(delete, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.RIGHT));
        if (info == null || info.resizeMode != AppWidgetProviderInfo.RESIZE_NONE) {
            Button resize = button("↘");
            resize.setContentDescription("Изменить размер " + title);
            resize.setOnTouchListener(widgetTouch(container, hostView, placement, info, true));
            container.addView(resize, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM | Gravity.RIGHT));
        }
    }

    private View.OnTouchListener widgetTouch(FrameLayout container, View hostView,
                                             WidgetPlacement placement,
                                             AppWidgetProviderInfo info, boolean resizing) {
        Point padding = info == null ? new Point(0, 0) : widgetPaddingDp(info);
        return new View.OnTouchListener() {
            float startX;
            float startY;
            int originalX;
            int originalY;
            int originalWidth;
            int originalHeight;

            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    startX = event.getRawX();
                    startY = event.getRawY();
                    originalX = placement.x;
                    originalY = placement.y;
                    originalWidth = placement.width;
                    originalHeight = placement.height;
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    int dx = pxToDp(event.getRawX() - startX);
                    int dy = pxToDp(event.getRawY() - startY);
                    int areaWidth = pxToDp(widgetRow.getWidth());
                    int areaHeight = pxToDp(widgetRow.getHeight());
                    if (resizing) {
                        if (info == null || (info.resizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0)
                            placement.width = Math.min(areaWidth - placement.x, Math.max(info == null ? WIDGET_CELL_DP : Math.max(pxToDp(info.minResizeWidth), 56) + padding.x, originalWidth + dx));
                        if (info == null || (info.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0)
                            placement.height = Math.min(areaHeight - placement.y, Math.max(info == null ? WIDGET_CELL_DP : Math.max(pxToDp(info.minResizeHeight), 96) + padding.y, originalHeight + dy));
                    } else {
                        placement.x = Math.max(0, Math.min(areaWidth - placement.width, originalX + dx));
                        placement.y = Math.max(0, Math.min(areaHeight - placement.height, originalY + dy));
                    }
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
                    params.width = dp(placement.width);
                    params.height = dp(placement.height);
                    params.leftMargin = dp(placement.x);
                    params.topMargin = dp(placement.y);
                    container.setLayoutParams(params);
                    if (resizing && info == null) updateClockWidget(hostView, placement);
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    WidgetGrid grid = widgetGrid();
                    if (event.getActionMasked() == MotionEvent.ACTION_CANCEL || grid == null) {
                        placement.x = originalX;
                        placement.y = originalY;
                        placement.width = originalWidth;
                        placement.height = originalHeight;
                    } else {
                        List<WidgetPlacement> occupied = new ArrayList<>(widgets);
                        occupied.remove(placement);
                        int columns = resizing ? grid.nearestSpan(placement.width, grid.cellWidth, grid.columns)
                                : grid.span(placement.width, grid.cellWidth, grid.columns);
                        int rows = resizing ? grid.nearestSpan(placement.height, grid.cellHeight, grid.rows)
                                : grid.span(placement.height, grid.cellHeight, grid.rows);
                        Point slot;
                        if (resizing) {
                            columns = Math.max(columns, grid.span(info == null ? WIDGET_CELL_DP : Math.max(56, pxToDp(info.minResizeWidth)) + padding.x,
                                    grid.cellWidth, grid.columns));
                            rows = Math.max(rows, grid.span(info == null ? WIDGET_CELL_DP : Math.max(96, pxToDp(info.minResizeHeight)) + padding.y,
                                    grid.cellHeight, grid.rows));
                            int column = originalX / grid.cellWidth;
                            int row = originalY / grid.cellHeight;
                            slot = column + columns <= grid.columns && row + rows <= grid.rows &&
                                    gridSlotFree(grid, column, row, columns, rows, occupied)
                                    ? new Point(column, row) : null;
                        } else {
                            slot = findGridSlot(grid, placement.x, placement.y, columns, rows, occupied);
                        }
                        if (slot == null) {
                            placement.x = originalX;
                            placement.y = originalY;
                            placement.width = originalWidth;
                            placement.height = originalHeight;
                        } else {
                            setGridPlacement(placement, grid, slot, columns, rows);
                            saveWidgets();
                        }
                    }
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
                    params.width = dp(placement.width);
                    params.height = dp(placement.height);
                    params.leftMargin = dp(placement.x);
                    params.topMargin = dp(placement.y);
                    container.setLayoutParams(params);
                    if (resizing) {
                        if (hostView instanceof AppWidgetHostView) updateWidgetSize((AppWidgetHostView) hostView, placement);
                        else updateClockWidget(hostView, placement);
                    }
                    return true;
                }
                return true;
            }
        };
    }

    private void loadSavedState() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        try {
            JSONArray savedFavorites = new JSONArray(prefs.getString(FAVORITES, "[]"));
            for (int i = 0; i < savedFavorites.length(); i++) favorites.add(savedFavorites.getString(i));
            JSONArray savedWidgets = new JSONArray(prefs.getString(WIDGETS, "[]"));
            for (int i = 0; i < savedWidgets.length(); i++) {
                Object saved = savedWidgets.get(i);
                if (saved instanceof JSONObject) {
                    JSONObject item = (JSONObject) saved;
                    widgets.add(new WidgetPlacement(item.getInt("id"), item.getInt("x"), item.getInt("y"),
                            item.getInt("width"), item.getInt("height")));
                } else {
                    int id = savedWidgets.getInt(i);
                    widgets.add(new WidgetPlacement(id, 0, i * 210, 0, 0));
                }
            }
            if (!prefs.getBoolean(WIDGET_PADDING_MIGRATED, false)) {
                for (WidgetPlacement widget : widgets) {
                    if (widget.width == 0 || widget.height == 0) continue;
                    AppWidgetProviderInfo info = widgetManager.getAppWidgetInfo(widget.id);
                    if (info == null) continue;
                    Point padding = widgetPaddingDp(info);
                    widget.width += padding.x;
                    widget.height += padding.y;
                }
                saveWidgets();
            }
        } catch (JSONException e) {
            favorites.clear();
            widgets.clear();
        }
    }

    private void saveFavorites() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(FAVORITES, new JSONArray(favorites).toString()).apply();
    }

    private void saveWidgets() {
        JSONArray saved = new JSONArray();
        for (WidgetPlacement widget : widgets) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", widget.id);
                item.put("x", widget.x);
                item.put("y", widget.y);
                item.put("width", widget.width);
                item.put("height", widget.height);
                saved.put(item);
            } catch (JSONException e) { throw new IllegalStateException(e); }
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(WIDGETS, saved.toString())
                .putBoolean(WIDGET_PADDING_MIGRATED, true)
                .apply();
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private int pxToDp(float value) { return Math.round(value / getResources().getDisplayMetrics().density); }

    private static final class WidgetGrid {
        final int width;
        final int height;
        final int columns;
        final int rows;
        final int cellWidth;
        final int cellHeight;

        WidgetGrid(int width, int height) {
            this.width = width;
            this.height = height;
            columns = Math.max(1, width / WIDGET_CELL_DP);
            rows = Math.max(1, height / WIDGET_CELL_DP);
            cellWidth = Math.min(width, WIDGET_CELL_DP);
            cellHeight = Math.min(height, WIDGET_CELL_DP);
        }

        int span(int size, int cell, int count) {
            return Math.max(1, Math.min(count, (int) Math.ceil((double) size / cell)));
        }

        int nearestSpan(int size, int cell, int count) {
            return Math.max(1, Math.min(count, Math.round((float) size / cell)));
        }
    }

    private static final class WidgetPlacement {
        final int id;
        int x;
        int y;
        int width;
        int height;

        WidgetPlacement(int id, int x, int y, int width, int height) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private static final class AppEntry {
        final ComponentName component;
        final String label;
        final Drawable icon;
        AppEntry(ComponentName component, String label, Drawable icon) {
            this.component = component;
            this.label = label;
            this.icon = icon;
        }
    }
}
