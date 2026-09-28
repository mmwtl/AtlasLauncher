package com.mmwtl.atlaslauncher;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.accessibility.AccessibilityManager;
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
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Switch;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.io.InputStream;
import java.io.IOException;

public final class HomeActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(5, 17, 34);
    private static final int SURFACE = Color.argb(220, 9, 26, 47);
    private static final int SURFACE_RAISED = Color.argb(224, 27, 47, 73);
    private static final int ACCENT = Color.rgb(46, 150, 246);
    private static final int TEXT = Color.rgb(250, 252, 255);
    private static final int MUTED = Color.rgb(188, 204, 224);
    private static final int NEUTRAL_SURFACE = Color.rgb(35, 37, 40);
    private static final int NEUTRAL_RAISED = Color.rgb(64, 67, 71);
    private static final int NEUTRAL_TEXT = Color.rgb(241, 242, 244);
    private static final int NEUTRAL_MUTED = Color.rgb(196, 199, 202);
    private static final int HOST_ID = 240925;
    private static final int BIND_WIDGET = 1;
    private static final int CONFIGURE_WIDGET = 2;
    private static final int PICK_WALLPAPER = 3;
    private static final int RECONFIGURE_WIDGET = 4;
    private static final int CLOCK_WIDGET_ID = -2;
    private static final int DOCK_WIDGET_ID = -3;
    private static final int WIDGET_CELL_DP = 96;
    // The OneOS climate panel overlays the bottom of HOME; physical pixels, not dp.
    private static final int CLIMATE_PANEL_PX = 150;
    private static final int MAX_ICON_DP = 160;
    private static final int CATALOG_ICON_DP = 96;
    private static final int WIDGET_PREVIEW_DP = 150;
    static final String PREFS = "home";
    private static final String FAVORITES = "favorites";
    private static final String WIDGETS = "widgets";
    private static final String WIDGET_PADDING_MIGRATED = "widget_padding_migrated";
    private static final String WALLPAPER = "wallpaper";
    private static final String DOCK_VISIBLE = "dock_visible";
    private static final String DOCK_WIDGET_MIGRATED = "dock_widget_migrated";
    private static final String DOCK_APPS = "dock_apps";
    private static final String DOCK_COMPACT = "dock_compact";
    private static final String DRAWER_ICON_SIZE = "drawer_icon_size";
    private static final String DOCK_LABELS = "drawer_labels";
    private static final String DRAWER_ACTIVITY = "drawer_activity";
    private static final String GIB_LAUNCHER = "com.salat.gbinder/com.salat.gbinder.AppLauncher";
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
    private View dropTarget;
    private LinearLayout widgetControls;
    private ImageView wallpaperView;
    private LinearLayout favoritePanel;
    private LinearLayout appsTile;
    private boolean editingWidgets;
    private Dialog settingsDialog;
    private Dialog appDrawer;
    private int settingsPage;
    private ImageView settingsWallpaperPreview;
    private LinearLayout settingsWallpaperPresets;
    private LinearLayout settingsRedirectState;
    private int pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private final Runnable renderWidgets = this::showWidgets;
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            loadApps();
            showFavorites();
            // An uninstalled provider's widget ID is gone; drop its stale view and placement.
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) scheduleShowWidgets();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        widgetManager = AppWidgetManager.getInstance(this);
        widgetHost = new AppWidgetHost(this, HOST_ID);
        if (state != null) {
            editingWidgets = state.getBoolean("editingWidgets");
            pendingWidgetId = state.getInt("pendingWidgetId", AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        loadSavedState();
        // Power loss during binding or configuration leaves bound IDs that no placement owns.
        for (int id : widgetHost.getAppWidgetIds())
            if (id != pendingWidgetId && widgets.stream().noneMatch(placement -> placement.id == id))
                widgetHost.deleteAppWidgetId(id);
        loadApps();
        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addDataScheme("package");
        registerReceiver(packageReceiver, packages);
        buildHome();
        if (state != null && state.getBoolean("settingsOpen"))
            widgetRow.post(() -> showSettings(state.getInt("settingsPage", 0)));
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("editingWidgets", editingWidgets);
        state.putBoolean("settingsOpen", settingsDialog != null && settingsDialog.isShowing());
        state.putInt("settingsPage", settingsPage);
        // Only a newly allocated widget belongs to the add/cancel lifecycle.
        state.putInt("pendingWidgetId", pendingWidgetId);
    }

    @Override public void onStart() {
        super.onStart();
        widgetHost.startListening();
    }

    @Override public void onResume() {
        super.onResume();
        // The system 12/24-hour setting may have changed while HOME was in the background.
        updateDesktopClock();
        // The accessibility permission may have been granted in Android settings.
        updateStockHomeRedirectState();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home returns to a clean desktop, like the stock launcher.
        if (appDrawer != null) appDrawer.dismiss();
        if (settingsDialog != null) settingsDialog.dismiss();
        if (editingWidgets) setEditingWidgets(false);
    }

    @Override public void onStop() {
        widgetHost.stopListening();
        super.onStop();
    }

    @Override protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        if (settingsDialog != null) settingsDialog.dismiss();
        if (appDrawer != null) appDrawer.dismiss();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (editingWidgets) setEditingWidgets(false);
        // Keep the HOME surface open; dialogs handle Back in their own windows.
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
        FrameLayout.LayoutParams widgetRowParams = new FrameLayout.LayoutParams(-1, -1);
        widgetRowParams.bottomMargin = CLIMATE_PANEL_PX;
        backdrop.addView(widgetRow, widgetRowParams);
        widgetRow.setClickable(true);
        widgetRow.setOnLongClickListener(v -> { setEditingWidgets(true); return true; });
        widgetRow.setOnClickListener(v -> { if (editingWidgets) setEditingWidgets(false); });
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
        widgetControls.setVisibility(editingWidgets ? View.VISIBLE : View.GONE);
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

    }

    private View createDockWidget() {
        favoritePanel = new LinearLayout(this);
        favoritePanel.setGravity(Gravity.CENTER_VERTICAL);
        favoritePanel.setBackground(round(NEUTRAL_SURFACE, Color.TRANSPARENT, 32));
        favoritePanel.setElevation(editingWidgets ? 0 : dp(8));
        HorizontalScrollView favoriteScroll = new HorizontalScrollView(this);
        favoriteScroll.setFillViewport(true);
        favoriteScroll.setHorizontalScrollBarEnabled(false);
        favoritePanel.addView(favoriteScroll, new LinearLayout.LayoutParams(-1, -1));
        favoriteRow = new LinearLayout(this);
        favoriteRow.setGravity(Gravity.CENTER_VERTICAL);
        favoriteScroll.addView(favoriteRow, new FrameLayout.LayoutParams(-2, -1));
        appsTile = dockTile(getDrawable(R.drawable.ic_apps), "Все приложения");
        ImageView appsIcon = (ImageView) appsTile.getChildAt(0);
        appsIcon.setBackground(round(NEUTRAL_RAISED, Color.TRANSPARENT, 34));
        appsIcon.setPadding(dp(18), dp(18), dp(18), dp(18));
        appsTile.setOnClickListener(v -> openAllApps());
        appsTile.setOnLongClickListener(v -> { setEditingWidgets(true); return true; });
        updateDock();
        showFavorites();
        return favoritePanel;
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
    }

    private AppEntry findApp(String flattened) {
        for (AppEntry app : apps) if (app.component.flattenToString().equals(flattened)) return app;
        return null;
    }

    private void showFavorites() {
        if (favoriteRow == null) return;
        favoriteRow.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int iconSize = Math.max(40, Math.min(MAX_ICON_DP, prefs.getInt(DRAWER_ICON_SIZE, 64)));
        boolean showLabels = prefs.getBoolean(DOCK_LABELS, true);
        int tilePadding = prefs.getBoolean(DOCK_COMPACT, false) && !showLabels ? 4 : 8;
        int tileWidth = Math.max(112, iconSize + 12);
        int tileHeight = iconSize + 2 * tilePadding + (showLabels ? 44 : 0);
        ((ImageView) appsTile.getChildAt(0)).setLayoutParams(new LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)));
        appsTile.setPadding(dp(6), dp(tilePadding), dp(6), dp(tilePadding));
        ((TextView) appsTile.getChildAt(1)).setVisibility(showLabels ? View.VISIBLE : View.GONE);
        // Unavailable apps stay pinned: they may be disabled or updating, not uninstalled.
        for (String name : favorites) {
            AppEntry app = findApp(name);
            if (app == null) continue;
            View item = appTile(app, false);
            item.setOnClickListener(v -> launch(app));
            item.setOnLongClickListener(v -> { setEditingWidgets(true); return true; });
            favoriteRow.addView(item, new LinearLayout.LayoutParams(0, dp(tileHeight), 1));
        }
        int shownApps = favoriteRow.getChildCount();
        if (shownApps == 0) {
            TextView empty = label("Добавьте приложения кнопкой ⚙ дока в режиме редактирования", 14, NEUTRAL_MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(14), dp(14), dp(14), dp(14));
            favoriteRow.addView(empty, new LinearLayout.LayoutParams(dp(240), -2));
        }
        favoriteRow.addView(appsTile, new LinearLayout.LayoutParams(0, dp(tileHeight), 1));
        int visibleTiles = shownApps + (appsTile.getVisibility() == View.VISIBLE ? 1 : 0);
        favoriteRow.setMinimumWidth(dp(visibleTiles * tileWidth + (shownApps == 0 ? 240 : 0)));
    }

    private LinearLayout dockTile(Drawable drawable, String title) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean showLabels = prefs.getBoolean(DOCK_LABELS, true);
        int verticalPadding = prefs.getBoolean(DOCK_COMPACT, false) && !showLabels ? 4 : 8;
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(6), dp(verticalPadding), dp(6), dp(verticalPadding));
        tile.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(40, 255, 255, 255)),
                null, round(Color.WHITE, Color.TRANSPARENT, 24)));
        tile.setFocusable(true);
        tile.setContentDescription(title);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(drawable);
        int iconSize = Math.max(40, Math.min(MAX_ICON_DP, prefs.getInt(DRAWER_ICON_SIZE, 64)));
        tile.addView(icon, new LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)));
        TextView titleView = label(title, 14, NEUTRAL_TEXT, false);
        titleView.setGravity(Gravity.CENTER);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        titleView.setVisibility(showLabels ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(36));
        titleParams.topMargin = dp(8);
        tile.addView(titleView, titleParams);
        return tile;
    }

    private View appTile(AppEntry app, boolean card) {
        if (!card) return dockTile(app.icon, app.label);
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(6), dp(6), dp(6), dp(6));
        tile.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(40, 255, 255, 255)),
                null, round(Color.WHITE, Color.TRANSPARENT, 20)));
        int iconSize = CATALOG_ICON_DP;
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)));
        TextView label = label(app.label, 14, NEUTRAL_TEXT, false);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        tile.setContentDescription(app.label);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, dp(40));
        labelParams.setMargins(0, dp(8), 0, 0);
        tile.addView(label, labelParams);
        return tile;
    }

    private void showAppDrawer() {
        if (appDrawer != null) return;
        int iconSize = CATALOG_ICON_DP;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(20), dp(22), dp(20));
        content.setBackground(round(NEUTRAL_SURFACE, Color.TRANSPARENT, 32));
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(titleRow, new LinearLayout.LayoutParams(-1, dp(76)));
        LinearLayout titleText = new LinearLayout(this);
        titleText.setOrientation(LinearLayout.VERTICAL);
        titleText.addView(label("Все приложения", 24, NEUTRAL_TEXT, true));
        titleRow.addView(titleText, new LinearLayout.LayoutParams(0, -2, 1));
        Dialog dialog = new Dialog(this);
        appDrawer = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setOnDismissListener(d -> appDrawer = null);
        Button close = neutralButton("×");
        close.setContentDescription("Закрыть список приложений");
        close.setTextSize(22);
        close.setOnClickListener(v -> dialog.dismiss());
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(64), dp(64)));
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Поиск приложений");
        search.setTextColor(NEUTRAL_TEXT);
        search.setHintTextColor(NEUTRAL_MUTED);
        search.setTextSize(16);
        search.setPadding(dp(18), 0, dp(18), 0);
        search.setBackground(round(NEUTRAL_RAISED, Color.TRANSPARENT, 20));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(76));
        searchParams.setMargins(0, dp(8), 0, dp(18));
        content.addView(search, searchParams);
        GridView grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(iconSize + 64));
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
                tile.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, dp(iconSize + 64)));
                return tile;
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            AppEntry app = visible.get(position);
            dialog.dismiss();
            launch(app);
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
            window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(1200)),
                    Math.min(getResources().getDisplayMetrics().heightPixels - dp(64), dp(1400)));
        }
    }

    private Button neutralButton(String title) {
        Button button = button(title);
        button.setTextColor(NEUTRAL_TEXT);
        button.setMinimumHeight(0);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(40, 255, 255, 255)),
                round(NEUTRAL_RAISED, Color.TRANSPARENT, 20), null));
        return button;
    }

    private void openAllApps() {
        String target = getSharedPreferences(PREFS, MODE_PRIVATE).getString(DRAWER_ACTIVITY, "");
        if (target.isEmpty()) {
            showAppDrawer();
            return;
        }
        ComponentName component = ComponentName.unflattenFromString(target);
        try {
            if (component == null) throw new ActivityNotFoundException();
            startActivity(new Intent(Intent.ACTION_MAIN).setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Выбранная Activity недоступна. Открыт встроенный каталог.", Toast.LENGTH_LONG).show();
            showAppDrawer();
        }
    }

    private void chooseDrawerAction(Runnable onChanged) {
        String[] choices = {"Встроенный каталог", "GLauncher (GIB)", "Activity установленного приложения", "Указать Activity вручную"};
        new AlertDialog.Builder(this).setTitle("Открывать по кнопке «Все приложения»")
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(DRAWER_ACTIVITY).apply();
                        onChanged.run();
                    } else if (which == 1) {
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(DRAWER_ACTIVITY, GIB_LAUNCHER).apply();
                        onChanged.run();
                    } else if (which == 2) {
                        chooseActivityPackage(onChanged);
                    } else {
                        enterDrawerActivity(onChanged);
                    }
                }).show();
    }

    private void chooseActivityPackage(Runnable onChanged) {
        List<String> packages = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        for (AppEntry app : apps) {
            String name = app.component.getPackageName();
            if (packages.contains(name)) continue;
            packages.add(name);
            titles.add(app.label + "\n" + name);
        }
        new AlertDialog.Builder(this).setTitle("Выберите приложение")
                .setItems(titles.toArray(new String[0]), (dialog, which) -> choosePackageActivity(packages.get(which), onChanged)).show();
    }

    @SuppressWarnings("deprecation")
    private void choosePackageActivity(String packageName, Runnable onChanged) {
        List<ComponentName> components = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        try {
            ActivityInfo[] activities = getPackageManager().getPackageInfo(packageName, PackageManager.GET_ACTIVITIES).activities;
            if (activities != null) for (ActivityInfo activity : activities) {
                if (!activity.exported || !activity.enabled || !activity.applicationInfo.enabled) continue;
                if (activity.permission != null && checkSelfPermission(activity.permission) != PackageManager.PERMISSION_GRANTED) continue;
                ComponentName component = new ComponentName(packageName, activity.name);
                components.add(component);
                titles.add(activity.loadLabel(getPackageManager()) + "\n" + activity.name);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(this, "Приложение больше не установлено", Toast.LENGTH_SHORT).show();
            return;
        }
        if (components.isEmpty()) {
            Toast.makeText(this, "Нет доступных для внешнего запуска Activity", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Activity для запуска")
                .setItems(titles.toArray(new String[0]), (dialog, which) -> {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString(DRAWER_ACTIVITY, components.get(which).flattenToString()).apply();
                    onChanged.run();
                }).show();
    }

    private void enterDrawerActivity(Runnable onChanged) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("package.name/.ActivityName");
        input.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString(DRAWER_ACTIVITY, ""));
        input.setPadding(dp(24), dp(16), dp(24), dp(16));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Activity для запуска")
                .setMessage("Укажите пакет и класс Activity через /. Приложение должно разрешать её внешний запуск.")
                .setView(input).setNegativeButton("Отмена", null).setPositiveButton("Сохранить", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            ComponentName component = ComponentName.unflattenFromString(input.getText().toString().trim());
            if (component == null || component.getPackageName().isEmpty() || component.getClassName().isEmpty()) {
                input.setError("Формат: package.name/.ActivityName");
                return;
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(DRAWER_ACTIVITY, component.flattenToString()).apply();
            onChanged.run();
            dialog.dismiss();
        }));
        dialog.show();
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
        showSettings(0);
    }

    private void showSettings(int page) {
        if (settingsDialog != null && settingsDialog.isShowing()) return;
        settingsPage = page;
        Dialog dialog = new Dialog(this);
        settingsDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(d -> {
            settingsDialog = null;
            settingsWallpaperPreview = null;
            settingsWallpaperPresets = null;
            settingsRedirectState = null;
        });

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(20), dp(20), dp(16));
        panel.setBackground(round(NEUTRAL_SURFACE, NEUTRAL_RAISED, 28));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = label(page == 1 ? "Док приложений" : page == 2 ? "Настройки часов" : "Настройки",
                28, NEUTRAL_TEXT, true);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = neutralButton("×");
        close.setTextSize(26);
        close.setContentDescription("Закрыть настройки");
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(52), dp(52)));
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollParams.topMargin = dp(24);
        panel.addView(scroll, scrollParams);
        // Dock and clock are widgets: their pages open only from the widget's own settings button.
        if (page == 1) buildDockSettings(content);
        else if (page == 2) buildClockSettings(content);
        else {
            buildDesktopSettings(content, dialog);
            buildSystemSettings(content);
        }

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView hint = label("Изменения сохраняются сразу", 13, NEUTRAL_MUTED, false);
        footer.addView(hint, new LinearLayout.LayoutParams(0, -2, 1));
        Button done = neutralButton("Готово");
        done.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(dp(112), dp(52));
        doneParams.leftMargin = dp(12);
        footer.addView(done, doneParams);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(16);
        panel.addView(footer, footerParams);
        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            Rect available = new Rect();
            getWindow().getDecorView().getWindowVisibleDisplayFrame(available);
            window.setLayout(Math.min(available.width() - dp(32), dp(880)),
                    Math.min(available.height() - dp(48), dp(1040)));
            window.setDimAmount(0.72f);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    private LinearLayout settingsCard(LinearLayout parent, String title, String description) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(18), dp(16), dp(16));
        card.setBackground(round(Color.rgb(44, 47, 51), Color.TRANSPARENT, 20));
        TextView heading = label(title, 20, NEUTRAL_TEXT, true);
        card.addView(heading);
        if (description != null) {
            TextView subtitle = label(description, 14, NEUTRAL_MUTED, false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, dp(6), 0, dp(12));
            card.addView(subtitle, params);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(14);
        parent.addView(card, params);
        return card;
    }

    private Button settingsAction(LinearLayout parent, String title, Runnable action) {
        Button button = neutralButton(title);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        button.setMinHeight(dp(56));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        parent.addView(button, params);
        return button;
    }

    private void settingsToggle(LinearLayout parent, String title, String key, boolean defaultValue, Runnable changed) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        settingsSwitch(parent, title, prefs.getBoolean(key, defaultValue), checked -> {
            prefs.edit().putBoolean(key, checked).apply();
            changed.run();
        });
    }

    private void settingsSwitch(LinearLayout parent, String title, boolean checked, Consumer<Boolean> changed) {
        Switch toggle = new Switch(this);
        toggle.setText(title);
        toggle.setTextColor(NEUTRAL_TEXT);
        toggle.setTextSize(16);
        toggle.setSwitchPadding(dp(16));
        toggle.setPadding(0, dp(12), 0, dp(12));
        toggle.setMinHeight(dp(60));
        toggle.setThumbTintList(ColorStateList.valueOf(NEUTRAL_TEXT));
        toggle.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Color.rgb(74, 133, 187), NEUTRAL_RAISED}));
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener((view, value) -> changed.accept(value));
        parent.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
    }

    private void buildDesktopSettings(LinearLayout content, Dialog dialog) {
        LinearLayout wallpaper = settingsCard(content, "Фон рабочего стола", "Выберите изображение для главного экрана.");
        ImageView preview = new ImageView(this);
        settingsWallpaperPreview = preview;
        preview.setImageDrawable(wallpaperView.getDrawable().getConstantState().newDrawable());
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(round(NEUTRAL_SURFACE, Color.TRANSPARENT, 16));
        preview.setClipToOutline(true);
        wallpaper.addView(preview, new LinearLayout.LayoutParams(-1, dp(180)));
        TextView presetsLabel = label("Встроенные фоны · листайте в сторону", 14, NEUTRAL_MUTED, false);
        presetsLabel.setPadding(0, dp(16), 0, dp(8));
        wallpaper.addView(presetsLabel);
        HorizontalScrollView presetsScroll = new HorizontalScrollView(this);
        LinearLayout presets = new LinearLayout(this);
        settingsWallpaperPresets = presets;
        int[] presetImages = {
                R.drawable.wallpaper_car_suv, R.drawable.wallpaper_orange_trails,
                R.drawable.wallpaper_graphite, R.drawable.wallpaper_purple_sky,
                R.drawable.wallpaper_car_sedan, R.drawable.wallpaper_red_carbon,
                R.drawable.wallpaper_blue_trails, R.drawable.wallpaper_teal_glass,
                R.drawable.wallpaper_mountain_road, R.drawable.wallpaper_night_city,
                R.drawable.wallpaper_dark_marble, R.drawable.wallpaper_mountain_sunset
        };
        String[] presetNames = {"Внедорожник", "Огни дороги", "Графит", "Звёздная ночь",
                "Седан", "Красный карбон", "Синий поток", "Бирюза", "Горная дорога",
                "Ночной город", "Тёмный мрамор", "Горный закат"};
        for (int i = 0; i < presetImages.length; i++) {
            int resource = presetImages[i];
            ImageView thumbnail = new ImageView(this);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(getResources(), resource, options);
            options.inSampleSize = 1;
            while (options.outWidth / (options.inSampleSize * 2) >= dp(108)) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            thumbnail.setImageBitmap(BitmapFactory.decodeResource(getResources(), resource, options));
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setContentDescription(presetNames[i]);
            thumbnail.setBackground(round(NEUTRAL_SURFACE, Color.TRANSPARENT, 12));
            thumbnail.setClipToOutline(true);
            String uri = "android.resource://" + getPackageName() + "/drawable/"
                    + getResources().getResourceEntryName(resource);
            thumbnail.setTag(uri);
            thumbnail.setOnClickListener(v -> {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(WALLPAPER, uri).apply();
                showWallpaper();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(108), dp(144));
            params.rightMargin = dp(8);
            presets.addView(thumbnail, params);
        }
        presetsScroll.addView(presets);
        markWallpaperPreset(getSharedPreferences(PREFS, MODE_PRIVATE).getString(WALLPAPER, null));
        wallpaper.addView(presetsScroll, new LinearLayout.LayoutParams(-1, -2));
        settingsAction(wallpaper, "Выбрать изображение", () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            try { startActivityForResult(intent, PICK_WALLPAPER); }
            catch (ActivityNotFoundException e) { Toast.makeText(this, "Выбор изображения недоступен", Toast.LENGTH_SHORT).show(); }
        });
        settingsAction(wallpaper, "Вернуть стандартный фон", () -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(WALLPAPER).apply();
            showWallpaper();
        });
        LinearLayout desktop = settingsCard(content, "Рабочий стол",
                "Добавляйте и перемещайте виджеты. Часы, док и другие виджеты настраиваются кнопкой ⚙ на самом виджете.");
        settingsAction(desktop, "Редактировать рабочий стол  →", () -> {
            dialog.dismiss();
            setEditingWidgets(true);
        });
    }

    private void markWallpaperPreset(String wallpaper) {
        if (settingsWallpaperPresets == null) return;
        for (int i = 0; i < settingsWallpaperPresets.getChildCount(); i++) {
            View thumbnail = settingsWallpaperPresets.getChildAt(i);
            GradientDrawable frame = null;
            if (thumbnail.getTag().equals(wallpaper)) {
                frame = round(Color.TRANSPARENT, Color.TRANSPARENT, 12);
                frame.setStroke(dp(3), ACCENT);
            }
            thumbnail.setForeground(frame);
        }
    }

    private void buildSystemSettings(LinearLayout content) {
        LinearLayout system = settingsCard(content, "Система", "Домашнее приложение и настройки Android.");
        settingsAction(system, "Настройки HOME в Android  ↗", () -> {
            try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
            catch (ActivityNotFoundException e) { Toast.makeText(this, "Настройки HOME недоступны", Toast.LENGTH_SHORT).show(); }
        });
        settingsAction(system, "Открыть штатный Launcher3  ↗", () -> {
            // OneOS Launcher3 has several LAUNCHER activities; the package launch intent may pick CarLink.
            Intent intent = new Intent(Intent.ACTION_MAIN).setClassName("com.android.launcher3", "com.android.launcher3.Launcher")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            StockHomeRedirectService.allowStockLauncherUntil = SystemClock.elapsedRealtime() + 5000;
            try { startActivity(intent); }
            catch (ActivityNotFoundException | SecurityException e) { Toast.makeText(this, "Штатный Launcher3 недоступен", Toast.LENGTH_SHORT).show(); }
        });
        settingsAction(system, "Настройки устройства  ↗", () -> {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (ActivityNotFoundException e) { Toast.makeText(this, "Настройки недоступны", Toast.LENGTH_SHORT).show(); }
        });

        LinearLayout redirect = settingsCard(content, "Кнопка «Домой» на панели климата",
                "Панель всегда открывает штатный Launcher3. AtlasLauncher может сразу возвращать на себя, "
                        + "Launcher3 при этом на мгновение мелькнёт. Когда функция выключена, служба не получает событий окон.");
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        settingsSwitch(redirect, "Возвращать на AtlasLauncher", prefs.getBoolean(StockHomeRedirectService.ENABLED, false), checked -> {
            prefs.edit().putBoolean(StockHomeRedirectService.ENABLED, checked).apply();
            updateStockHomeRedirectState();
        });
        settingsRedirectState = new LinearLayout(this);
        settingsRedirectState.setOrientation(LinearLayout.VERTICAL);
        settingsRedirectState.addView(label("", 14, NEUTRAL_MUTED, false));
        settingsAction(settingsRedirectState, "Специальные возможности Android  ↗", () -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (ActivityNotFoundException e) { Toast.makeText(this, "Специальные возможности недоступны", Toast.LENGTH_SHORT).show(); }
        });
        redirect.addView(settingsRedirectState, new LinearLayout.LayoutParams(-1, -2));
        updateStockHomeRedirectState();
    }

    private void updateStockHomeRedirectState() {
        if (settingsRedirectState == null) return;
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(StockHomeRedirectService.ENABLED, false);
        settingsRedirectState.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (!enabled) return;
        // Lists only connected services: after a force stop Android 11 keeps the service unbound until reboot.
        String id = new ComponentName(this, StockHomeRedirectService.class).flattenToShortString();
        boolean active = false;
        for (AccessibilityServiceInfo info : getSystemService(AccessibilityManager.class)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK))
            if (id.equals(info.getId())) active = true;
        ((TextView) settingsRedirectState.getChildAt(0)).setText(active
                ? "Служба работает."
                : "Служба не работает: включите «" + getString(R.string.stock_home_redirect_label)
                        + "» в специальных возможностях Android. Если она уже включена, но AtlasLauncher "
                        + "останавливали принудительно, перезагрузите ГУ.");
    }

    private void setEditingWidgets(boolean editing) {
        editingWidgets = editing;
        widgetControls.setVisibility(editing ? View.VISIBLE : View.GONE);
        showWidgets();
    }

    private void showWallpaper() {
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(WALLPAPER, null);
        if (saved == null) wallpaperView.setImageResource(R.drawable.coastal_twilight);
        else try {
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
        if (settingsWallpaperPreview != null) settingsWallpaperPreview.setImageDrawable(wallpaperView.getDrawable().getConstantState().newDrawable());
        markWallpaperPreset(saved);
    }

    private void buildDockSettings(LinearLayout parent) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        LinearLayout content = settingsCard(parent, "Внешний вид", "Размер значков и плотность дока на рабочем столе.");
        settingsToggle(content, "Компактный док", DOCK_COMPACT, false, this::showWidgets);
        settingsToggle(content, "Подписи в доке", DOCK_LABELS, true, this::showWidgets);
        int size = Math.max(40, Math.min(MAX_ICON_DP, prefs.getInt(DRAWER_ICON_SIZE, 64)));
        LinearLayout.LayoutParams sizeParams = new LinearLayout.LayoutParams(-1, -2);
        sizeParams.topMargin = dp(12);
        content.addView(label("Размер значков", 18, NEUTRAL_TEXT, false), sizeParams);
        SeekBar sizeSlider = new SeekBar(this);
        sizeSlider.setMax((MAX_ICON_DP - 40) / 8);
        sizeSlider.setProgress((size - 40) / 8);
        sizeSlider.setProgressTintList(ColorStateList.valueOf(NEUTRAL_MUTED));
        sizeSlider.setThumbTintList(ColorStateList.valueOf(NEUTRAL_TEXT));
        sizeSlider.setContentDescription("Размер значков дока");
        content.addView(sizeSlider, new LinearLayout.LayoutParams(-1, dp(56)));
        sizeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    prefs.edit().putInt(DRAWER_ICON_SIZE, 40 + progress * 8).apply();
                    showFavorites();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { showWidgets(); }
        });
        LinearLayout drawer = settingsCard(parent, "Все приложения", "Выберите, что открывает кнопка в доке.");
        settingsToggle(drawer, "Показывать кнопку", DOCK_APPS, true, this::showWidgets);
        Button action = settingsAction(drawer, "", () -> { });
        Runnable refreshAction = () -> {
            String target = prefs.getString(DRAWER_ACTIVITY, "");
            action.setText("Открывать: " + drawerActionLabel(target));
        };
        refreshAction.run();
        action.setOnClickListener(v -> chooseDrawerAction(refreshAction));
        LinearLayout pinned = settingsCard(parent, "Закреплённые приложения", "Стрелки меняют порядок, × убирает приложение из дока.");
        LinearLayout selectedApps = new LinearLayout(this);
        selectedApps.setOrientation(LinearLayout.VERTICAL);
        pinned.addView(selectedApps, new LinearLayout.LayoutParams(-1, -2));
        refreshDockFavoriteSettings(selectedApps);
        settingsAction(pinned, "＋  Добавить приложение", () -> showDockAppPicker(selectedApps));
    }

    private String drawerActionLabel(String target) {
        if (target.isEmpty()) return "встроенный каталог";
        if (target.equals(GIB_LAUNCHER)) return "GLauncher (GIB)";
        ComponentName component = ComponentName.unflattenFromString(target);
        if (component == null) return target;
        PackageManager pm = getPackageManager();
        try {
            ActivityInfo activity = pm.getActivityInfo(component, 0);
            String app = String.valueOf(activity.applicationInfo.loadLabel(pm));
            String name = String.valueOf(activity.loadLabel(pm));
            return name.equals(app) ? app : app + " · " + name;
        } catch (PackageManager.NameNotFoundException e) {
            return target + " · недоступно";
        }
    }

    private void refreshDockFavoriteSettings(LinearLayout selectedApps) {
        selectedApps.removeAllViews();
        if (favorites.isEmpty()) {
            selectedApps.addView(label("Нет закреплённых приложений", 16, NEUTRAL_MUTED, false),
                    new LinearLayout.LayoutParams(-1, dp(48)));
            return;
        }
        Runnable refresh = () -> {
            saveFavorites();
            showFavorites();
            refreshDockFavoriteSettings(selectedApps);
        };
        for (int i = 0; i < favorites.size(); i++) {
            AppEntry app = findApp(favorites.get(i));
            String title = app != null ? app.label
                    : ComponentName.unflattenFromString(favorites.get(i)).getPackageName() + " · недоступно";
            int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView icon = new ImageView(this);
            if (app != null) icon.setImageDrawable(app.icon);
            row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
            TextView name = label(title, 18, app != null ? NEUTRAL_TEXT : NEUTRAL_MUTED, false);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1);
            nameParams.leftMargin = dp(8);
            row.addView(name, nameParams);
            Button left = neutralButton("←");
            left.setTextSize(22);
            left.setContentDescription("Передвинуть " + title + " влево");
            left.setEnabled(i > 0);
            left.setOnClickListener(v -> { Collections.swap(favorites, index, index - 1); refresh.run(); });
            row.addView(left, new LinearLayout.LayoutParams(dp(56), dp(56)));
            Button right = neutralButton("→");
            right.setTextSize(22);
            right.setContentDescription("Передвинуть " + title + " вправо");
            right.setEnabled(i < favorites.size() - 1);
            right.setOnClickListener(v -> { Collections.swap(favorites, index, index + 1); refresh.run(); });
            row.addView(right, new LinearLayout.LayoutParams(dp(56), dp(56)));
            Button remove = neutralButton("×");
            remove.setTextSize(22);
            remove.setContentDescription("Убрать " + title + " из дока");
            remove.setOnClickListener(v -> { favorites.remove(index); refresh.run(); });
            row.addView(remove, new LinearLayout.LayoutParams(dp(56), dp(56)));
            selectedApps.addView(row, new LinearLayout.LayoutParams(-1, dp(64)));
        }
    }

    private void showDockAppPicker(LinearLayout selectedApps) {
        List<AppEntry> available = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (AppEntry app : apps) {
            if (favorites.contains(app.component.flattenToString())) continue;
            available.add(app);
            names.add(app.label + " · " + app.component.getPackageName());
        }
        if (available.isEmpty()) {
            Toast.makeText(this, "Все приложения уже добавлены", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Добавить в док")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    favorites.add(available.get(which).component.flattenToString());
                    saveFavorites();
                    showFavorites();
                    refreshDockFavoriteSettings(selectedApps);
                }).show();
    }

    private void updateDock() {
        if (favoritePanel == null) return;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean compact = prefs.getBoolean(DOCK_COMPACT, false);
        int verticalPadding = compact ? (prefs.getBoolean(DOCK_LABELS, true) ? 8 : 4) : 16;
        favoritePanel.setPadding(dp(12), dp(verticalPadding), dp(12), dp(verticalPadding));
        appsTile.setVisibility(prefs.getBoolean(DOCK_APPS, true) ? View.VISIBLE : View.GONE);
    }

    private int dockRequiredHeight(SharedPreferences prefs) {
        int iconSize = Math.max(40, Math.min(MAX_ICON_DP, prefs.getInt(DRAWER_ICON_SIZE, 64)));
        boolean showLabels = prefs.getBoolean(DOCK_LABELS, true);
        boolean compact = prefs.getBoolean(DOCK_COMPACT, false);
        int tilePadding = compact && !showLabels ? 4 : 8;
        int panelPadding = compact ? (showLabels ? 8 : 4) : 16;
        Rect inset = dockInset();
        return iconSize + 2 * tilePadding + (showLabels ? 44 : 0) + 2 * panelPadding
                + pxToDp(inset.top + inset.bottom);
    }

    private Rect dockInset() {
        return AppWidgetHostView.getDefaultPaddingForWidget(this, getComponentName(), null);
    }

    private void chooseWidget() {
        PackageManager pm = getPackageManager();
        List<AppWidgetProviderInfo> providers = widgetManager.getInstalledProviders();
        Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(providers, (a, b) -> collator.compare(String.valueOf(a.loadLabel(pm)), String.valueOf(b.loadLabel(pm))));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(20), dp(22), dp(20));
        content.setBackground(round(NEUTRAL_SURFACE, Color.TRANSPARENT, 32));
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(label("Добавить виджет", 24, NEUTRAL_TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Button close = neutralButton("×");
        close.setContentDescription("Закрыть список виджетов");
        close.setTextSize(22);
        close.setOnClickListener(v -> dialog.dismiss());
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(76));
        titleParams.bottomMargin = dp(12);
        content.addView(titleRow, titleParams);
        GridView grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(WIDGET_PREVIEW_DP + 40));
        grid.setHorizontalSpacing(dp(10));
        grid.setVerticalSpacing(dp(10));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalScrollBarEnabled(false);
        content.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        WidgetGrid cells = widgetGrid();
        Bitmap[] previews = new Bitmap[providers.size()];
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return providers.size() + 2; }
            @Override public Object getItem(int position) { return position; }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View old, ViewGroup parent) {
                if (position == 0) return widgetPreviewTile(clockPreview(), "Часы", "AtlasLauncher");
                if (position == 1) return widgetPreviewTile(dockPreview(), "Док приложений", "AtlasLauncher");
                AppWidgetProviderInfo provider = providers.get(position - 2);
                ImageView image = new ImageView(HomeActivity.this);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setImageBitmap(previews[position - 2]);
                String details = provider.provider.getPackageName();
                try { details = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(details, 0))); }
                catch (PackageManager.NameNotFoundException ignored) { }
                if (cells != null) {
                    Point padding = widgetPaddingDp(provider);
                    int width = pxToDp(Math.max(provider.minWidth, provider.minResizeWidth)) + padding.x;
                    int height = Math.max(96, pxToDp(Math.max(provider.minHeight, provider.minResizeHeight))) + padding.y;
                    details += " · " + cells.span(width, cells.cellWidth, cells.columns) + "×" +
                            cells.span(height, cells.cellHeight, cells.rows);
                }
                return widgetPreviewTile(image, String.valueOf(provider.loadLabel(pm)), details);
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            if (position == 0) addClockWidget();
            else if (position == 1) addDockWidget();
            else addProviderWidget(providers.get(position - 2));
        });
        // Preview images can be large bitmaps, so decode and downscale them off the UI thread.
        ExecutorService loader = Executors.newSingleThreadExecutor();
        dialog.setOnDismissListener(d -> loader.shutdownNow());
        int density = getResources().getDisplayMetrics().densityDpi;
        for (int i = 0; i < providers.size(); i++) {
            int index = i;
            loader.execute(() -> {
                AppWidgetProviderInfo provider = providers.get(index);
                Drawable drawable = provider.loadPreviewImage(this, density);
                if (drawable == null) drawable = provider.loadIcon(this, density);
                Bitmap bitmap = drawable == null ? null : previewBitmap(drawable);
                runOnUiThread(() -> {
                    previews[index] = bitmap;
                    adapter.notifyDataSetChanged();
                });
            });
        }
        dialog.setContentView(content);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(1200)),
                    Math.min(getResources().getDisplayMetrics().heightPixels - dp(64), dp(1400)));
        }
    }

    private View widgetPreviewTile(View preview, String title, String details) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(10), dp(10), dp(10), dp(10));
        tile.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(40, 255, 255, 255)),
                round(NEUTRAL_RAISED, Color.TRANSPARENT, 20), null));
        tile.setContentDescription(title);
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(dp(8), dp(8), dp(8), dp(8));
        frame.setBackground(round(NEUTRAL_SURFACE, Color.TRANSPARENT, 14));
        frame.addView(preview, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        tile.addView(frame, new LinearLayout.LayoutParams(-1, dp(WIDGET_PREVIEW_DP)));
        TextView titleView = label(title, 16, NEUTRAL_TEXT, true);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(10);
        tile.addView(titleView, titleParams);
        TextView detailsView = label(details, 13, NEUTRAL_MUTED, false);
        detailsView.setSingleLine(true);
        detailsView.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(detailsView, new LinearLayout.LayoutParams(-1, -2));
        tile.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, -2));
        return tile;
    }

    private Bitmap previewBitmap(Drawable drawable) {
        int limit = dp(WIDGET_PREVIEW_DP) * 2;
        int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : limit;
        int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : limit;
        float scale = Math.min(1f, (float) limit / Math.max(width, height));
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }

    private View clockPreview() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setGravity(Gravity.CENTER);
        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("h:mm");
        time.setTextSize(44);
        time.setTextColor(TEXT);
        group.addView(time);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEE, d MMM");
        date.setFormat12Hour("EEE, d MMM");
        date.setTextSize(14);
        date.setTextColor(MUTED);
        group.addView(date);
        return group;
    }

    private View dockPreview() {
        LinearLayout panel = new LinearLayout(this);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        panel.setBackground(round(NEUTRAL_RAISED, Color.TRANSPARENT, 20));
        List<AppEntry> shown = new ArrayList<>();
        for (String favorite : favorites) {
            AppEntry app = findApp(favorite);
            if (app != null && shown.size() < 2) shown.add(app);
        }
        for (AppEntry app : apps) if (shown.size() < 2 && !shown.contains(app)) shown.add(app);
        List<Drawable> icons = new ArrayList<>();
        for (AppEntry app : shown) icons.add(app.icon);
        icons.add(getDrawable(R.drawable.ic_apps));
        for (Drawable icon : icons) {
            ImageView image = new ImageView(this);
            image.setImageDrawable(icon);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(40), dp(40));
            params.setMargins(dp(4), 0, dp(4), 0);
            panel.addView(image, params);
        }
        FrameLayout frame = new FrameLayout(this);
        frame.addView(panel, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        return frame;
    }

    private void addProviderWidget(AppWidgetProviderInfo provider) {
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
    }

    private void configureWidget(AppWidgetProviderInfo provider) {
        if (provider.configure == null) {
            finishAddingWidget();
            return;
        }
        // The host API also opens configuration activities that are not exported.
        try { widgetHost.startAppWidgetConfigureActivityForResult(this, pendingWidgetId, 0, CONFIGURE_WIDGET, null); }
        catch (ActivityNotFoundException | SecurityException e) { cancelPendingWidget(); Toast.makeText(this, "Настройка виджета недоступна", Toast.LENGTH_SHORT).show(); }
    }

    private void reconfigureWidget(WidgetPlacement placement) {
        try { widgetHost.startAppWidgetConfigureActivityForResult(this, placement.id, 0, RECONFIGURE_WIDGET, null); }
        catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Настройка виджета недоступна", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == RECONFIGURE_WIDGET) {
            // Both Done and Back keep the existing ID and placement, even after recreation.
            setEditingWidgets(true);
            return;
        }
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
        WidgetPlacement placement = new WidgetPlacement(CLOCK_WIDGET_ID, 0, 0,
                columns * grid.cellWidth, rows * grid.cellHeight);
        compactClockWidget(placement, grid);
        columns = grid.span(placement.width, grid.cellWidth, grid.columns);
        Point slot = findGridSlot(grid, 0, 0, columns, rows, widgets);
        if (slot == null) {
            Toast.makeText(this, "Недостаточно места для часов", Toast.LENGTH_SHORT).show();
            return;
        }
        setGridPlacement(placement, grid, slot, columns, rows);
        widgets.add(placement);
        saveWidgets();
        showWidgets();
    }

    private WidgetPlacement newDockPlacement(WidgetGrid grid) {
        int iconSize = Math.max(40, Math.min(MAX_ICON_DP, getSharedPreferences(PREFS, MODE_PRIVATE).getInt(DRAWER_ICON_SIZE, 64)));
        int tileWidth = Math.max(112, iconSize + 12);
        int contentWidth = favorites.isEmpty() ? 240 + tileWidth : (favorites.size() + 1) * tileWidth;
        int columns = Math.min(grid.columns, Math.max(2,
                (int) Math.ceil((double) (contentWidth + 24) / grid.cellWidth)));
        int rows = grid.span(dockRequiredHeight(getSharedPreferences(PREFS, MODE_PRIVATE)), grid.cellHeight, grid.rows);
        int x = (grid.columns - columns) * grid.cellWidth / 2;
        int y = Math.max(0, grid.rows - rows - 1) * grid.cellHeight;
        Point slot = findGridSlot(grid, x, y, columns, rows, widgets);
        if (slot == null) return null;
        WidgetPlacement placement = new WidgetPlacement(DOCK_WIDGET_ID, 0, 0, 0, 0);
        setGridPlacement(placement, grid, slot, columns, rows);
        return placement;
    }

    private void addDockWidget() {
        for (WidgetPlacement placement : widgets) {
            if (placement.id == DOCK_WIDGET_ID) {
                Toast.makeText(this, "Док уже добавлен", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        WidgetGrid grid = widgetGrid();
        if (grid == null) return;
        WidgetPlacement placement = newDockPlacement(grid);
        if (placement == null) {
            Toast.makeText(this, "Недостаточно места для дока", Toast.LENGTH_SHORT).show();
            return;
        }
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
        // Whole rows end at the climate panel; the leftover goes above them.
        widgetRow.setPadding(0, widgetRow.getHeight() - dp(grid.height), 0, 0);
        // Shows where a dragged or resized widget will land; stays below the widgets.
        dropTarget = new View(this);
        dropTarget.setVisibility(View.GONE);
        widgetRow.addView(dropTarget);
        boolean changed = false;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean(DOCK_WIDGET_MIGRATED, false)) {
            if (prefs.getBoolean(DOCK_VISIBLE, true)) {
                WidgetPlacement dock = newDockPlacement(grid);
                if (dock != null) {
                    widgets.add(dock);
                    changed = true;
                }
            }
            prefs.edit().putBoolean(DOCK_WIDGET_MIGRATED, true).apply();
        }
        favoritePanel = null;
        favoriteRow = null;
        appsTile = null;
        List<WidgetPlacement> occupied = new ArrayList<>();
        Iterator<WidgetPlacement> iterator = widgets.iterator();
        while (iterator.hasNext()) {
            WidgetPlacement placement = iterator.next();
            boolean clockWidget = placement.id == CLOCK_WIDGET_ID;
            boolean dockWidget = placement.id == DOCK_WIDGET_ID;
            AppWidgetProviderInfo info = clockWidget || dockWidget ? null : widgetManager.getAppWidgetInfo(placement.id);
            if (!clockWidget && !dockWidget && info == null) {
                widgetHost.deleteAppWidgetId(placement.id);
                iterator.remove();
                changed = true;
                continue;
            }
            int availableWidth = grid.width;
            int availableHeight = grid.height;
            Point padding = info == null ? new Point(0, 0) : widgetPaddingDp(info);
            int oldX = placement.x, oldY = placement.y;
            int oldWidth = placement.width, oldHeight = placement.height;
            if (placement.width == 0 || placement.height == 0) {
                placement.width = Math.min(availableWidth, clockWidget ? 4 * WIDGET_CELL_DP : dockWidget ? 3 * WIDGET_CELL_DP
                        : pxToDp(Math.max(info.minWidth, info.minResizeWidth)) + padding.x);
                placement.height = Math.min(availableHeight, clockWidget ? 2 * WIDGET_CELL_DP
                        : dockWidget ? grid.span(dockRequiredHeight(prefs), grid.cellHeight, grid.rows) * grid.cellHeight
                        : Math.max(96, pxToDp(Math.max(info.minHeight, info.minResizeHeight))) + padding.y);
                changed = true;
            }
            if (!clockWidget && placement.height < 96 + padding.y && availableHeight >= 96 + padding.y) {
                placement.height = 96 + padding.y;
                changed = true;
            }
            if (clockWidget && placement.clockSize == 0) {
                compactClockWidget(placement, grid);
                changed = true;
            }
            placement.width = Math.min(placement.width, availableWidth);
            placement.height = Math.min(placement.height, availableHeight);
            placement.x = Math.max(0, Math.min(placement.x, availableWidth - placement.width));
            placement.y = Math.max(0, Math.min(placement.y, availableHeight - placement.height));
            int fallbackX = placement.x, fallbackY = placement.y;
            int fallbackWidth = placement.width, fallbackHeight = placement.height;
            int minColumns = clockWidget ? 1 : dockWidget ? Math.min(2, grid.columns)
                    : grid.span(Math.max(56, pxToDp(info.minResizeWidth)) + padding.x, grid.cellWidth, grid.columns);
            int minRows = clockWidget ? 1 : dockWidget ? grid.span(dockRequiredHeight(prefs), grid.cellHeight, grid.rows)
                    : grid.span(Math.max(96, pxToDp(info.minResizeHeight)) + padding.y, grid.cellHeight, grid.rows);
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
                hostView.setTag(CLOCK_WIDGET_ID);
            } else if (dockWidget) {
                hostView = createDockWidget();
            } else {
                AppWidgetHostView widgetView = widgetHost.createView(this, placement.id, info);
                widgetView.setAppWidget(placement.id, info);
                hostView = widgetView;
            }
            hostView.setOnLongClickListener(v -> { setEditingWidgets(true); return true; });
            FrameLayout.LayoutParams hostParams = new FrameLayout.LayoutParams(-1, -1);
            if (dockWidget) {
                // Inset the dock like AppWidgetHostView insets regular widgets.
                Rect inset = dockInset();
                hostParams.setMargins(inset.left, inset.top, inset.right, inset.bottom);
            }
            container.addView(hostView, hostParams);
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

    private void compactClockWidget(WidgetPlacement placement, WidgetGrid grid) {
        LinearLayout clock = (LinearLayout) createClockWidget(placement);
        TextClock time = (TextClock) ((LinearLayout) clock.getChildAt(0)).getChildAt(0);
        TextClock period = (TextClock) ((LinearLayout) clock.getChildAt(0)).getChildAt(1);
        TextClock date = (TextClock) clock.getChildAt(1);
        float contentWidth = clockTimeWidth(time, period);
        if (date.getVisibility() == View.VISIBLE)
            contentWidth = Math.max(contentWidth, date.getPaint().measureText(date.getText().toString()));
        int columns = grid.span((int) Math.ceil(contentWidth / getResources().getDisplayMetrics().density) + 16,
                grid.cellWidth, grid.columns);
        placement.width = Math.min(placement.width, columns * grid.cellWidth);
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
        if (placement.clockSize == 0)
            placement.clockSize = Math.max(28, Math.min(placement.width / 5,
                    Math.round(placement.height / (showDate ? 2.2f : 1.3f))));
        int clockSize = Math.min(placement.clockSize,
                Math.max(28, Math.round(placement.height / (showDate ? 2.2f : 1.3f))));
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
        TextClock period = (TextClock) timeLine.getChildAt(1);
        float availableWidth = dp(Math.max(1, placement.width - 16));
        float timeWidth = clockTimeWidth(clock, period);
        if (timeWidth > availableWidth) {
            float scale = availableWidth / timeWidth;
            clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, clock.getTextSize() * scale);
            period.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, period.getTextSize() * scale);
        }
    }

    private void updateDesktopClock() {
        View desktopClock = widgetRow.findViewWithTag(CLOCK_WIDGET_ID);
        if (desktopClock == null) return;
        for (WidgetPlacement placement : widgets) {
            if (placement.id == CLOCK_WIDGET_ID) {
                updateClockWidget(desktopClock, placement);
                return;
            }
        }
    }

    private float clockTimeWidth(TextClock clock, TextClock period) {
        // Reserve the widest digits so the block does not change width every minute.
        float digitWidth = 0;
        for (int digit = 0; digit <= 9; digit++)
            digitWidth = Math.max(digitWidth, clock.getPaint().measureText(Integer.toString(digit)));
        float width = digitWidth * 4 + clock.getPaint().measureText(":");
        if (period.getVisibility() == View.VISIBLE) {
            float periodWidth = 0;
            for (String label : new java.text.DateFormatSymbols().getAmPmStrings())
                periodWidth = Math.max(periodWidth, period.getPaint().measureText(label));
            width += dp(6) + periodWidth;
        }
        return width;
    }

    private void buildClockSettings(LinearLayout content) {
        LinearLayout preview = settingsCard(content, "Часы", "Предпросмотр · параметры применяются к часам на рабочем столе.");
        WidgetPlacement previewPlacement = new WidgetPlacement(CLOCK_WIDGET_ID, 0, 0, 280, 192);
        previewPlacement.clockSize = 64;
        View clock = createClockWidget(previewPlacement);
        preview.addView(clock, new LinearLayout.LayoutParams(-1, dp(156)));
        Runnable changed = () -> {
            updateClockWidget(clock, previewPlacement);
            updateDesktopClock();
        };
        settingsToggle(preview, "Показывать дату", CLOCK_DATE, true, changed);
        LinearLayout format = settingsCard(content, "Формат времени", null);
        settingsChoice(format, CLOCK_FORMAT, new String[]{"Как в системе", "24 часа", "12 часов"}, changed);
        LinearLayout type = settingsCard(content, "Начертание", null);
        settingsChoice(type, CLOCK_WEIGHT, new String[]{"Тонкий", "Обычный", "Жирный"}, changed);
        LinearLayout font = settingsCard(content, "Шрифт", null);
        settingsChoice(font, CLOCK_FONT, new String[]{"Без засечек", "С засечками", "Моноширинный"}, changed);
    }

    private void settingsChoice(LinearLayout parent, String key, String[] options, Runnable changed) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        android.widget.RadioGroup group = new android.widget.RadioGroup(this);
        for (int i = 0; i < options.length; i++) {
            android.widget.RadioButton option = new android.widget.RadioButton(this);
            option.setId(View.generateViewId());
            option.setText(options[i]);
            option.setTextSize(16);
            option.setTextColor(NEUTRAL_TEXT);
            option.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{Color.rgb(117, 178, 234), NEUTRAL_MUTED}));
            group.addView(option, new LinearLayout.LayoutParams(-1, dp(52)));
            if (i == prefs.getInt(key, 0)) group.check(option.getId());
        }
        group.setOnCheckedChangeListener((view, id) -> {
            prefs.edit().putInt(key, group.indexOfChild(group.findViewById(id))).apply();
            changed.run();
        });
        parent.addView(group, new LinearLayout.LayoutParams(-1, -2));
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
        String title = placement.id == CLOCK_WIDGET_ID ? "часы" : placement.id == DOCK_WIDGET_ID
                ? "док приложений" : String.valueOf(info.loadLabel(getPackageManager()));
        dragSurface.setContentDescription("Перетащить " + title);
        dragSurface.setOnTouchListener(widgetTouch(container, hostView, placement, info, false));
        container.addView(dragSurface, new FrameLayout.LayoutParams(-1, -1));
        if (info == null || info.configure != null) {
            Button settings = button("⚙");
            settings.setContentDescription("Настроить " + title);
            settings.setOnClickListener(v -> {
                if (placement.id == CLOCK_WIDGET_ID) showSettings(2);
                else if (placement.id == DOCK_WIDGET_ID) showSettings(1);
                else reconfigureWidget(placement);
            });
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
        int minWidth = info == null ? (placement.id == DOCK_WIDGET_ID ? 2 : 1) * WIDGET_CELL_DP
                : Math.max(56, pxToDp(info.minResizeWidth)) + padding.x;
        int minHeight = info == null ? placement.id == DOCK_WIDGET_ID
                ? dockRequiredHeight(getSharedPreferences(PREFS, MODE_PRIVATE)) : WIDGET_CELL_DP
                : Math.max(96, pxToDp(info.minResizeHeight)) + padding.y;
        return new View.OnTouchListener() {
            float startX;
            float startY;
            int originalX;
            int originalY;
            int originalWidth;
            int originalHeight;
            int originalClockSize;

            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    startX = event.getRawX();
                    startY = event.getRawY();
                    originalX = placement.x;
                    originalY = placement.y;
                    originalWidth = placement.width;
                    originalHeight = placement.height;
                    originalClockSize = placement.clockSize;
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    int dx = pxToDp(event.getRawX() - startX);
                    int dy = pxToDp(event.getRawY() - startY);
                    int areaWidth = pxToDp(widgetRow.getWidth());
                    int areaHeight = pxToDp(widgetRow.getHeight() - widgetRow.getPaddingTop());
                    if (resizing) {
                        if (info == null || (info.resizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0)
                            placement.width = Math.min(areaWidth - placement.x, Math.max(minWidth, originalWidth + dx));
                        if (info == null || (info.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0)
                            placement.height = Math.min(areaHeight - placement.y, Math.max(minHeight, originalHeight + dy));
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
                    if (resizing && placement.id == CLOCK_WIDGET_ID) {
                        placement.clockSize = Math.max(28, Math.round((float) originalClockSize * placement.width / originalWidth));
                        updateClockWidget(hostView, placement);
                    }
                    WidgetGrid grid = widgetGrid();
                    WidgetPlacement target = grid == null ? null : snappedPlacement(grid);
                    boolean fits = target != null;
                    int color = fits ? ACCENT : Color.rgb(239, 83, 80);
                    FrameLayout.LayoutParams targetParams = new FrameLayout.LayoutParams(
                            dp(fits ? target.width : originalWidth), dp(fits ? target.height : originalHeight));
                    targetParams.leftMargin = dp(fits ? target.x : originalX);
                    targetParams.topMargin = dp(fits ? target.y : originalY);
                    dropTarget.setLayoutParams(targetParams);
                    dropTarget.setBackground(round(Color.argb(64, Color.red(color), Color.green(color), Color.blue(color)), color, 12));
                    dropTarget.setVisibility(View.VISIBLE);
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    dropTarget.setVisibility(View.GONE);
                    WidgetGrid grid = widgetGrid();
                    WidgetPlacement target = event.getActionMasked() == MotionEvent.ACTION_CANCEL || grid == null
                            ? null : snappedPlacement(grid);
                    if (target == null) {
                        placement.x = originalX;
                        placement.y = originalY;
                        placement.width = originalWidth;
                        placement.height = originalHeight;
                    } else {
                        placement.x = target.x;
                        placement.y = target.y;
                        placement.width = target.width;
                        placement.height = target.height;
                        if (placement.id == CLOCK_WIDGET_ID && resizing)
                            placement.clockSize = Math.max(28, Math.round((float) originalClockSize * placement.width / originalWidth));
                        saveWidgets();
                    }
                    if (placement.id == CLOCK_WIDGET_ID && resizing) {
                        placement.clockSize = Math.max(28, Math.round((float) originalClockSize * placement.width / originalWidth));
                    }
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) container.getLayoutParams();
                    params.width = dp(placement.width);
                    params.height = dp(placement.height);
                    params.leftMargin = dp(placement.x);
                    params.topMargin = dp(placement.y);
                    container.setLayoutParams(params);
                    if (resizing) {
                        if (hostView instanceof AppWidgetHostView) updateWidgetSize((AppWidgetHostView) hostView, placement);
                        else if (placement.id == CLOCK_WIDGET_ID) updateClockWidget(hostView, placement);
                    }
                    return true;
                }
                return true;
            }

            // The grid position the widget takes on release, or null when it returns to its original place.
            private WidgetPlacement snappedPlacement(WidgetGrid grid) {
                List<WidgetPlacement> occupied = new ArrayList<>(widgets);
                occupied.remove(placement);
                int columns = resizing ? grid.nearestSpan(placement.width, grid.cellWidth, grid.columns)
                        : grid.span(placement.width, grid.cellWidth, grid.columns);
                int rows = resizing ? grid.nearestSpan(placement.height, grid.cellHeight, grid.rows)
                        : grid.span(placement.height, grid.cellHeight, grid.rows);
                Point slot;
                if (resizing) {
                    columns = Math.max(columns, grid.span(minWidth, grid.cellWidth, grid.columns));
                    rows = Math.max(rows, grid.span(minHeight, grid.cellHeight, grid.rows));
                    int column = originalX / grid.cellWidth;
                    int row = originalY / grid.cellHeight;
                    slot = column + columns <= grid.columns && row + rows <= grid.rows &&
                            gridSlotFree(grid, column, row, columns, rows, occupied)
                            ? new Point(column, row) : null;
                } else {
                    slot = findGridSlot(grid, placement.x, placement.y, columns, rows, occupied);
                }
                if (slot == null) return null;
                WidgetPlacement target = new WidgetPlacement(placement.id, 0, 0, 0, 0);
                setGridPlacement(target, grid, slot, columns, rows);
                return target;
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
                    WidgetPlacement placement = new WidgetPlacement(item.getInt("id"), item.getInt("x"), item.getInt("y"),
                            item.getInt("width"), item.getInt("height"));
                    placement.clockSize = item.optInt("clockSize", 0);
                    widgets.add(placement);
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
                if (widget.id == CLOCK_WIDGET_ID) item.put("clockSize", widget.clockSize);
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
            columns = Math.max(1, width / WIDGET_CELL_DP);
            rows = Math.max(1, height / WIDGET_CELL_DP);
            cellWidth = Math.min(width, WIDGET_CELL_DP);
            cellHeight = Math.min(height, WIDGET_CELL_DP);
            this.height = rows * cellHeight;
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
        int clockSize;

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
