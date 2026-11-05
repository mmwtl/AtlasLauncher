package com.mmwtl.atlaslauncher;

import android.content.Context;
import android.content.res.Configuration;

/**
 * The launcher's day or night theme: the head unit's mode (OneOS switches it with the headlights)
 * or one fixed in the settings. A fixed theme overrides the night qualifier of the resources, so
 * values-night and every check of the configuration follow it.
 */
final class ThemeMode {
    /** Index of the settings choice: {@link #AUTO}, {@link #DAY} or {@link #NIGHT}. */
    static final String KEY = "theme_mode";
    static final int AUTO = 0;
    static final int DAY = 1;
    static final int NIGHT = 2;

    private ThemeMode() {
    }

    @SuppressWarnings("deprecation")
    static int get(Context context) {
        // The gesture service runs in its own process; MULTI_PROCESS rereads the file the settings changed.
        return context.getSharedPreferences(HomeActivity.PREFS, Context.MODE_MULTI_PROCESS).getInt(KEY, AUTO);
    }

    /** The base context, with the night qualifier of a fixed theme. */
    static Context apply(Context base) {
        int mode = get(base);
        return mode == AUTO ? base : withNight(base, mode == NIGHT);
    }

    /** Whether the launcher is drawn in the night theme now. */
    static boolean isNight(Context context) {
        int mode = get(context);
        if (mode != AUTO) return mode == NIGHT;
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /** A context whose resources are the night or the day ones whatever the head unit's mode. */
    static Context withNight(Context base, boolean night) {
        // Only the night bits are set: the other fields stay undefined and keep following the system.
        Configuration override = new Configuration();
        override.uiMode = night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        return base.createConfigurationContext(override);
    }
}
