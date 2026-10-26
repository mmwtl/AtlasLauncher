package com.mmwtl.atlaslauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Lets other apps hide, show or toggle the climate panel on HOME; HomeActivity applies the setting. */
public class ClimatePanelReceiver extends BroadcastReceiver {
    static final String ACTION_HIDE = "com.geely.atlaslauncher.action.HIDE_CLIMATE_PANEL";
    static final String ACTION_SHOW = "com.geely.atlaslauncher.action.SHOW_CLIMATE_PANEL";
    static final String ACTION_TOGGLE = "com.geely.atlaslauncher.action.TOGGLE_CLIMATE_PANEL";

    @Override public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(HomeActivity.PREFS, Context.MODE_PRIVATE);
        boolean hidden;
        switch (String.valueOf(intent.getAction())) {
            case ACTION_HIDE: hidden = true; break;
            case ACTION_SHOW: hidden = false; break;
            case ACTION_TOGGLE: hidden = !prefs.getBoolean(HomeActivity.CLIMATE_PANEL_HIDDEN, false); break;
            default: return;
        }
        prefs.edit().putBoolean(HomeActivity.CLIMATE_PANEL_HIDDEN, hidden).apply();
    }
}
