package com.mmwtl.atlaslauncher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * The OneOS climate panel opens com.android.launcher3/.Launcher explicitly instead of the current HOME, both for
 * Home and for All apps; only an intent extra tells them apart. This service returns to AtlasLauncher when that
 * happens and recognizes All apps by the preceding click on the panel's All apps button. Its config limits events
 * to Launcher3 and the SystemUI plugin, and it requests only the event types of the features enabled in
 * AtlasLauncher settings, so with both off the system sends it nothing.
 */
public final class StockHomeRedirectService extends AccessibilityService {
    static final String ENABLED = "stock_home_redirect";
    static final String ALL_APPS_ENABLED = "stock_all_apps_redirect";
    static final String EXTRA_OPEN_ALL_APPS = "open_all_apps";
    private static final String STOCK_LAUNCHER = "com.android.launcher3.Launcher";
    // The plugin pins its All apps item first in this list and does not let the user move it.
    private static final String PANEL_SUB_LIST = ":id/rv_nav_sub";
    // Home is the first, fixed item of this list (dock_main, item_position 0).
    private static final String PANEL_MAIN_LIST = ":id/rv_nav_main";
    private static final long ALL_APPS_CLICK_TIMEOUT_MS = 2000;
    // Set before AtlasLauncher opens the stock launcher on purpose.
    static volatile long allowStockLauncherUntil;
    private long allAppsClickedAt;

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
        info.eventTypes = (home || allApps ? AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED : 0)
                | (home || allApps ? AccessibilityEvent.TYPE_VIEW_CLICKED : 0);
        setServiceInfo(info);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            AccessibilityNodeInfo item = event.getSource();
            if (isFirstItemOf(item, PANEL_SUB_LIST)) {
                allAppsClickedAt = SystemClock.elapsedRealtime();
            } else if (isFirstItemOf(item, PANEL_MAIN_LIST)
                    && getSharedPreferences(HomeActivity.PREFS, MODE_PRIVATE).getBoolean(ENABLED, false)) {
                // The plugin has already asked to start Launcher3; starting AtlasLauncher right away puts it on
                // top before Launcher3 is drawn. The window check below stays as a fallback.
                openAtlasIfHome(false);
            }
            return;
        }
        if (!STOCK_LAUNCHER.equals(String.valueOf(event.getClassName()))) return;
        long now = SystemClock.elapsedRealtime();
        if (now < allowStockLauncherUntil) return;
        boolean allApps = now - allAppsClickedAt < ALL_APPS_CLICK_TIMEOUT_MS;
        allAppsClickedAt = 0;
        SharedPreferences prefs = getSharedPreferences(HomeActivity.PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean(allApps ? ALL_APPS_ENABLED : ENABLED, false)) return;
        openAtlasIfHome(allApps);
    }

    private void openAtlasIfHome(boolean allApps) {
        // After a rollback to the stock HOME the panel buttons must keep opening it.
        ResolveInfo home = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        if (home == null || !getPackageName().equals(home.activityInfo.packageName)) return;
        startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_OPEN_ALL_APPS, allApps));
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
