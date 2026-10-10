package com.mmwtl.atlaslauncher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

/**
 * The OneOS climate panel opens com.android.launcher3/.Launcher explicitly instead of the current HOME, both for
 * Home and for All apps; only an intent extra tells them apart. This service opens AtlasLauncher right on the click
 * of the panel's Home or All apps button, so Launcher3 is not shown, and as a fallback returns to AtlasLauncher
 * when the Launcher3 window appears. While Launcher3 stays on the hidden display, where HOME preloads it, All apps
 * opens the catalog over the current app without AtlasLauncher. Its config limits events
 * to Launcher3 and the SystemUI plugin, and it requests only the event types of the features enabled in
 * AtlasLauncher settings, so with both off the system sends it nothing.
 */
public final class StockHomeRedirectService extends AccessibilityService {
    static final String ENABLED = "stock_home_redirect";
    static final String ALL_APPS_ENABLED = "stock_all_apps_redirect";
    static final String EXTRA_OPEN_ALL_APPS = "open_all_apps";
    private static final String STOCK_PACKAGE = "com.android.launcher3";
    private static final String STOCK_LAUNCHER = STOCK_PACKAGE + ".Launcher";
    // The plugin pins its All apps item first in this list and does not let the user move it.
    private static final String PANEL_SUB_LIST = ":id/rv_nav_sub";
    // Home is the first, fixed item of this list (dock_main, item_position 0).
    private static final String PANEL_MAIN_LIST = ":id/rv_nav_main";
    private static final long ALL_APPS_CLICK_TIMEOUT_MS = 2000;
    // Set before AtlasLauncher opens the stock launcher on purpose.
    static volatile long allowStockLauncherUntil;
    private long allAppsOpenedAt;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (prefs, key) -> {
        if (ENABLED.equals(key) || ALL_APPS_ENABLED.equals(key)) applyEnabled(prefs);
    };

    @Override protected void onServiceConnected() {
        SharedPreferences prefs = getSharedPreferences(HomeActivity.PREFS, MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);
        applyEnabled(prefs);
    }

    @Override public boolean onUnbind(Intent intent) {
        getSharedPreferences(HomeActivity.PREFS, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener);
        return super.onUnbind(intent);
    }

    private void applyEnabled(SharedPreferences prefs) {
        boolean home = prefs.getBoolean(ENABLED, false);
        boolean allApps = prefs.getBoolean(ALL_APPS_ENABLED, false);
        AccessibilityServiceInfo info = getServiceInfo();
        info.eventTypes = home || allApps
                ? AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_VIEW_CLICKED : 0;
        // Windows of other displays are listed only with this flag; All apps needs them to find Launcher3.
        if (allApps) info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        else info.flags &= ~AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        SharedPreferences prefs = getSharedPreferences(HomeActivity.PREFS, MODE_PRIVATE);
        long now = SystemClock.elapsedRealtime();
        // The plugin has already asked to start Launcher3; starting AtlasLauncher right away puts it on top before
        // Launcher3 is drawn. The window check below stays as a fallback.
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            AccessibilityNodeInfo item = event.getSource();
            if (isFirstItemOf(item, PANEL_SUB_LIST) && prefs.getBoolean(ALL_APPS_ENABLED, false)) {
                allAppsOpenedAt = now;
                openAtlasIfHome(true);
            } else if (isFirstItemOf(item, PANEL_MAIN_LIST) && prefs.getBoolean(ENABLED, false)) {
                openAtlasIfHome(false);
            }
            return;
        }
        if (!STOCK_LAUNCHER.equals(String.valueOf(event.getClassName()))) return;
        if (now < allowStockLauncherUntil || !prefs.getBoolean(ENABLED, false)) return;
        // Opening AtlasLauncher again would close the All apps view just opened, or start the chosen activity twice.
        if (now - allAppsOpenedAt < ALL_APPS_CLICK_TIMEOUT_MS) return;
        openAtlasIfHome(false);
    }

    private void openAtlasIfHome(boolean allApps) {
        // After a rollback to the stock HOME the panel buttons must keep opening it.
        ResolveInfo home = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        if (home == null || !getPackageName().equals(home.activityInfo.packageName)) return;
        // The click brings Launcher3 forward on the display it is on. On the hidden one nobody sees it, so nothing
        // has to cover it; on the main screen only AtlasLauncher does.
        if (allApps && isStockLauncherHidden()) {
            AllAppsActivity.open(this);
            return;
        }
        // Launcher3 now covers an open catalog, which then closes itself; a second press only returns to HOME.
        startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_OPEN_ALL_APPS, allApps && !AllAppsActivity.shown));
    }

    private boolean isStockLauncherHidden() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        SparseArray<List<AccessibilityWindowInfo>> displays = getWindowsOnAllDisplays();
        for (int i = 0; i < displays.size(); i++) {
            if (displays.keyAt(i) == Display.DEFAULT_DISPLAY) continue;
            for (AccessibilityWindowInfo window : displays.valueAt(i)) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null && STOCK_PACKAGE.equals(String.valueOf(root.getPackageName()))) return true;
            }
        }
        return false;
    }

    private static boolean isFirstItemOf(AccessibilityNodeInfo item, String listId) {
        if (item == null) return false;
        AccessibilityNodeInfo list = item.getParent();
        if (list == null) return false;
        String id = list.getViewIdResourceName();
        return id != null && id.endsWith(listId) && item.equals(list.getChild(0));
    }

    @Override public void onInterrupt() { }
}
