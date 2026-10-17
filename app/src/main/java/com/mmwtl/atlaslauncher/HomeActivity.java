package com.mmwtl.atlaslauncher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Dialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.Log;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.Settings;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.VelocityTracker;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.io.InputStream;
import java.io.IOException;

public final class HomeActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(5, 17, 34);
    private static final int SURFACE = Color.argb(220, 9, 26, 47);
    private static final int SURFACE_RAISED = Color.argb(224, 27, 47, 73);
    private static final int ACCENT = Color.rgb(46, 150, 246);
    private static final int TEXT = Color.rgb(250, 252, 255);
    private static final int MUTED = Color.rgb(188, 204, 224);
    static final int NEUTRAL_SURFACE = Color.rgb(35, 37, 40);
    static final int NEUTRAL_RAISED = Color.rgb(64, 67, 71);
    static final int NEUTRAL_TEXT = Color.rgb(241, 242, 244);
    static final int NEUTRAL_MUTED = Color.rgb(196, 199, 202);
    private static final int HOST_ID = 240925;
    private static final int BIND_WIDGET = 1;
    private static final int CONFIGURE_WIDGET = 2;
    private static final int RECONFIGURE_WIDGET = 4;
    private static final int BIND_SOURCE_LIST = 5;
    private static final int CLOCK_WIDGET_ID = -2;
    private static final int DOCK_WIDGET_ID = -3;
    private static final int VOLUME_WIDGET_ID = -4;
    static final int WIDGET_CELL_DP = 96;
    // The OneOS climate panel overlays the bottom of HOME; physical pixels, not dp.
    static final int CLIMATE_PANEL_PX = 150;
    private static final int HIDDEN_CLIMATE_PANEL_PX = 24;
    static final int MAX_ICON_DP = 160;
    private static final int WIDGET_PREVIEW_DP = 150;
    static final String PREFS = "home";
    static final String FAVORITES = "favorites";
    private static final String WIDGETS = "widgets";
    private static final String WIDGET_PADDING_MIGRATED = "widget_padding_migrated";
    static final String WIDGET_SIDE_MARGIN = "widget_side_margin";
    static final String WALLPAPER = "wallpaper";
    static final String VIDEO_WALLPAPER = "wallpaper_video";
    static final String SLIDESHOW = "wallpaper_slideshow";
    static final String SLIDESHOW_IMAGES = "wallpaper_slideshow_images";
    static final String SLIDESHOW_PERIOD = "wallpaper_slideshow_period";
    private static final String SLIDESHOW_CHANGED_AT = "wallpaper_slideshow_changed_at";
    private static final String SLIDESHOW_BOOT = "wallpaper_slideshow_boot";
    // Index 0 changes the wallpaper once per boot.
    private static final long[] SLIDESHOW_PERIODS_MS = {0, 15 * 60_000, 60 * 60_000, 24 * 60 * 60_000};
    static final int[] PRESET_WALLPAPERS = {
            R.drawable.wallpaper_car_suv, R.drawable.wallpaper_orange_trails,
            R.drawable.wallpaper_graphite, R.drawable.wallpaper_purple_sky,
            R.drawable.wallpaper_car_sedan, R.drawable.wallpaper_red_carbon,
            R.drawable.wallpaper_blue_trails, R.drawable.wallpaper_teal_glass,
            R.drawable.wallpaper_mountain_road, R.drawable.wallpaper_night_city,
            R.drawable.wallpaper_dark_marble, R.drawable.wallpaper_mountain_sunset
    };
    static final String[] PRESET_NAMES = {"Внедорожник", "Огни дороги", "Графит", "Звёздная ночь",
            "Седан", "Красный карбон", "Синий поток", "Бирюза", "Горная дорога",
            "Ночной город", "Тёмный мрамор", "Горный закат"};
    static final String CLIMATE_PANEL_HIDDEN = "climate_panel_hidden";
    static final String CLUSTER_BRIGHTNESS = "cluster_brightness_follows_screen";
    static final String GESTURES_ENABLED = "gestures_enabled";
    static final String GESTURES_PRIORITY = "gestures_priority";
    static final String GESTURES_TRIGGER = "gestures_trigger";
    private static final String DOCK_VISIBLE = "dock_visible";
    private static final String DOCK_WIDGET_MIGRATED = "dock_widget_migrated";
    static final String DOCK_APPS = "dock_apps";
    static final String DRAWER_ICON_SIZE = "drawer_icon_size";
    static final String DOCK_LABELS = "drawer_labels";
    static final String DRAWER_ACTIVITY = "drawer_activity";
    static final String EDIT_DELAY = "edit_long_press_delay";
    // Hold time before editing starts; index 0 is the system long press timeout.
    private static final long[] EDIT_DELAYS_MS = {0, 1000, 2000, 3000};
    static final String HIDE_BY_DOUBLE_TAP = "hide_widgets_double_tap";
    static final String HIDE_BY_PANEL_HOME = "hide_widgets_panel_home";
    static final String MULTIPLE_PAGES = "multiple_desktop_pages";
    private static final String WIDGETS_HIDDEN = "widgets_hidden";
    private static final long PAGE_SNAP_MS = 250;
    // A widget dragged this close to the side of the grid moves to the next page after a pause.
    private static final int PAGE_EDGE_DP = 56;
    private static final long PAGE_EDGE_DELAY_MS = 500;
    private static final long PAGE_EDGE_REPEAT_MS = 1000;
    // The OneOS media widget asks Launcher3 to show its source list; Atlas hosts that list itself.
    private static final String MEDIA_WIDGET_PACKAGE = "com.geely.mediawidget";
    private static final ComponentName SOURCE_LIST_PROVIDER = new ComponentName(MEDIA_WIDGET_PACKAGE,
            MEDIA_WIDGET_PACKAGE + ".customwidget.SourceListWidgetProvider");
    private static final String SOURCE_LIST_WIDGET = "source_list_widget";
    // QNX hides the boot logo when this service reports boot complete (IDIMKey.notifyBootComplete, code 3).
    private static final ComponentName DIM_KEY_SERVICE = new ComponentName("com.autolink.diminteraction",
            "com.autolink.diminteraction.DIMKeyService");
    // The QNX boot animation acknowledges the report and exits; asked after that, the service resends it every
    // second until it restarts. So HOME asks only while the logo can still be up after a boot.
    private static final long BOOT_LOGO_WINDOW_MS = 120_000;
    // The logo stays over HOME while widgets and the dock fill in; the OEM climate panel usually appears 4-10 s
    // after the first frame of HOME.
    private static final long BOOT_LOGO_HOLD_MS = 5_000;
    // Launcher3 takes about 4 s to start after boot; it starts under the logo, after HOME has drawn.
    private static final long LAUNCHER3_PRELOAD_DELAY_MS = 2_000;
    // Android display 4 (1920×532) is not shown anywhere on the G636; Launcher3 opened there stays out of sight.
    // Display 1 (800×480) is not used: on cars with a HUD it can be shown there.
    private static final int HIDDEN_DISPLAY_ID = 4;
    private final List<AppEntry> apps = new ArrayList<>();
    // The catalog activity shows the list HOME last loaded instead of loading it again.
    static List<AppEntry> loadedApps;
    private final List<String> favorites = new ArrayList<>();
    private final List<WidgetPlacement> widgets = new ArrayList<>();
    private final Map<ComponentName, Point> fixedLayouts = new HashMap<>();
    private AppWidgetManager widgetManager;
    private AppWidgetHost widgetHost;
    private LinearLayout favoriteRow;
    private FrameLayout widgetRow;
    private View dropTarget;
    private LinearLayout widgetControls;
    private Button settingsButton;
    private ImageView wallpaperView;
    private VideoWallpaperView videoWallpaper;
    private LinearLayout favoritePanel;
    private LinearLayout appsTile;
    private boolean editingWidgets;
    private boolean startupPending;
    private boolean homeVisible;
    private float pressX;
    private float pressY;
    private boolean pressStartedEditing;
    // Desktop pages lie side by side in pageLayer with the current one in view; each holds its widgets at grid
    // coordinates. Hiding the widgets hides the whole layer.
    private final List<FrameLayout> pages = new ArrayList<>();
    private FrameLayout pageLayer;
    private int currentPage;
    private LinearLayout pageIndicator;
    private boolean widgetsHidden;
    private long lastEmptyTap;
    // Home pressed while HOME is already in front returns to the first page, as in Launcher3.
    private boolean stoppedSinceResume;
    private final Runnable hideIndicator = () -> { if (!editingWidgets) pageIndicator.setVisibility(View.GONE); };
    private PopupWindow sourceListPopup;
    // The saved wallpaper on screen, so a change made in settings loads only a new image.
    private String shownWallpaper;
    private String shownVideo;
    private int wallpaperGeneration;
    private int slideshowGeneration;
    private int pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    // Icons and labels of every app take long to load right after boot; HOME draws without waiting for them.
    private final ExecutorService appLoader = Executors.newSingleThreadExecutor();
    private final ExecutorService wallpaperLoader = Executors.newSingleThreadExecutor();
    private boolean dimKeyBound;
    private long bootReportedAt;
    private boolean launcher3Preloaded;
    // The service reports boot complete by itself only when com.android.launcher3 starts, otherwise 10 s after
    // its own start; GIB force-stops it after boot, which restarts that wait. So HOME asks once it is drawn.
    private final ServiceConnection dimKeyConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            if (bootReportedAt != 0 || SystemClock.elapsedRealtime() > BOOT_LOGO_WINDOW_MS) return;
            // A service started by this binding sees the preloaded Launcher3 and reports by itself; a report from
            // HOME as well would be repeated every second once QNX acknowledged the first one.
            if (launcher3Preloaded) return;
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("com.autolink.diminteraction.IDIMKey");
                service.transact(3, data, reply, 0);
                reply.readException();
                bootReportedAt = SystemClock.elapsedRealtime();
            } catch (RemoteException | RuntimeException e) {
                Log.w("AtlasLauncher", "Cannot report boot complete to the DIM key service", e);
            } finally {
                data.recycle();
                reply.recycle();
            }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            // The system reconnects when the service restarts.
        }

        @Override public void onBindingDied(ComponentName name) {
            // A force stop ends the binding for good. QNX acknowledges in a fraction of a second, so ask the
            // restarted service only if the stop came before or right after the report.
            unbindService(this);
            dimKeyBound = false;
            if (bootReportedAt == 0 || SystemClock.elapsedRealtime() - bootReportedAt < 3_000) {
                bootReportedAt = 0;
                bindDimKeyService();
            }
        }
    };
    // The OneOS power service (com.autolink.adapterbinder.IPower) reports every screen brightness level 0-15 to
    // registered callbacks and takes the cluster level on the same scale, like the two sliders in the shade.
    private static final String POWER_PACKAGE = "com.autolink.powerservice";
    private static final String POWER = "com.autolink.adapterbinder.IPower";
    private static final String POWER_CALLBACK = "com.autolink.adapterbinder.IPowerCallback";
    private static final int POWER_REGISTER = 1;
    private static final int POWER_UNREGISTER = 2;
    private static final int POWER_GET_SCREEN = 4;
    private static final int POWER_SET_CLUSTER = 5;
    private IBinder powerService;
    private boolean powerBound;
    private final Binder powerCallback = new Binder() {
        { attachInterface(null, POWER_CALLBACK); }

        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            // Call 1 reports the screen level; calls 2-6 report day/night, the cluster and the passenger display.
            if (code < FIRST_CALL_TRANSACTION || code > 6) return super.onTransact(code, data, reply, flags);
            data.enforceInterface(POWER_CALLBACK);
            if (code == 1) {
                int level = data.readInt();
                runOnUiThread(() -> setClusterBrightness(level));
            }
            reply.writeNoException();
            return true;
        }
    };
    private final ServiceConnection powerConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            powerService = service;
            try {
                callPower(POWER_REGISTER, powerCallback);
                setClusterBrightness(callPower(POWER_GET_SCREEN, null));
            } catch (RemoteException | RuntimeException e) {
                Log.w("AtlasLauncher", "Cannot follow the screen brightness", e);
            }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            // The system reconnects when the service restarts; the new instance needs the callback again.
            powerService = null;
        }

        @Override public void onBindingDied(ComponentName name) {
            unbindService(this);
            powerBound = false;
            powerService = null;
            applyClusterBrightness();
        }
    };
    private final Runnable renderWidgets = this::showWidgets;
    private final Runnable slideshowStep = this::advanceSlideshow;
    private final Runnable editByPress = this::startEditingByPress;
    // Settings only write the prefs; HOME applies each change, also while it is hidden under them.
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (prefs, key) -> {
        if (key == null) return;
        switch (key) {
            case VIDEO_WALLPAPER: case WALLPAPER:
                if (!Objects.equals(shownWallpaper, prefs.getString(WALLPAPER, null))
                        || !Objects.equals(shownVideo, prefs.getString(VIDEO_WALLPAPER, null))) showWallpaper();
                scheduleSlideshow();
                break;
            case SLIDESHOW: case SLIDESHOW_IMAGES: case SLIDESHOW_PERIOD: scheduleSlideshow(); break;
            case WIDGET_SIDE_MARGIN: applyWidgetSideMargin(); break;
            case MULTIPLE_PAGES:
                currentPage = 0;
                scheduleShowWidgets();
                break;
            case CLIMATE_PANEL_HIDDEN: applyClimatePanel(); break;
            case CLUSTER_BRIGHTNESS: applyClusterBrightness(); break;
            case HIDE_BY_DOUBLE_TAP: case HIDE_BY_PANEL_HOME:
                if (widgetsHidden && !prefs.getBoolean(HIDE_BY_DOUBLE_TAP, false)
                        && !prefs.getBoolean(HIDE_BY_PANEL_HOME, false)) setWidgetsHidden(false);
                break;
            case GESTURES_ENABLED: case GESTURES_PRIORITY: case GESTURES_TRIGGER: applyGestures(); break;
            case FAVORITES: loadFavorites(prefs); showFavorites(); break;
            case DOCK_LABELS: case DOCK_APPS: case DRAWER_ICON_SIZE: scheduleShowWidgets(); break;
            case BuiltInWidgets.CLOCK_FORMAT: case BuiltInWidgets.CLOCK_WEIGHT: case BuiltInWidgets.CLOCK_FONT:
            case BuiltInWidgets.CLOCK_DATE: updateDesktopClock(); break;
            case BuiltInWidgets.VOLUME_MUTE: case BuiltInWidgets.VOLUME_ICONS: case BuiltInWidgets.VOLUME_JOINED:
                updateDesktopVolume(); break;
        }
    };
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            fixedLayouts.clear();
            loadApps();
            // An uninstalled provider's widget ID is gone; drop its stale view and placement.
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) scheduleShowWidgets();
        }
    };

    // Keeps the mute button in sync when hardware keys or the system change the volume.
    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            updateDesktopVolume();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        startDimService();
        applyGestures();
        applyClusterBrightness();
        startupPending = true;
        widgetManager = AppWidgetManager.getInstance(this);
        widgetHost = new AppWidgetHost(this, HOST_ID) {
            @Override protected AppWidgetHostView onCreateView(Context context, int id, AppWidgetProviderInfo info) {
                return new FittedWidgetView(context);
            }
        };
        if (state != null) {
            editingWidgets = state.getBoolean("editingWidgets");
            pendingWidgetId = state.getInt("pendingWidgetId", AppWidgetManager.INVALID_APPWIDGET_ID);
            currentPage = state.getInt("page");
        }
        loadSavedState();
        multiplePagesEnabled(this);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        widgetsHidden = prefs.getBoolean(WIDGETS_HIDDEN, false)
                && (prefs.getBoolean(HIDE_BY_DOUBLE_TAP, false) || prefs.getBoolean(HIDE_BY_PANEL_HOME, false));
        int sourceListId = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(SOURCE_LIST_WIDGET, AppWidgetManager.INVALID_APPWIDGET_ID);
        // Power loss during binding or configuration leaves bound IDs that no placement owns.
        for (int id : widgetHost.getAppWidgetIds())
            if (id != pendingWidgetId && id != sourceListId && widgets.stream().noneMatch(placement -> placement.id == id))
                widgetHost.deleteAppWidgetId(id);
        loadApps();
        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addDataScheme("package");
        registerReceiver(packageReceiver, packages);
        IntentFilter volume = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
        volume.addAction("android.media.STREAM_MUTE_CHANGED_ACTION");
        registerReceiver(volumeReceiver, volume);
        // HOME is not drawn yet, so a due slideshow change here is never seen.
        if (slideshowDelay() == 0) saveSlide(nextSlides().get(0));
        buildHome();
        getSharedPreferences(PREFS, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener);
        if (state == null && getIntent().getBooleanExtra(StockHomeRedirectService.EXTRA_OPEN_ALL_APPS, false))
            AllAppsActivity.open(this);
    }

    // Only stock Launcher3 starts the DIM service; without it the cluster gets no media/phone info after boot.
    private void startDimService() {
        try {
            startService(new Intent().setComponent(
                    new ComponentName("com.geely.dimservice", "com.geely.dimservice.service.DimService")));
        } catch (SecurityException | IllegalStateException e) {
            Log.w("AtlasLauncher", "Cannot start DIM service", e);
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("editingWidgets", editingWidgets);
        // Only a newly allocated widget belongs to the add/cancel lifecycle.
        state.putInt("pendingWidgetId", pendingWidgetId);
        state.putInt("page", currentPage);
    }

    @Override public void onStart() {
        super.onStart();
        homeVisible = true;
        setWallpaperPlaying(true);
        scheduleSlideshow();
        widgetHost.startListening();
    }

    @Override public void onResume() {
        super.onResume();
        stoppedSinceResume = false;
        // The system 12/24-hour setting may have changed while HOME was in the background.
        updateDesktopClock();
        // Like Launcher3: OneOS LifeControlService starts its last boot stage (hvac, settings, gesture and
        // other OEM services, night mode) only on this broadcast from the first resume of HOME.
        if (startupPending) {
            sendBroadcast(new Intent("com.android.launcher3.startup"));
            // Posted before the window is attached, the delays count from the first frame.
            getWindow().getDecorView().postDelayed(this::preloadLauncher3, LAUNCHER3_PRELOAD_DELAY_MS);
            getWindow().getDecorView().postDelayed(this::bindDimKeyService, BOOT_LOGO_HOLD_MS);
        }
        startupPending = false;
    }

    private void preloadLauncher3() {
        if (isDestroyed() || SystemClock.elapsedRealtime() > BOOT_LOGO_WINDOW_MS) return;
        // The climate panel Home click starts Launcher3; a Launcher3 activity created for the first time shows on
        // screen before AtlasLauncher returns. So Launcher3 is opened once on a display nobody sees: later clicks
        // bring its task to front there, and the redirect service returns AtlasLauncher when its window appears.
        // The DIM key service does not see Launcher3 start there, only once the service itself starts; if GIB stopped
        // it, binding from HOME restarts it, otherwise its own fallback timer reports.
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(StockHomeRedirectService.ENABLED, false)
                || !StockHomeRedirectService.isActive(this)) return;
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).setClassName("com.android.launcher3", "com.android.launcher3.Launcher")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    ActivityOptions.makeBasic().setLaunchDisplayId(HIDDEN_DISPLAY_ID).toBundle());
            launcher3Preloaded = true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w("AtlasLauncher", "Cannot open Launcher3 under the boot logo", e);
        }
    }

    private void bindDimKeyService() {
        if (isDestroyed() || SystemClock.elapsedRealtime() > BOOT_LOGO_WINDOW_MS) return;
        try {
            bindService(new Intent().setComponent(DIM_KEY_SERVICE), dimKeyConnection, BIND_AUTO_CREATE);
            dimKeyBound = true;
        } catch (SecurityException e) {
            Log.w("AtlasLauncher", "Cannot bind the DIM key service", e);
        }
    }

    // Night mode switches with the lights and once more after boot, when OneOS applies the mode deferred until
    // the HOME startup broadcast. Atlas colors are fixed; only widgets and app icons may have night resources.
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        loadApps();
        showWidgets();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home returns to a clean desktop, like the stock launcher; the settings above HOME close with its task.
        boolean onMainPage = currentPage == 0;
        if (editingWidgets) setEditingWidgets(false);
        if (intent.getBooleanExtra(StockHomeRedirectService.EXTRA_OPEN_ALL_APPS, false)) AllAppsActivity.open(this);
        else if (intent.getBooleanExtra(StockHomeRedirectService.EXTRA_PANEL_HOME, false)
                && getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(HIDE_BY_PANEL_HOME, false)) {
            // As in Launcher3: first return to the main desktop, then toggle its widgets on another press.
            boolean popup = sourceListPopup != null && sourceListPopup.isShowing();
            boolean hidden = !stoppedSinceResume && onMainPage && !AllAppsActivity.shown && !widgetsHidden;
            if (popup) sourceListPopup.dismiss();
            if (hidden != widgetsHidden) setWidgetsHidden(hidden);
            snapToPage(0);
        } else if (!stoppedSinceResume) snapToPage(0);
    }

    @Override public void onStop() {
        if (sourceListPopup != null) sourceListPopup.dismiss();
        widgetHost.stopListening();
        homeVisible = false;
        setWallpaperPlaying(false);
        stoppedSinceResume = true;
        // The slideshow changes the wallpaper only while another app covers HOME.
        scheduleSlideshow();
        super.onStop();
    }

    @Override protected void onDestroy() {
        wallpaperGeneration++;
        setWallpaperPlaying(false);
        wallpaperView.removeCallbacks(slideshowStep);
        appLoader.shutdownNow();
        wallpaperLoader.shutdownNow();
        if (dimKeyBound) unbindService(dimKeyConnection);
        if (powerBound) releasePowerService();
        unregisterReceiver(packageReceiver);
        unregisterReceiver(volumeReceiver);
        getSharedPreferences(PREFS, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener);
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        // Keep the HOME surface open; dialogs handle Back in their own windows.
        // Back also stays in edit mode: the OneOS edge swipe injects Back on every
        // non-Launcher3 app, so a swipe from the screen edge would end editing.
    }

    private void buildHome() {
        // Like Launcher3: draw the wallpaper under a transparent status bar.
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        getWindow().setNavigationBarColor(BACKGROUND);
        FrameLayout backdrop = new FrameLayout(this);
        wallpaperView = new ImageView(this);
        wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        videoWallpaper = new VideoWallpaperView(this, wallpaperLoader, () -> {
            wallpaperView.setImageDrawable(null);
        }, () -> {
            wallpaperView.setImageResource(R.drawable.coastal_twilight);
            Toast.makeText(this, "Не удалось воспроизвести видео. Выберите другой файл.", Toast.LENGTH_LONG).show();
        });
        backdrop.addView(videoWallpaper, new FrameLayout.LayoutParams(-1, -1));
        wallpaperView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)
                updateStatusBarIcons();
        });
        backdrop.addView(wallpaperView, new FrameLayout.LayoutParams(-1, -1));
        showWallpaper();
        setContentView(backdrop);
        FrameLayout content = new FrameLayout(this);
        content.setFitsSystemWindows(true);
        backdrop.addView(content, new FrameLayout.LayoutParams(-1, -1));
        widgetRow = new PagedRow(this);
        content.addView(widgetRow, new FrameLayout.LayoutParams(-1, -1));
        applyClimatePanel();
        applyWidgetSideMargin();
        widgetRow.setClickable(true);
        widgetRow.setOnClickListener(v -> onEmptyTap());
        widgetRow.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)
                scheduleShowWidgets();
        });

        settingsButton = button("⚙");
        settingsButton.setContentDescription("Настройки и возврат к штатному HOME");
        settingsButton.setTextSize(28);
        settingsButton.setBackground(round(SURFACE, Color.TRANSPARENT, 38));
        settingsButton.setVisibility(editingWidgets ? View.VISIBLE : View.GONE);
        settingsButton.setOnClickListener(v -> SettingsActivity.open(this, SettingsActivity.PAGE_MAIN));
        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(dp(76), dp(76), Gravity.TOP | Gravity.RIGHT);
        settingsParams.setMargins(0, dp(16), dp(20), 0);
        content.addView(settingsButton, settingsParams);

        // Between the edit controls at the top, so it covers no widget controls.
        pageIndicator = new LinearLayout(this);
        pageIndicator.setGravity(Gravity.CENTER);
        pageIndicator.setPadding(dp(10), 0, dp(10), 0);
        pageIndicator.setBackground(round(SURFACE, Color.TRANSPARENT, 18));
        pageIndicator.setVisibility(View.GONE);
        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(-2, dp(36), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        indicatorParams.topMargin = dp(36);
        content.addView(pageIndicator, indicatorParams);

        widgetControls = new LinearLayout(this);
        widgetControls.setGravity(Gravity.CENTER_VERTICAL);
        widgetControls.setVisibility(editingWidgets ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(-2, dp(76), Gravity.TOP | Gravity.LEFT);
        controlsParams.setMargins(dp(20), dp(16), 0, 0);
        content.addView(widgetControls, controlsParams);
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
        appsTile.setOnClickListener(v -> AllAppsActivity.open(this));
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
        appLoader.execute(() -> {
            List<AppEntry> loaded = queryApps(this);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                apps.clear();
                apps.addAll(loaded);
                loadedApps = loaded;
                showFavorites();
            });
        });
    }

    static List<AppEntry> queryApps(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<AppEntry> loaded = new ArrayList<>();
        for (ResolveInfo info : pm.queryIntentActivities(intent, 0)) {
            if (info.activityInfo == null || !info.activityInfo.exported) continue;
            ComponentName component = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
            if (component.getPackageName().equals(context.getPackageName())) continue;
            loaded.add(new AppEntry(component, info.loadLabel(pm).toString(), info.loadIcon(pm)));
        }
        Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(loaded, (a, b) -> collator.compare(a.label, b.label));
        return Collections.unmodifiableList(loaded);
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
        int tilePadding = showLabels ? 8 : 4;
        int tileWidth = Math.max(112, iconSize + 12);
        int tileHeight = iconSize + 2 * tilePadding + (showLabels ? 44 : 0);
        ((ImageView) appsTile.getChildAt(0)).setLayoutParams(new LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)));
        appsTile.setPadding(dp(6), dp(tilePadding), dp(6), dp(tilePadding));
        ((TextView) appsTile.getChildAt(1)).setVisibility(showLabels ? View.VISIBLE : View.GONE);
        // Unavailable apps stay pinned: they may be disabled or updating, not uninstalled.
        for (String name : favorites) {
            AppEntry app = findApp(name);
            if (app == null) continue;
            View item = dockTile(app.icon, app.label);
            item.setOnClickListener(v -> launch(app));
            favoriteRow.addView(item, new LinearLayout.LayoutParams(0, dp(tileHeight), 1));
        }
        int shownApps = favoriteRow.getChildCount();
        // Until the app list has loaded, pinned apps cannot be found yet; skip the hint instead of flashing it.
        if (shownApps == 0 && !apps.isEmpty()) {
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
        int verticalPadding = showLabels ? 8 : 4;
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

    private Button neutralButton(String title) {
        Button button = button(title);
        button.setTextColor(NEUTRAL_TEXT);
        button.setMinimumHeight(0);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(40, 255, 255, 255)),
                round(NEUTRAL_RAISED, Color.TRANSPARENT, 20), null));
        return button;
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
        }
    }

    static String presetUri(Context context, int resource) {
        return "android.resource://" + context.getPackageName() + "/drawable/" + context.getResources().getResourceEntryName(resource);
    }

    static boolean isPresetUri(String uri) {
        return ContentResolver.SCHEME_ANDROID_RESOURCE.equals(Uri.parse(uri).getScheme());
    }

    static List<String> slideshowImages(Context context) {
        List<String> images = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(context.getSharedPreferences(PREFS, MODE_PRIVATE).getString(SLIDESHOW_IMAGES, "[]"));
            for (int i = 0; i < saved.length(); i++) images.add(saved.getString(i));
        } catch (JSONException e) {
            Log.w("AtlasLauncher", "Cannot read slideshow images", e);
        }
        return images;
    }

    static void saveSlideshowImages(Context context, List<String> images) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(SLIDESHOW_IMAGES, new JSONArray(images).toString()).apply();
    }

    /** Replaces the timer and invalidates any image still loading with the previous settings. */
    private void scheduleSlideshow() {
        slideshowGeneration++;
        wallpaperView.removeCallbacks(slideshowStep);
        if (homeVisible) return;
        long delay = slideshowDelay();
        if (delay >= 0) wallpaperView.postDelayed(slideshowStep, delay);
    }

    /** Returns the time until the slideshow is due to change the wallpaper, or -1 if it is not. */
    private long slideshowDelay() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getString(VIDEO_WALLPAPER, null) != null
                || !prefs.getBoolean(SLIDESHOW, false) || slideshowImages(this).isEmpty()) return -1;
        int period = prefs.getInt(SLIDESHOW_PERIOD, 0);
        if (period == 0) return prefs.getInt(SLIDESHOW_BOOT, -1) != bootCount() ? 0 : -1;
        long elapsed = System.currentTimeMillis() - prefs.getLong(SLIDESHOW_CHANGED_AT, 0);
        // A clock set back after the last change makes the change due.
        return elapsed < 0 ? 0 : Math.max(0, SLIDESHOW_PERIODS_MS[period] - elapsed);
    }

    private int bootCount() {
        return Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, 0);
    }

    /** Returns the slideshow images in showing order, starting after the current wallpaper. */
    private List<String> nextSlides() {
        List<String> slides = slideshowImages(this);
        Collections.rotate(slides, -(slides.indexOf(getSharedPreferences(PREFS, MODE_PRIVATE).getString(WALLPAPER, null)) + 1));
        return slides;
    }

    private void saveSlide(String uri) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(WALLPAPER, uri)
                .putLong(SLIDESHOW_CHANGED_AT, System.currentTimeMillis()).putInt(SLIDESHOW_BOOT, bootCount()).apply();
    }

    /** Shows the next readable slideshow image; runs only while HOME is hidden. */
    private void advanceSlideshow() {
        if (isDestroyed() || homeVisible || slideshowDelay() != 0) return;
        int generation = slideshowGeneration;
        List<String> slides = nextSlides();
        wallpaperLoader.execute(() -> {
            for (String slide : slides) {
                Drawable image = decodeWallpaperDrawable(this, Uri.parse(slide), 2048);
                if (image == null) continue;
                runOnUiThread(() -> {
                    // HOME or the settings may have changed while the image loaded.
                    if (isDestroyed() || homeVisible || generation != slideshowGeneration) return;
                    applyWallpaper(image);
                    shownWallpaper = slide;
                    saveSlide(slide);
                });
                return;
            }
        });
    }

    private void applyWidgetSideMargin() {
        // Stored in half cells, so a screen that fits whole cells keeps whole cells.
        int margin = dp(getSharedPreferences(PREFS, MODE_PRIVATE).getInt(WIDGET_SIDE_MARGIN, 0) * WIDGET_CELL_DP / 2);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) widgetRow.getLayoutParams();
        params.leftMargin = margin;
        params.rightMargin = margin;
        widgetRow.setLayoutParams(params);
    }

    private void applyGestures() {
        Intent service = new Intent(this, GestureFilterService.class).putExtra(GestureFilterService.EXTRA_PRIORITY,
                getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(GESTURES_PRIORITY, false))
                .putExtra(GestureFilterService.EXTRA_TRIGGER, getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getInt(GESTURES_TRIGGER, MultiFingerGestures.TRIGGER_NORMAL));
        try {
            if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(GESTURES_ENABLED, false)) startForegroundService(service);
            else stopService(service);
        } catch (IllegalStateException | SecurityException e) {
            Log.w("AtlasLauncher", "Cannot change the gesture service", e);
        }
    }

    private void applyClusterBrightness() {
        boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(CLUSTER_BRIGHTNESS, false);
        if (enabled && !powerBound) {
            try {
                powerBound = bindService(new Intent(POWER_PACKAGE + ".PowerService").setPackage(POWER_PACKAGE),
                        powerConnection, BIND_AUTO_CREATE);
            } catch (SecurityException e) {
                Log.w("AtlasLauncher", "Cannot bind the power service", e);
            }
        } else if (!enabled && powerBound) {
            releasePowerService();
        }
    }

    private void releasePowerService() {
        if (powerService != null) {
            try { callPower(POWER_UNREGISTER, powerCallback); }
            catch (RemoteException | RuntimeException e) { Log.w("AtlasLauncher", "Cannot stop following the screen brightness", e); }
        }
        unbindService(powerConnection);
        powerBound = false;
        powerService = null;
    }

    private void setClusterBrightness(int level) {
        // The service indexes its level table without a bounds check; an unknown screen level comes as -1.
        if (powerService == null || level < 0 || level > 15) return;
        try { callPower(POWER_SET_CLUSTER, level); }
        catch (RemoteException | RuntimeException e) { Log.w("AtlasLauncher", "Cannot set the cluster brightness", e); }
    }

    // IPower calls take a callback, a level or nothing and return a level or nothing.
    private int callPower(int code, Object arg) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(POWER);
            if (arg instanceof IBinder) data.writeStrongBinder((IBinder) arg);
            else if (arg instanceof Integer) data.writeInt((Integer) arg);
            powerService.transact(code, data, reply, 0);
            reply.readException();
            return reply.dataAvail() >= 4 ? reply.readInt() : 0;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private void applyClimatePanel() {
        // The OneOS SystemUI plugin hides the climate panel for com.geely.* windows that hide navigation.
        // Sticky immersive keeps the flag after touches.
        int hide = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility() & ~hide;
        boolean hidden = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(CLIMATE_PANEL_HIDDEN, false);
        decor.setSystemUiVisibility(hidden ? flags | hide : flags);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) widgetRow.getLayoutParams();
        params.bottomMargin = hidden ? HIDDEN_CLIMATE_PANEL_PX : CLIMATE_PANEL_PX;
        widgetRow.setLayoutParams(params);
    }

    // Holding a finger anywhere on HOME starts editing after the delay chosen in settings; a view long press
    // would always wait the system timeout. Multi-finger gestures are not editing: a second finger cancels it.
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pressStartedEditing = false;
            if (!editingWidgets) {
                pressX = event.getRawX();
                pressY = event.getRawY();
                widgetRow.postDelayed(editByPress, editDelay());
            }
        } else if (action != MotionEvent.ACTION_MOVE || Math.hypot(event.getRawX() - pressX,
                event.getRawY() - pressY) > ViewConfiguration.get(this).getScaledTouchSlop()) {
            widgetRow.removeCallbacks(editByPress);
        }
        // The rest of the press that started editing must not click what is under the finger.
        return pressStartedEditing || super.dispatchTouchEvent(event);
    }

    private long editDelay() {
        int index = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(EDIT_DELAY, 0);
        return index > 0 && index < EDIT_DELAYS_MS.length ? EDIT_DELAYS_MS[index] : ViewConfiguration.getLongPressTimeout();
    }

    private void startEditingByPress() {
        pressStartedEditing = true;
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, pressX, pressY, 0);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
        widgetRow.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        setEditingWidgets(true);
    }

    private void setEditingWidgets(boolean editing) {
        editingWidgets = editing;
        if (editing && widgetsHidden) setWidgetsHidden(false);
        widgetControls.setVisibility(editing ? View.VISIBLE : View.GONE);
        settingsButton.setVisibility(editing ? View.VISIBLE : View.GONE);
        showWidgets();
    }

    private void showWallpaper() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int generation = ++wallpaperGeneration;
        if (wallpaperView.getDrawable() instanceof Animatable) ((Animatable) wallpaperView.getDrawable()).stop();
        shownWallpaper = prefs.getString(WALLPAPER, null);
        shownVideo = prefs.getString(VIDEO_WALLPAPER, null);
        videoWallpaper.setVideo(shownVideo);
        if (shownVideo != null) {
            wallpaperView.setImageResource(R.drawable.coastal_twilight);
            updateWallpaperStyle();
            return;
        }
        String saved = shownWallpaper;
        Uri uri = saved == null ? null : Uri.parse(saved);
        if (uri != null && !ContentResolver.SCHEME_ANDROID_RESOURCE.equals(uri.getScheme())) {
            if (wallpaperView.getDrawable() == null) wallpaperView.setImageResource(R.drawable.coastal_twilight);
            // A picked image comes from its document provider, which may take seconds to start right after boot.
            wallpaperLoader.execute(() -> {
                Drawable image = decodeWallpaperDrawable(this, uri, 2048);
                runOnUiThread(() -> {
                    // A newer choice may have replaced this one while it loaded.
                    if (!isDestroyed() && generation == wallpaperGeneration) {
                        applyWallpaper(image);
                        if (image == null) Toast.makeText(this, "Не удалось загрузить фон", Toast.LENGTH_SHORT).show();
                    }
                });
            });
            return;
        }
        applyWallpaper(uri == null ? null : decodeWallpaperDrawable(this, uri, 2048));
    }

    /** Android 9+ decodes GIF and animated WebP without a separate animation library. */
    private static Drawable decodeWallpaperDrawable(Context context, Uri uri, int maxSize) {
        if (Build.VERSION.SDK_INT >= 28 && !isPresetUri(uri.toString())) {
            try {
                return ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.getContentResolver(), uri),
                        (decoder, info, source) -> {
                            int longest = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                            if (longest > maxSize) decoder.setTargetSize(
                                    Math.max(1, info.getSize().getWidth() * maxSize / longest),
                                    Math.max(1, info.getSize().getHeight() * maxSize / longest));
                            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        });
            } catch (IOException | SecurityException e) {
                return null;
            }
        }
        Bitmap bitmap = decodeWallpaper(context, uri, maxSize);
        return bitmap == null ? null : new BitmapDrawable(context.getResources(), bitmap);
    }

    /** Returns null if the image cannot be read. */
    static Bitmap decodeWallpaper(Context context, Uri uri, int maxSize) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(stream, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(stream, null, options);
            }
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    private void applyWallpaper(Drawable image) {
        if (wallpaperView.getDrawable() instanceof Animatable) ((Animatable) wallpaperView.getDrawable()).stop();
        if (image != null) wallpaperView.setImageDrawable(image);
        else wallpaperView.setImageResource(R.drawable.coastal_twilight);
        if (Build.VERSION.SDK_INT >= 28 && image instanceof AnimatedImageDrawable)
            ((AnimatedImageDrawable) image).setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
        setWallpaperPlaying(homeVisible);
        updateWallpaperStyle();
    }

    private void setWallpaperPlaying(boolean playing) {
        videoWallpaper.setActive(playing);
        Drawable image = wallpaperView.getDrawable();
        if (image instanceof Animatable) {
            if (playing) ((Animatable) image).start();
            else ((Animatable) image).stop();
        }
    }

    private boolean animatedWallpaper() {
        return shownVideo != null || wallpaperView.getDrawable() instanceof Animatable;
    }

    private void updateWallpaperStyle() {
        // Animation frames can change brightness: keep white status icons against a stronger top scrim.
        wallpaperView.setForeground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(animatedWallpaper() ? 160 : 45, 2, 10, 24), Color.argb(8, 2, 10, 24), Color.argb(175, 2, 9, 21)}));
        updateStatusBarIcons();
    }

    private void updateStatusBarIcons() {
        if (animatedWallpaper()) {
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            return;
        }
        WindowInsets insets = wallpaperView.getRootWindowInsets();
        int width = wallpaperView.getWidth();
        if (insets == null || width == 0 || insets.getStableInsetTop() == 0) return;
        // Sample the scrimmed wallpaper under the status bar at 1/8 scale.
        Bitmap sample = Bitmap.createBitmap((width + 7) / 8, (insets.getStableInsetTop() + 7) / 8, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sample);
        canvas.scale(1 / 8f, 1 / 8f);
        wallpaperView.draw(canvas);
        int[] pixels = new int[sample.getWidth() * sample.getHeight()];
        sample.getPixels(pixels, 0, sample.getWidth(), 0, 0, sample.getWidth(), sample.getHeight());
        sample.recycle();
        // Launcher3 picks dark icons by WallpaperColors.HINT_SUPPORTS_DARK_TEXT; this is the Android 11 rule.
        float lightness = 0;
        int darkPixels = 0;
        for (int pixel : pixels) {
            int r = Color.red(pixel), g = Color.green(pixel), b = Color.blue(pixel);
            lightness += (Math.max(r, Math.max(g, b)) + Math.min(r, Math.min(g, b))) / 510f;
            if ((Color.luminance(pixel) + 0.05f) / 0.05f <= 6) darkPixels++;
        }
        boolean darkIcons = lightness / pixels.length > 0.75f && darkPixels < (int) (pixels.length * 0.025f);
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        decor.setSystemUiVisibility(darkIcons ? flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : flags);
    }

    private void updateDock() {
        if (favoritePanel == null) return;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int verticalPadding = prefs.getBoolean(DOCK_LABELS, true) ? 8 : 4;
        favoritePanel.setPadding(dp(12), dp(verticalPadding), dp(12), dp(verticalPadding));
        appsTile.setVisibility(prefs.getBoolean(DOCK_APPS, true) ? View.VISIBLE : View.GONE);
    }

    private int dockRequiredHeight(SharedPreferences prefs) {
        int iconSize = Math.max(40, Math.min(MAX_ICON_DP, prefs.getInt(DRAWER_ICON_SIZE, 64)));
        boolean showLabels = prefs.getBoolean(DOCK_LABELS, true);
        int padding = showLabels ? 8 : 4;
        Rect inset = dockInset();
        return iconSize + 4 * padding + (showLabels ? 44 : 0)
                + pxToDp(inset.top + inset.bottom);
    }

    private Rect dockInset() {
        return AppWidgetHostView.getDefaultPaddingForWidget(this, getComponentName(), null);
    }

    private void chooseWidget() {
        PackageManager pm = getPackageManager();
        List<AppWidgetProviderInfo> providers = widgetManager.getInstalledProviders();
        providers.forEach(this::fitFixedWidget);
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
            @Override public int getCount() { return providers.size() + 3; }
            @Override public Object getItem(int position) { return position; }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View old, ViewGroup parent) {
                if (position == 0) return widgetPreviewTile(clockPreview(), "Часы", "AtlasLauncher");
                if (position == 1) return widgetPreviewTile(dockPreview(), "Док приложений", "AtlasLauncher");
                if (position == 2) return widgetPreviewTile(volumePreview(), "Громкость", "AtlasLauncher");
                AppWidgetProviderInfo provider = providers.get(position - 3);
                ImageView image = new ImageView(HomeActivity.this);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setImageBitmap(previews[position - 3]);
                String details = provider.provider.getPackageName();
                try {
                    CharSequence appLabel = pm.getApplicationLabel(pm.getApplicationInfo(details, 0));
                    // The OneOS media widget app has an empty label.
                    if (!TextUtils.isEmpty(appLabel)) details = String.valueOf(appLabel);
                } catch (PackageManager.NameNotFoundException ignored) { }
                if (cells != null) {
                    Point padding = widgetPaddingDp(provider);
                    int width = pxToDp(Math.max(provider.minWidth, provider.minResizeWidth)) + padding.x;
                    int height = pxToDp(Math.max(provider.minHeight, provider.minResizeHeight)) + padding.y;
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
            else if (position == 2) addVolumeWidget();
            else addProviderWidget(providers.get(position - 3));
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

    private View volumePreview() {
        LinearLayout panel = (LinearLayout) BuiltInWidgets.createVolume(this, new WidgetPlacement(VOLUME_WIDGET_ID, 0, 0, 240, 80));
        // The tile itself picks the widget.
        for (int i = 0; i < panel.getChildCount(); i++) {
            panel.getChildAt(i).setClickable(false);
            panel.getChildAt(i).setLongClickable(false);
        }
        FrameLayout frame = new FrameLayout(this);
        frame.addView(panel, new FrameLayout.LayoutParams(-1, dp(80), Gravity.CENTER));
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
        Point slot = findGridSlot(grid, 0, 0, columns, rows, onPage(widgets, currentPage));
        if (slot == null) {
            Toast.makeText(this, "Недостаточно места для часов", Toast.LENGTH_SHORT).show();
            return;
        }
        setGridPlacement(placement, grid, slot, columns, rows);
        placement.page = currentPage;
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
        Point slot = findGridSlot(grid, x, y, columns, rows, onPage(widgets, currentPage));
        if (slot == null) return null;
        WidgetPlacement placement = new WidgetPlacement(DOCK_WIDGET_ID, 0, 0, 0, 0);
        setGridPlacement(placement, grid, slot, columns, rows);
        placement.page = currentPage;
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

    private void addVolumeWidget() {
        for (WidgetPlacement placement : widgets) {
            if (placement.id == VOLUME_WIDGET_ID) {
                Toast.makeText(this, "Громкость уже добавлена", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        WidgetGrid grid = widgetGrid();
        if (grid == null) return;
        int columns = Math.min(3, grid.columns);
        Point slot = findGridSlot(grid, 0, 0, columns, 1, onPage(widgets, currentPage));
        if (slot == null) {
            Toast.makeText(this, "Недостаточно места для громкости", Toast.LENGTH_SHORT).show();
            return;
        }
        WidgetPlacement placement = new WidgetPlacement(VOLUME_WIDGET_ID, 0, 0, 0, 0);
        setGridPlacement(placement, grid, slot, columns, 1);
        placement.page = currentPage;
        widgets.add(placement);
        saveWidgets();
        showWidgets();
    }

    private void finishAddingWidget() {
        if (pendingWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return;
        AppWidgetProviderInfo info = fitFixedWidget(widgetManager.getAppWidgetInfo(pendingWidgetId));
        if (info == null) { cancelPendingWidget(); return; }
        WidgetGrid grid = widgetGrid();
        if (grid == null) { widgetRow.post(this::finishAddingWidget); return; }
        Point padding = widgetPaddingDp(info);
        int widgetWidth = pxToDp(Math.max(info.minWidth, info.minResizeWidth)) + padding.x;
        int widgetHeight = pxToDp(Math.max(info.minHeight, info.minResizeHeight)) + padding.y;
        WidgetPlacement placement = new WidgetPlacement(pendingWidgetId, 0, 0, widgetWidth, widgetHeight);
        int columns = grid.span(widgetWidth, grid.cellWidth, grid.columns);
        int rows = grid.span(widgetHeight, grid.cellHeight, grid.rows);
        Point slot = findGridSlot(grid, 0, 0, columns, rows, onPage(widgets, currentPage));
        if (slot == null) {
            cancelPendingWidget();
            Toast.makeText(this, "Недостаточно места для виджета", Toast.LENGTH_SHORT).show();
            return;
        }
        setGridPlacement(placement, grid, slot, columns, rows);
        placement.page = currentPage;
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
        if (sourceListPopup != null) sourceListPopup.dismiss();
        widgetRow.removeAllViews();
        WidgetGrid grid = widgetGrid();
        if (grid == null) return;
        // Whole rows end at the climate panel; the leftover goes above them.
        widgetRow.setPadding(0, widgetRow.getHeight() - dp(grid.height), 0, 0);
        // Shows where a dragged or resized widget will land; stays below the widgets.
        dropTarget = new View(this);
        dropTarget.setVisibility(View.GONE);
        widgetRow.addView(dropTarget);
        boolean multiplePages = multiplePagesEnabled(this);
        // Disabled pages keep their placements and numbers until the user enables them again.
        boolean changed = multiplePages && !editingWidgets && compactPages();
        pages.clear();
        pageLayer = new FrameLayout(this);
        // A dragged widget stays in view while its page slides away.
        pageLayer.setClipChildren(false);
        pageLayer.setAlpha(widgetsHidden ? 0 : 1);
        pageLayer.setVisibility(widgetsHidden ? View.INVISIBLE : View.VISIBLE);
        widgetRow.addView(pageLayer, new FrameLayout.LayoutParams(-1, -1));
        int pageCount = pageCount();
        currentPage = Math.min(currentPage, pageCount - 1);
        for (int i = 0; i < pageCount; i++) addPage();
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
            if (!multiplePages && placement.page != 0) continue;
            boolean clockWidget = placement.id == CLOCK_WIDGET_ID;
            boolean dockWidget = placement.id == DOCK_WIDGET_ID;
            boolean volumeWidget = placement.id == VOLUME_WIDGET_ID;
            boolean builtIn = clockWidget || dockWidget || volumeWidget;
            AppWidgetProviderInfo info = builtIn ? null : fitFixedWidget(widgetManager.getAppWidgetInfo(placement.id));
            if (!builtIn && info == null) {
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
                placement.width = Math.min(availableWidth, clockWidget ? 4 * WIDGET_CELL_DP
                        : dockWidget || volumeWidget ? 3 * WIDGET_CELL_DP
                        : pxToDp(Math.max(info.minWidth, info.minResizeWidth)) + padding.x);
                placement.height = Math.min(availableHeight, clockWidget ? 2 * WIDGET_CELL_DP
                        : volumeWidget ? WIDGET_CELL_DP
                        : dockWidget ? grid.span(dockRequiredHeight(prefs), grid.cellHeight, grid.rows) * grid.cellHeight
                        : pxToDp(Math.max(info.minHeight, info.minResizeHeight)) + padding.y);
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
            Point minSize = info == null ? null : minWidgetSizeDp(info);
            int minColumns = clockWidget || volumeWidget ? 1 : dockWidget ? Math.min(2, grid.columns)
                    : grid.span(minSize.x, grid.cellWidth, grid.columns);
            int minRows = clockWidget || volumeWidget ? 1 : dockWidget ? grid.span(dockRequiredHeight(prefs), grid.cellHeight, grid.rows)
                    : grid.span(minSize.y, grid.cellHeight, grid.rows);
            int columns = Math.max(minColumns, grid.span(placement.width, grid.cellWidth, grid.columns));
            int rows = Math.max(minRows, grid.span(placement.height, grid.cellHeight, grid.rows));
            Point slot = null;
            for (int rowSpan = rows; rowSpan >= minRows && slot == null; rowSpan--) {
                for (int columnSpan = columns; columnSpan >= minColumns && slot == null; columnSpan--) {
                    slot = findGridSlot(grid, placement.x, placement.y, columnSpan, rowSpan, onPage(occupied, placement.page));
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
            View hostView;
            if (clockWidget) {
                hostView = BuiltInWidgets.createClock(this, placement);
                hostView.setTag(CLOCK_WIDGET_ID);
            } else if (dockWidget) {
                hostView = createDockWidget();
            } else if (volumeWidget) {
                hostView = BuiltInWidgets.createVolume(this, placement);
                hostView.setTag(VOLUME_WIDGET_ID);
            } else {
                AppWidgetHostView widgetView = widgetHost.createView(this, placement.id, info);
                widgetView.setAppWidget(placement.id, info);
                hostView = widgetView;
            }
            FrameLayout container = isMediaWidget(info)
                    ? new TapFrame(this, event -> {
                        if (!editingWidgets && touchesSourceSwitch(hostView, event)) showSourceList(hostView);
                    })
                    : new FrameLayout(this);
            FrameLayout.LayoutParams hostParams = new FrameLayout.LayoutParams(-1, -1);
            if (dockWidget || volumeWidget) {
                // Inset the dock and volume buttons like AppWidgetHostView insets regular widgets.
                Rect inset = dockInset();
                hostParams.setMargins(inset.left, inset.top, inset.right, inset.bottom);
            }
            container.addView(hostView, hostParams);
            if (editingWidgets) addWidgetEditControls(container, hostView, placement, info);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(placement.width), dp(placement.height));
            params.leftMargin = dp(placement.x);
            params.topMargin = dp(placement.y);
            pages.get(placement.page).addView(container, params);
            if (hostView instanceof AppWidgetHostView) updateWidgetSize((AppWidgetHostView) hostView, placement);
        }
        if (changed) saveWidgets();
        if (editingWidgets) for (int i = 0; i < pages.size(); i++) {
            int page = i;
            if (widgets.stream().anyMatch(placement -> placement.page == page)) continue;
            TextView empty = label("＋  Добавить виджет", 16, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(round(SURFACE, Color.TRANSPARENT, 18));
            empty.setOnClickListener(v -> chooseWidget());
            pages.get(i).addView(empty, new FrameLayout.LayoutParams(dp(220), dp(64), Gravity.CENTER));
        }
        updateIndicator(false);
    }

    private int pageCount() {
        if (!multiplePagesEnabled(this)) return 1;
        int count = 1;
        for (WidgetPlacement placement : widgets) count = Math.max(count, placement.page + 1);
        // Editing offers an empty page after the last one, to add or move widgets there.
        return editingWidgets ? Math.max(count + 1, currentPage + 1) : count;
    }

    /** Keep desktops created before the switch was introduced; new installations start with one desktop. */
    static boolean multiplePagesEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.contains(MULTIPLE_PAGES)) return prefs.getBoolean(MULTIPLE_PAGES, false);
        boolean enabled = false;
        try {
            JSONArray saved = new JSONArray(prefs.getString(WIDGETS, "[]"));
            for (int i = 0; i < saved.length(); i++) {
                if (saved.getJSONObject(i).optInt("page", 0) > 0) {
                    enabled = true;
                    break;
                }
            }
        } catch (JSONException e) {
            Log.w("AtlasLauncher", "Cannot read saved desktop pages", e);
        }
        prefs.edit().putBoolean(MULTIPLE_PAGES, enabled).apply();
        return enabled;
    }

    private void addPage() {
        FrameLayout page = new FrameLayout(this);
        page.setTranslationX((pages.size() - currentPage) * widgetRow.getWidth());
        pages.add(page);
        pageLayer.addView(page, new FrameLayout.LayoutParams(-1, -1));
    }

    /** Numbers the pages that hold widgets without gaps; returns whether a widget changed its page. */
    private boolean compactPages() {
        List<Integer> used = new ArrayList<>(new TreeSet<>(widgets.stream().map(placement -> placement.page)
                .collect(Collectors.toList())));
        boolean changed = false;
        for (WidgetPlacement placement : widgets) {
            int page = used.indexOf(placement.page);
            changed |= page != placement.page;
            placement.page = page;
        }
        // An empty page in view gives way to the page before it.
        int kept = 0;
        for (int i = 0; i < used.size(); i++) if (used.get(i) <= currentPage) kept = i;
        currentPage = kept;
        return changed;
    }

    private static List<WidgetPlacement> onPage(List<WidgetPlacement> placements, int page) {
        List<WidgetPlacement> result = new ArrayList<>();
        for (WidgetPlacement placement : placements) if (placement.page == page) result.add(placement);
        return result;
    }

    private void layoutPages(float offset) {
        for (int i = 0; i < pages.size(); i++) pages.get(i).setTranslationX((i - currentPage) * widgetRow.getWidth() + offset);
    }

    private void snapToPage(int page) {
        currentPage = Math.max(0, Math.min(pages.size() - 1, page));
        for (int i = 0; i < pages.size(); i++)
            pages.get(i).animate().translationX((i - currentPage) * widgetRow.getWidth()).setDuration(PAGE_SNAP_MS).start();
        updateIndicator(true);
    }

    /** Moves a dragged widget to another page: the pages slide, the widget stays under the finger. */
    private void moveWhileDragging(View container, WidgetPlacement placement, int page) {
        if (page < 0 || page >= pages.size()) return;
        // One empty page is enough to move to: going from one to another would keep adding pages.
        if (onPage(widgets, currentPage).stream().allMatch(other -> other == placement)
                && onPage(widgets, page).stream().allMatch(other -> other == placement)) return;
        // The page the widget leaves slides away with it inside; the widget slides back by as much.
        container.animate().translationX(container.getTranslationX() + (page - currentPage) * widgetRow.getWidth())
                .setDuration(PAGE_SNAP_MS).start();
        placement.page = page;
        if (page == pages.size() - 1) addPage();
        snapToPage(page);
    }

    /** Page dots: shown while editing and for a moment after the page changes. */
    private void updateIndicator(boolean flash) {
        pageIndicator.removeCallbacks(hideIndicator);
        pageIndicator.removeAllViews();
        for (int i = 0; i < pages.size(); i++) {
            View dot = new View(this);
            dot.setBackground(round(i == currentPage ? TEXT : Color.argb(100, 255, 255, 255), Color.TRANSPARENT, 4));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(8), dp(8));
            params.setMargins(dp(5), 0, dp(5), 0);
            pageIndicator.addView(dot, params);
        }
        boolean show = pages.size() > 1 && !widgetsHidden && (editingWidgets || flash);
        pageIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && !editingWidgets) pageIndicator.postDelayed(hideIndicator, 1500);
    }

    private void onEmptyTap() {
        if (editingWidgets) {
            setEditingWidgets(false);
            return;
        }
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(HIDE_BY_DOUBLE_TAP, false)) return;
        long now = SystemClock.uptimeMillis();
        // Clicks come on release, so the press of the second tap counts too.
        if (now - lastEmptyTap > ViewConfiguration.getDoubleTapTimeout() + ViewConfiguration.getTapTimeout()) {
            lastEmptyTap = now;
            return;
        }
        lastEmptyTap = 0;
        setWidgetsHidden(!widgetsHidden);
    }

    private void setWidgetsHidden(boolean hidden) {
        widgetsHidden = hidden;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(WIDGETS_HIDDEN, hidden).apply();
        FrameLayout layer = pageLayer;
        if (layer != null) {
            layer.animate().cancel();
            layer.setVisibility(View.VISIBLE);
            layer.animate().alpha(hidden ? 0 : 1).setDuration(200)
                    .withEndAction(() -> layer.setVisibility(hidden ? View.INVISIBLE : View.VISIBLE)).start();
        }
        updateIndicator(false);
    }

    private static boolean isMediaWidget(AppWidgetProviderInfo info) {
        return info != null && MEDIA_WIDGET_PACKAGE.equals(info.provider.getPackageName());
    }

    /**
     * OneOS widgets (media, phone, gallery) are drawn for Launcher3 as fixed-pixel cards, larger than their
     * declared minimum. Returns that size, or null for an ordinary resizable layout.
     */
    private Point fixedLayoutPx(AppWidgetProviderInfo info) {
        Point size = fixedLayouts.computeIfAbsent(info.provider, provider -> {
            try {
                Context context = createPackageContext(provider.getPackageName(), 0);
                ViewGroup.LayoutParams params = LayoutInflater.from(context)
                        .inflate(info.initialLayout, new FrameLayout(context), false).getLayoutParams();
                if (params.width > 0 && params.height > 0) return new Point(params.width, params.height);
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) { }
            return new Point();
        });
        return size.x > 0 ? size : null;
    }

    /** New fixed-size widgets get cells for the whole card; they may still be resized down to the declared minimum. */
    private AppWidgetProviderInfo fitFixedWidget(AppWidgetProviderInfo info) {
        Point size = info == null ? null : fixedLayoutPx(info);
        if (size != null) {
            info.minWidth = size.x;
            info.minHeight = size.y;
        }
        return info;
    }

    private boolean touchesSourceSwitch(View widget, MotionEvent event) {
        Resources resources;
        try { resources = getPackageManager().getResourcesForApplication(MEDIA_WIDGET_PACKAGE); }
        catch (PackageManager.NameNotFoundException e) { return false; }
        Rect bounds = new Rect();
        // The media widget sends its source-switch broadcast from these views.
        for (String name : new String[] {"rl_swicth_area", "iv_mediaWidget_switch", "iv_marker"}) {
            int id = resources.getIdentifier(name, "id", MEDIA_WIDGET_PACKAGE);
            View view = id == 0 ? null : widget.findViewById(id);
            // Visible bounds include the scale applied by FittedWidgetView; HOME fills the screen.
            if (view != null && view.isShown() && view.getGlobalVisibleRect(bounds)
                    && bounds.contains((int) event.getRawX(), (int) event.getRawY())) return true;
        }
        return false;
    }

    private void showSourceList(View widget) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int id = prefs.getInt(SOURCE_LIST_WIDGET, AppWidgetManager.INVALID_APPWIDGET_ID);
        AppWidgetProviderInfo info = id == AppWidgetManager.INVALID_APPWIDGET_ID ? null : widgetManager.getAppWidgetInfo(id);
        if (info == null) {
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) widgetHost.deleteAppWidgetId(id);
            id = widgetHost.allocateAppWidgetId();
            prefs.edit().putInt(SOURCE_LIST_WIDGET, id).apply();
            if (!widgetManager.bindAppWidgetIdIfAllowed(id, SOURCE_LIST_PROVIDER)) {
                // Once binding is allowed, the next tap opens the list.
                Intent bind = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
                bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, SOURCE_LIST_PROVIDER);
                try { startActivityForResult(bind, BIND_SOURCE_LIST); }
                catch (ActivityNotFoundException | SecurityException e) { Toast.makeText(this, "Привязка виджета недоступна", Toast.LENGTH_SHORT).show(); }
                return;
            }
            info = widgetManager.getAppWidgetInfo(id);
            if (info == null) return;
        }
        if (sourceListPopup != null) sourceListPopup.dismiss();
        AppWidgetHostView list = widgetHost.createView(this, id, info);
        list.setPadding(0, 0, 0, 0);
        PopupWindow popup = new PopupWindow(this);
        // Choosing a source does not close the list: the widget reports that to Launcher3 only.
        TapFrame content = new TapFrame(this, event -> widgetRow.postDelayed(popup::dismiss, 300));
        content.addView(list, new FrameLayout.LayoutParams(-2, -2));
        popup.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        // Like Launcher3, anchor the list to the card itself; scale it to the card's (possibly scaled) width.
        View card = widget instanceof ViewGroup && ((ViewGroup) widget).getChildCount() > 0
                ? ((ViewGroup) widget).getChildAt(0) : widget;
        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        Rect bounds = new Rect();
        card.getGlobalVisibleRect(bounds);
        popup.setContentView(new ScaledFrame(this, content, (float) bounds.width() / content.getMeasuredWidth()));
        // Bottom gravity keeps the list on the card while its rows load and it grows upwards.
        popup.showAtLocation(widgetRow, Gravity.BOTTOM | Gravity.START, bounds.left,
                getWindow().getDecorView().getHeight() - bounds.top);
        sourceListPopup = popup;
    }

    private void compactClockWidget(WidgetPlacement placement, WidgetGrid grid) {
        LinearLayout clock = (LinearLayout) BuiltInWidgets.createClock(this, placement);
        TextClock time = (TextClock) ((LinearLayout) clock.getChildAt(0)).getChildAt(0);
        TextClock period = (TextClock) ((LinearLayout) clock.getChildAt(0)).getChildAt(1);
        TextClock date = (TextClock) clock.getChildAt(1);
        float contentWidth = BuiltInWidgets.clockTimeWidth(this, time, period);
        if (date.getVisibility() == View.VISIBLE)
            contentWidth = Math.max(contentWidth, date.getPaint().measureText(date.getText().toString()));
        int columns = grid.span((int) Math.ceil(contentWidth / getResources().getDisplayMetrics().density) + 16,
                grid.cellWidth, grid.columns);
        placement.width = Math.min(placement.width, columns * grid.cellWidth);
    }

    private void updateDesktopClock() {
        View desktopClock = widgetRow.findViewWithTag(CLOCK_WIDGET_ID);
        if (desktopClock == null) return;
        for (WidgetPlacement placement : widgets) {
            if (placement.id == CLOCK_WIDGET_ID) {
                BuiltInWidgets.updateClock(this, desktopClock, placement);
                return;
            }
        }
    }

    private void updateDesktopVolume() {
        View desktopVolume = widgetRow.findViewWithTag(VOLUME_WIDGET_ID);
        if (desktopVolume == null) return;
        for (WidgetPlacement placement : widgets) {
            if (placement.id == VOLUME_WIDGET_ID) {
                BuiltInWidgets.updateVolume(this, desktopVolume, placement);
                return;
            }
        }
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
        // A fixed-size layout is scaled into the padded area, so its own size alone picks the cells.
        if (fixedLayoutPx(info) != null) return new Point(0, 0);
        Rect padding = AppWidgetHostView.getDefaultPaddingForWidget(this, info.provider, null);
        return new Point(pxToDp(padding.left + padding.right), pxToDp(padding.top + padding.bottom));
    }

    /**
     * Any widget may be enlarged. A fixed-size layout is scaled, so it also shrinks down to one cell; other widgets
     * keep their declared minimum along an axis they resize and their initial size along an axis they do not.
     */
    private Point minWidgetSizeDp(AppWidgetProviderInfo info) {
        if (fixedLayoutPx(info) != null) return new Point(WIDGET_CELL_DP, WIDGET_CELL_DP);
        Point padding = widgetPaddingDp(info);
        int width = (info.resizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
                ? Math.max(56, pxToDp(info.minResizeWidth)) : pxToDp(info.minWidth);
        int height = (info.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0
                ? pxToDp(info.minResizeHeight) : pxToDp(info.minHeight);
        return new Point(width + padding.x, height + padding.y);
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
                ? "док приложений" : placement.id == VOLUME_WIDGET_ID ? "громкость"
                : String.valueOf(info.loadLabel(getPackageManager()));
        dragSurface.setContentDescription("Перетащить " + title);
        dragSurface.setOnTouchListener(widgetTouch(container, hostView, placement, info, false));
        container.addView(dragSurface, new FrameLayout.LayoutParams(-1, -1));
        if (info == null || info.configure != null) {
            Button settings = button("⚙");
            settings.setContentDescription("Настроить " + title);
            settings.setOnClickListener(v -> {
                if (placement.id == CLOCK_WIDGET_ID) SettingsActivity.open(this, SettingsActivity.PAGE_CLOCK);
                else if (placement.id == DOCK_WIDGET_ID) SettingsActivity.open(this, SettingsActivity.PAGE_DOCK);
                else if (placement.id == VOLUME_WIDGET_ID) SettingsActivity.open(this, SettingsActivity.PAGE_VOLUME);
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
        Button resize = button("↘");
        resize.setContentDescription("Изменить размер " + title);
        resize.setOnTouchListener(widgetTouch(container, hostView, placement, info, true));
        container.addView(resize, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM | Gravity.RIGHT));
    }

    private View.OnTouchListener widgetTouch(FrameLayout container, View hostView,
                                             WidgetPlacement placement,
                                             AppWidgetProviderInfo info, boolean resizing) {
        Point minSize = info == null ? null : minWidgetSizeDp(info);
        int minWidth = info == null ? (placement.id == DOCK_WIDGET_ID ? 2 : 1) * WIDGET_CELL_DP : minSize.x;
        int minHeight = info == null ? placement.id == DOCK_WIDGET_ID
                ? dockRequiredHeight(getSharedPreferences(PREFS, MODE_PRIVATE)) : WIDGET_CELL_DP : minSize.y;
        return new View.OnTouchListener() {
            float startX;
            float startY;
            int originalX;
            int originalY;
            int originalWidth;
            int originalHeight;
            int originalClockSize;
            int originalPage;
            int edgeDirection;
            boolean movedPage;
            final Runnable edgeMove = new Runnable() {
                @Override public void run() {
                    movedPage |= currentPage + edgeDirection >= 0;
                    moveWhileDragging(container, placement, currentPage + edgeDirection);
                    widgetRow.postDelayed(this, PAGE_EDGE_REPEAT_MS);
                }
            };

            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    startX = event.getRawX();
                    startY = event.getRawY();
                    originalX = placement.x;
                    originalY = placement.y;
                    originalWidth = placement.width;
                    originalHeight = placement.height;
                    originalClockSize = placement.clockSize;
                    originalPage = placement.page;
                    edgeDirection = 0;
                    movedPage = false;
                    // Page swipes must not take the drag; the widget is drawn above the page it may move to.
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    ((View) container.getParent()).setTranslationZ(1);
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    int dx = pxToDp(event.getRawX() - startX);
                    int dy = pxToDp(event.getRawY() - startY);
                    int areaWidth = pxToDp(widgetRow.getWidth());
                    int areaHeight = pxToDp(widgetRow.getHeight() - widgetRow.getPaddingTop());
                    if (resizing) {
                        placement.width = Math.min(areaWidth - placement.x, Math.max(minWidth, originalWidth + dx));
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
                        BuiltInWidgets.updateClock(HomeActivity.this, hostView, placement);
                    }
                    if (resizing && placement.id == VOLUME_WIDGET_ID) BuiltInWidgets.updateVolume(HomeActivity.this, hostView, placement);
                    if (!resizing && multiplePagesEnabled(HomeActivity.this)) {
                        int[] row = new int[2];
                        widgetRow.getLocationOnScreen(row);
                        float x = event.getRawX() - row[0];
                        int direction = x < dp(PAGE_EDGE_DP) ? -1 : x > widgetRow.getWidth() - dp(PAGE_EDGE_DP) ? 1 : 0;
                        if (direction != edgeDirection) {
                            edgeDirection = direction;
                            widgetRow.removeCallbacks(edgeMove);
                            if (direction != 0) widgetRow.postDelayed(edgeMove, PAGE_EDGE_DELAY_MS);
                        }
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
                    widgetRow.removeCallbacks(edgeMove);
                    WidgetGrid grid = widgetGrid();
                    WidgetPlacement target = event.getActionMasked() == MotionEvent.ACTION_CANCEL || grid == null
                            ? null : snappedPlacement(grid);
                    if (target == null) {
                        placement.x = originalX;
                        placement.y = originalY;
                        placement.width = originalWidth;
                        placement.height = originalHeight;
                        placement.page = originalPage;
                        currentPage = originalPage;
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
                        else if (placement.id == CLOCK_WIDGET_ID) BuiltInWidgets.updateClock(HomeActivity.this, hostView, placement);
                        else if (placement.id == VOLUME_WIDGET_ID) BuiltInWidgets.updateVolume(HomeActivity.this, hostView, placement);
                    }
                    ((View) container.getParent()).setTranslationZ(0);
                    // The widget belongs to the page it was dropped on; views are rebuilt once this touch is over.
                    if (movedPage) widgetRow.post(HomeActivity.this::showWidgets);
                    return true;
                }
                return true;
            }

            // The grid position the widget takes on release, or null when it returns to its original place.
            private WidgetPlacement snappedPlacement(WidgetGrid grid) {
                List<WidgetPlacement> occupied = onPage(widgets, placement.page);
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
        loadFavorites(prefs);
        try {
            JSONArray savedWidgets = new JSONArray(prefs.getString(WIDGETS, "[]"));
            for (int i = 0; i < savedWidgets.length(); i++) {
                Object saved = savedWidgets.get(i);
                if (saved instanceof JSONObject) {
                    JSONObject item = (JSONObject) saved;
                    WidgetPlacement placement = new WidgetPlacement(item.getInt("id"), item.getInt("x"), item.getInt("y"),
                            item.getInt("width"), item.getInt("height"));
                    placement.clockSize = item.optInt("clockSize", 0);
                    placement.page = item.optInt("page", 0);
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

    private void loadFavorites(SharedPreferences prefs) {
        favorites.clear();
        try {
            JSONArray saved = new JSONArray(prefs.getString(FAVORITES, "[]"));
            for (int i = 0; i < saved.length(); i++) favorites.add(saved.getString(i));
        } catch (JSONException e) {
            favorites.clear();
        }
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
                item.put("page", widget.page);
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

    /** The widget area: a horizontal swipe that nothing under the finger scrolls itself moves between pages. */
    private final class PagedRow extends FrameLayout {
        private final int touchSlop = ViewConfiguration.get(HomeActivity.this).getScaledTouchSlop();
        private final int minFling = ViewConfiguration.get(HomeActivity.this).getScaledMinimumFlingVelocity() * 4;
        private final VelocityTracker velocity = VelocityTracker.obtain();
        private float downX;
        private float downY;
        private boolean paging;
        private boolean blocked;

        PagedRow(Context context) {
            super(context);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            return trackPaging(event);
        }

        @Override public boolean performClick() {
            return super.performClick();
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            boolean wasPaging = paging;
            if (!trackPaging(event)) return super.onTouchEvent(event);
            if (!wasPaging) {
                // A swipe that started on empty space does not click it.
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.onTouchEvent(cancel);
                cancel.recycle();
            }
            return true;
        }

        /** Returns true once the gesture is a page swipe; the rest of it then belongs to the pages. */
        private boolean trackPaging(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                velocity.clear();
                downX = event.getX();
                downY = event.getY();
                paging = false;
                blocked = pages.size() < 2 || widgetsHidden;
            }
            velocity.addMovement(event);
            if (action == MotionEvent.ACTION_POINTER_DOWN) {
                // Multi-finger gestures are not page swipes.
                if (paging && !blocked) snapToPage(currentPage);
                blocked = true;
            } else if (action == MotionEvent.ACTION_MOVE && !blocked) {
                float dx = event.getX() - downX;
                if (!paging) {
                    if (Math.abs(dx) <= touchSlop || Math.abs(dx) <= Math.abs(event.getY() - downY)) return false;
                    if (canScroll(this, downX, downY, dx)) {
                        blocked = true;
                        return false;
                    }
                    paging = true;
                    downX += Math.signum(dx) * touchSlop;
                    dx = event.getX() - downX;
                }
                // Past the first and the last page the pages follow the finger only a little.
                if (dx > 0 && currentPage == 0 || dx < 0 && currentPage == pages.size() - 1) dx /= 3;
                layoutPages(dx);
            } else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && paging && !blocked) {
                velocity.computeCurrentVelocity(1000);
                float dx = event.getX() - downX;
                int page = currentPage;
                if (action == MotionEvent.ACTION_UP && (dx < -getWidth() / 4f || velocity.getXVelocity() < -minFling)) page++;
                else if (action == MotionEvent.ACTION_UP && (dx > getWidth() / 4f || velocity.getXVelocity() > minFling)) page--;
                snapToPage(page);
            }
            return paging;
        }
    }

    private static boolean canScroll(View view, float x, float y, float dx) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                float childX = x + group.getScrollX() - child.getLeft() - child.getTranslationX();
                float childY = y + group.getScrollY() - child.getTop() - child.getTranslationY();
                if (child.getVisibility() == View.VISIBLE && childX >= 0 && childY >= 0 && childX < child.getWidth()
                        && childY < child.getHeight() && canScroll(child, childX, childY, dx)) return true;
            }
        }
        return view.canScrollHorizontally(dx > 0 ? -1 : 1);
    }

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

    /** Scales a fixed-size widget layout into the padded area instead of cropping it, keeping its proportions. */
    private static final class FittedWidgetView extends AppWidgetHostView {
        FittedWidgetView(Context context) {
            super(context);
        }

        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            View content = getChildCount() > 0 ? getChildAt(0) : null;
            ViewGroup.LayoutParams params = content == null ? null : content.getLayoutParams();
            if (params == null || params.width <= 0 || params.height <= 0) return;
            int width = getWidth() - getPaddingLeft() - getPaddingRight();
            int height = getHeight() - getPaddingTop() - getPaddingBottom();
            float scale = Math.min((float) width / params.width, (float) height / params.height);
            content.setPivotX(0);
            content.setPivotY(0);
            content.setScaleX(scale);
            content.setScaleY(scale);
            // The host lays the card out centred; move it into the padded area. The top edge stays in line
            // with neighbouring widgets, any spare height goes below.
            content.setTranslationX(getPaddingLeft() - content.getLeft() + (width - params.width * scale) / 2);
            content.setTranslationY(getPaddingTop() - content.getTop());
        }
    }

    /** Draws its only child scaled and takes the scaled size, so a wrap-content window fits it exactly. */
    private static final class ScaledFrame extends FrameLayout {
        private final float scale;

        ScaledFrame(Context context, View child, float scale) {
            super(context);
            this.scale = scale;
            child.setPivotX(0);
            child.setPivotY(0);
            child.setScaleX(scale);
            child.setScaleY(scale);
            addView(child, new LayoutParams(-2, -2));
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            View child = getChildAt(0);
            // A list measured with an unspecified height shows one row, so bound it by the available height.
            child.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec((int) (MeasureSpec.getSize(heightSpec) / scale), MeasureSpec.AT_MOST));
            setMeasuredDimension(Math.round(child.getMeasuredWidth() * scale), Math.round(child.getMeasuredHeight() * scale));
        }
    }

    /** Reports taps without taking touches from its children. */
    private static final class TapFrame extends FrameLayout {
        private final Consumer<MotionEvent> onTap;
        private final int touchSlop;
        private float downX;
        private float downY;

        TapFrame(Context context, Consumer<MotionEvent> onTap) {
            super(context);
            this.onTap = onTap;
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    && Math.hypot(event.getX() - downX, event.getY() - downY) < touchSlop) {
                onTap.accept(event);
            }
            return false;
        }
    }

    static final class WidgetPlacement {
        final int id;
        int x;
        int y;
        int width;
        int height;
        int clockSize;
        int page;

        WidgetPlacement(int id, int x, int y, int width, int height) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    static final class AppEntry {
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
