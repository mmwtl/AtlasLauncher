package com.mmwtl.atlaslauncher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * The OneOS climate panel opens com.android.launcher3/.Launcher explicitly instead of the current HOME.
 * This service returns to AtlasLauncher when that happens. Its config limits events to Launcher3, and while
 * the feature is off in AtlasLauncher settings it requests no event types, so the system sends it nothing.
 */
public final class StockHomeRedirectService extends AccessibilityService {
    static final String ENABLED = "stock_home_redirect";
    private static final String STOCK_LAUNCHER = "com.android.launcher3.Launcher";
    // Set before AtlasLauncher opens the stock launcher on purpose.
    static volatile long allowStockLauncherUntil;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (prefs, key) -> {
        if (ENABLED.equals(key)) applyEnabled(prefs);
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
        AccessibilityServiceInfo info = getServiceInfo();
        info.eventTypes = prefs.getBoolean(ENABLED, false) ? AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED : 0;
        setServiceInfo(info);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!STOCK_LAUNCHER.equals(String.valueOf(event.getClassName()))) return;
        if (SystemClock.elapsedRealtime() < allowStockLauncherUntil) return;
        // After a rollback to the stock HOME the panel button must keep opening it.
        ResolveInfo home = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        if (home == null || !getPackageName().equals(home.activityInfo.packageName)) return;
        startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @Override public void onInterrupt() { }
}
