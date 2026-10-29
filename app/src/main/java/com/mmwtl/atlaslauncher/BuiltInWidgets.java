package com.mmwtl.atlaslauncher;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.appwidget.AppWidgetHostView;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Proxy;
import java.util.concurrent.Executor;

/** The clock, volume and Wi-Fi/Bluetooth/hotspot widgets built into HOME; settings show them as previews. */
final class BuiltInWidgets {
    private static final int TEXT = Color.rgb(250, 252, 255);
    private static final int MUTED = Color.rgb(188, 204, 224);
    // Active tiles of the Atlas widgets (AtlasClimateWidget palette "Atlas").
    private static final int ATLAS_ACTIVE = Color.rgb(120, 147, 160);
    static final String CLOCK_FORMAT = "clock_format";
    static final String CLOCK_WEIGHT = "clock_weight";
    static final String CLOCK_FONT = "clock_font";
    static final String CLOCK_DATE = "clock_date";
    static final String VOLUME_MUTE = "volume_mute";
    static final String VOLUME_ICONS = "volume_icons";
    static final String VOLUME_JOINED = "volume_joined";
    static final String CONNECTIVITY_WIFI = "connectivity_wifi";
    static final String CONNECTIVITY_BLUETOOTH = "connectivity_bluetooth";
    static final String CONNECTIVITY_HOTSPOT = "connectivity_hotspot";
    static final String CONNECTIVITY_STYLE = "connectivity_style";
    // Opacity of the volume and Wi-Fi/Bluetooth cards in percent; switched-on tiles stay opaque. Each kind has its own
    // key: the first widget of every kind keeps the plain one.
    static final String VOLUME_OPACITY = "volume_opacity";
    static final String CONNECTIVITY_OPACITY = "connectivity_opacity";
    static final int STYLE_ICONS = 0;
    static final int STYLE_STATE = 1;
    static final int STYLE_NETWORK = 2;
    // Hidden WifiManager constants: the sticky hotspot state broadcast and its states.
    static final String WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED";
    private static final int WIFI_AP_STATE_DISABLING = 10;
    private static final int WIFI_AP_STATE_ENABLING = 12;
    private static final int WIFI_AP_STATE_ENABLED = 13;
    // Volume up, volume down for each icon style; mute uses one icon in every style.
    private static final int[][] VOLUME_ICON_STYLES = {
            {R.drawable.ic_volume_up, R.drawable.ic_volume_down},
            {R.drawable.ic_plus, R.drawable.ic_minus},
            {R.drawable.ic_chevron_up, R.drawable.ic_chevron_down}
    };

    private BuiltInWidgets() {
    }

    /** Settings key of a widget: the first of its kind keeps the plain key, later ones add their ID. */
    static String key(String base, int widgetId) {
        return widgetId > -HomeActivity.BUILT_IN_STEP ? base : base + widgetId;
    }

    static String baseKey(String key) {
        return key.replaceFirst("-\\d+$", "");
    }

    /** The card color of a widget with its opacity setting. */
    private static int surface(Context context, SharedPreferences prefs, String opacityKey, int widgetId) {
        int opacity = Math.max(0, Math.min(100, prefs.getInt(key(opacityKey, widgetId), 100)));
        int color = context.getColor(R.color.neutral_surface);
        return Color.argb(Color.alpha(color) * opacity / 100, Color.red(color), Color.green(color), Color.blue(color));
    }

    static View createClock(Context context, HomeActivity.WidgetPlacement placement) {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setGravity(Gravity.CENTER);
        LinearLayout timeLine = new LinearLayout(context);
        timeLine.setGravity(Gravity.CENTER);
        TextClock clock = new TextClock(context);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("h:mm");
        clock.setTextColor(TEXT);
        clock.setGravity(Gravity.CENTER);
        timeLine.addView(clock);
        TextClock period = new TextClock(context);
        period.setFormat24Hour("a");
        period.setFormat12Hour("a");
        period.setTextColor(MUTED);
        LinearLayout.LayoutParams periodParams = new LinearLayout.LayoutParams(-2, -2);
        periodParams.leftMargin = Ui.dp(context, 6);
        timeLine.addView(period, periodParams);
        group.addView(timeLine, new LinearLayout.LayoutParams(-1, -2));
        TextClock date = new TextClock(context);
        date.setFormat24Hour("EEE, d MMM, yyyy");
        date.setFormat12Hour("EEE, d MMM, yyyy");
        date.setTextSize(18);
        date.setTextColor(MUTED);
        date.setGravity(Gravity.CENTER);
        group.addView(date, new LinearLayout.LayoutParams(-1, -2));
        updateClock(context, group, placement);
        return group;
    }

    static void updateClock(Context context, View hostView, HomeActivity.WidgetPlacement placement) {
        LinearLayout group = (LinearLayout) hostView;
        LinearLayout timeLine = (LinearLayout) group.getChildAt(0);
        boolean showDate = placement.height >= 2 * HomeActivity.WIDGET_CELL_DP;
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
        SharedPreferences prefs = context.getSharedPreferences(HomeActivity.PREFS, Context.MODE_PRIVATE);
        int format = prefs.getInt(key(CLOCK_FORMAT, placement.id), 0);
        TextClock clock = (TextClock) timeLine.getChildAt(0);
        clock.setFormat24Hour(format == 2 ? "h:mm" : "HH:mm");
        clock.setFormat12Hour(format == 1 ? "HH:mm" : "h:mm");
        if (timeLine.getChildCount() > 1) {
            boolean periodVisible = format == 2 || (format == 0 && !android.text.format.DateFormat.is24HourFormat(context));
            timeLine.getChildAt(1).setVisibility(periodVisible ? View.VISIBLE : View.GONE);
        }
        String[] fonts = {"sans-serif", "serif", "monospace"};
        int font = Math.max(0, Math.min(2, prefs.getInt(key(CLOCK_FONT, placement.id), 0)));
        int weight = Math.max(0, Math.min(2, prefs.getInt(key(CLOCK_WEIGHT, placement.id), 0)));
        Typeface face = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? Typeface.create(Typeface.create(fonts[font], Typeface.NORMAL), weight == 0 ? 300 : weight == 1 ? 400 : 700, false)
                : Typeface.create(weight == 0 && font == 0 ? "sans-serif-light" : fonts[font], weight == 2 ? Typeface.BOLD : Typeface.NORMAL);
        clock.setTypeface(face);
        ((TextClock) timeLine.getChildAt(1)).setTypeface(face);
        date.setTypeface(face);
        date.setVisibility(showDate && prefs.getBoolean(key(CLOCK_DATE, placement.id), true) ? View.VISIBLE : View.GONE);
        TextClock period = (TextClock) timeLine.getChildAt(1);
        float availableWidth = Ui.dp(context, Math.max(1, placement.width - 16));
        float timeWidth = clockTimeWidth(context, clock, period);
        if (timeWidth > availableWidth) {
            float scale = availableWidth / timeWidth;
            clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, clock.getTextSize() * scale);
            period.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, period.getTextSize() * scale);
        }
    }

    static float clockTimeWidth(Context context, TextClock clock, TextClock period) {
        // Reserve the widest digits so the block does not change width every minute.
        float digitWidth = 0;
        for (int digit = 0; digit <= 9; digit++)
            digitWidth = Math.max(digitWidth, clock.getPaint().measureText(Integer.toString(digit)));
        float width = digitWidth * 4 + clock.getPaint().measureText(":");
        if (period.getVisibility() == View.VISIBLE) {
            float periodWidth = 0;
            for (String label : new java.text.DateFormatSymbols().getAmPmStrings())
                periodWidth = Math.max(periodWidth, period.getPaint().measureText(label));
            width += Ui.dp(context, 6) + periodWidth;
        }
        return width;
    }

    static View createVolume(Context context, HomeActivity.WidgetPlacement placement) {
        LinearLayout panel = new LinearLayout(context);
        updateVolume(context, panel, placement);
        return panel;
    }

    /** Rebuilds the buttons: in a row for a wide widget, in a column for a tall one. */
    static void updateVolume(Context context, View hostView, HomeActivity.WidgetPlacement placement) {
        LinearLayout panel = (LinearLayout) hostView;
        panel.removeAllViews();
        SharedPreferences prefs = context.getSharedPreferences(HomeActivity.PREFS, Context.MODE_PRIVATE);
        AudioManager audio = context.getSystemService(AudioManager.class);
        boolean horizontal = placement.width >= placement.height;
        boolean showMute = prefs.getBoolean(key(VOLUME_MUTE, placement.id), true);
        boolean joined = prefs.getBoolean(key(VOLUME_JOINED, placement.id), false);
        int[] icons = VOLUME_ICON_STYLES[Math.max(0, Math.min(VOLUME_ICON_STYLES.length - 1, prefs.getInt(key(VOLUME_ICONS, placement.id), 0)))];
        panel.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        // A joined block draws one card behind the buttons; separate buttons have a card each.
        int surface = surface(context, prefs, VOLUME_OPACITY, placement.id);
        panel.setBackground(joined ? Ui.rounded(surface, Ui.dp(context, 24)) : null);
        panel.setPadding(joined ? Ui.dp(context, 4) : 0, joined ? Ui.dp(context, 4) : 0, joined ? Ui.dp(context, 4) : 0, joined ? Ui.dp(context, 4) : 0);
        int buttonLength = (horizontal ? placement.width : placement.height) / (showMute ? 3 : 2);
        int iconSize = Math.max(16, Math.min(64, Math.min(buttonLength, horizontal ? placement.height : placement.width) / 2));
        // Volume down is on the left of a row and at the bottom of a column.
        int[] directions = horizontal
                ? new int[]{AudioManager.ADJUST_LOWER, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.ADJUST_RAISE}
                : new int[]{AudioManager.ADJUST_RAISE, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.ADJUST_LOWER};
        for (int direction : directions) {
            boolean mute = direction == AudioManager.ADJUST_TOGGLE_MUTE;
            if (mute && !showMute) continue;
            boolean muted = mute && audio.isStreamMute(AudioManager.STREAM_MUSIC);
            FrameLayout button = new FrameLayout(context);
            int radius = joined ? 20 : 24;
            button.setBackground(new RippleDrawable(context.getColorStateList(R.color.neutral_ripple),
                    muted ? Ui.rounded(ATLAS_ACTIVE, Ui.dp(context, radius)) : joined ? null : Ui.rounded(surface, Ui.dp(context, radius)),
                    Ui.rounded(Color.WHITE, Ui.dp(context, radius))));
            button.setContentDescription(direction == AudioManager.ADJUST_RAISE ? "Прибавить громкость"
                    : direction == AudioManager.ADJUST_LOWER ? "Убавить громкость"
                    : muted ? "Включить звук" : "Выключить звук");
            button.setOnClickListener(v -> {
                // FLAG_SHOW_UI shows the system volume panel, as hardware keys do.
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
                panel.post(() -> updateVolume(context, panel, placement));
            });
            ImageView image = new ImageView(context);
            image.setImageResource(direction == AudioManager.ADJUST_RAISE ? icons[0]
                    : direction == AudioManager.ADJUST_LOWER ? icons[1] : R.drawable.ic_volume_off);
            if (!muted) image.setImageTintList(context.getColorStateList(R.color.neutral_text));
            button.addView(image, new FrameLayout.LayoutParams(Ui.dp(context, iconSize), Ui.dp(context, iconSize), Gravity.CENTER));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(horizontal ? 0 : -1, horizontal ? -1 : 0, 1);
            int margin = joined ? 0 : Ui.dp(context, 3);
            params.setMargins(margin, margin, margin, margin);
            panel.addView(button, params);
        }
    }

    static View createConnectivity(Context context, HomeActivity.WidgetPlacement placement) {
        LinearLayout panel = new LinearLayout(context);
        updateConnectivity(context, panel, placement);
        return panel;
    }

    /**
     * Rebuilds the Wi-Fi, Bluetooth and hotspot tiles: in a row for a wide widget, in a column for a tall one. A wide tile
     * puts its text beside the icon, a narrow one under it.
     */
    // BLUETOOTH_CONNECT exists from API 31; with targetSdk 28 the BLUETOOTH and BLUETOOTH_ADMIN grants apply.
    @SuppressLint("MissingPermission")
    @SuppressWarnings("deprecation")
    static void updateConnectivity(Context context, View hostView, HomeActivity.WidgetPlacement placement) {
        LinearLayout panel = (LinearLayout) hostView;
        panel.removeAllViews();
        SharedPreferences prefs = context.getSharedPreferences(HomeActivity.PREFS, Context.MODE_PRIVATE);
        WifiManager wifi = prefs.getBoolean(key(CONNECTIVITY_WIFI, placement.id), true)
                ? context.getApplicationContext().getSystemService(WifiManager.class) : null;
        BluetoothAdapter bluetooth = prefs.getBoolean(key(CONNECTIVITY_BLUETOOTH, placement.id), true) ? BluetoothAdapter.getDefaultAdapter() : null;
        boolean hotspot = prefs.getBoolean(key(CONNECTIVITY_HOTSPOT, placement.id), false);
        int style = prefs.getInt(key(CONNECTIVITY_STYLE, placement.id), STYLE_STATE);
        int tiles = (wifi == null ? 0 : 1) + (bluetooth == null ? 0 : 1) + (hotspot ? 1 : 0);
        if (tiles == 0) return;
        int width = placement.width;
        int height = placement.height;
        if (context instanceof HomeActivity) {
            // On the desktop the widget sits inside the default widget padding.
            Rect hostPadding = AppWidgetHostView.getDefaultPaddingForWidget(context,
                    new ComponentName(context, HomeActivity.class), null);
            float density = context.getResources().getDisplayMetrics().density;
            width -= Math.round((hostPadding.left + hostPadding.right) / density);
            height -= Math.round((hostPadding.top + hostPadding.bottom) / density);
        }
        boolean horizontal = width >= height;
        panel.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        int tileWidth = horizontal ? width / tiles : width;
        int tileHeight = horizontal ? height : height / tiles;
        int tileSize = Math.max(1, Math.min(tileWidth, tileHeight));
        int besideIcon = Math.max(16, Math.min(48, tileSize * 2 / 5));
        // Beside its text the icon keeps its place: a centred row would shift as the text changes.
        int start = Math.max(10, Math.min(32, (tileHeight - besideIcon) / 2));
        // Text goes beside the icon in a wide tile (from 3:2) with room for "Bluetooth"; longer names are ellipsized.
        boolean beside = style != STYLE_ICONS && tileWidth * 2 >= tileHeight * 3
                && tileWidth - start - besideIcon - 12 - 10 >= 80;
        boolean below = style != STYLE_ICONS && !beside;
        int offSurface = surface(context, prefs, CONNECTIVITY_OPACITY, placement.id);
        int iconSize = beside ? besideIcon : Math.max(16, Math.min(48, below ? tileSize / 3 : tileSize * 2 / 5));
        for (int i = 0; i < 3; i++) {
            boolean isWifi = i == 0;
            boolean isHotspot = i == 2;
            if (isWifi ? wifi == null : isHotspot ? !hotspot : bluetooth == null) continue;
            boolean on, off, turningOn;
            if (isWifi) {
                int state = wifi.getWifiState();
                on = state == WifiManager.WIFI_STATE_ENABLED;
                off = state == WifiManager.WIFI_STATE_DISABLED;
                turningOn = state == WifiManager.WIFI_STATE_ENABLING;
            } else if (isHotspot) {
                // The sticky broadcast keeps the current state; a failed start counts as off.
                Intent ap = context.registerReceiver(null, new IntentFilter(WIFI_AP_STATE_CHANGED));
                int state = ap == null ? 0 : ap.getIntExtra("wifi_state", 0);
                on = state == WIFI_AP_STATE_ENABLED;
                turningOn = state == WIFI_AP_STATE_ENABLING;
                off = !on && !turningOn && state != WIFI_AP_STATE_DISABLING;
            } else {
                int state = bluetooth.getState();
                on = state == BluetoothAdapter.STATE_ON;
                off = state == BluetoothAdapter.STATE_OFF;
                turningOn = state == BluetoothAdapter.STATE_TURNING_ON;
            }
            String name = isWifi ? "Wi-Fi" : isHotspot ? "Точка доступа" : "Bluetooth";
            String status = off ? "Выкл"
                    : !on ? turningOn ? "Включается…" : "Выключается…"
                    : style != STYLE_NETWORK || isHotspot ? "Вкл"
                    : isWifi ? wifiNetwork(wifi) : bluetoothDevice(bluetooth);
            LinearLayout button = new LinearLayout(context);
            button.setOrientation(below ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            button.setGravity(beside ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.CENTER);
            button.setPadding(Ui.dp(context, beside ? start : 10), 0, Ui.dp(context, 10), 0);
            int radius = Ui.dp(context, 24);
            int surface = on ? ATLAS_ACTIVE : offSurface;
            int textColor = on ? TEXT : context.getColor(R.color.neutral_text);
            int statusColor = on ? TEXT : context.getColor(R.color.neutral_muted);
            button.setBackground(new RippleDrawable(context.getColorStateList(R.color.neutral_ripple),
                    Ui.rounded(surface, radius), Ui.rounded(Color.WHITE, radius)));
            button.setContentDescription(name + ": " + status);
            button.setOnClickListener(v -> {
                if (!on && !off) return;
                if (isHotspot) {
                    setHotspotEnabled(context, !on);
                    return;
                }
                // Android 10+ lets only apps targeting API 28 or lower switch Wi-Fi.
                if (!(isWifi ? wifi.setWifiEnabled(!on) : on ? bluetooth.disable() : bluetooth.enable()))
                    Toast.makeText(context, isWifi ? "Не удалось переключить Wi-Fi"
                            : "Не удалось переключить Bluetooth", Toast.LENGTH_SHORT).show();
            });
            ImageView image = new ImageView(context);
            image.setImageResource(isWifi ? R.drawable.ic_wifi : isHotspot ? R.drawable.ic_hotspot : R.drawable.ic_bluetooth);
            image.setAlpha(on || off ? 1f : 0.5f);
            if (!on) image.setImageTintList(ColorStateList.valueOf(textColor));
            button.addView(image, new LinearLayout.LayoutParams(Ui.dp(context, iconSize), Ui.dp(context, iconSize)));
            if (beside) {
                LinearLayout texts = new LinearLayout(context);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.addView(tileText(context, name, 18, textColor));
                texts.addView(tileText(context, status, 14, statusColor));
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-2, -2);
                textParams.leftMargin = Ui.dp(context, 12);
                button.addView(texts, textParams);
            } else if (below) {
                // The icon already names the tile; only its state goes under it.
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-2, -2);
                textParams.topMargin = Ui.dp(context, 4);
                button.addView(tileText(context, status, 14, statusColor), textParams);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(horizontal ? 0 : -1, horizontal ? -1 : 0, 1);
            int margin = Ui.dp(context, 3);
            params.setMargins(margin, margin, margin, margin);
            panel.addView(button, params);
        }
    }

    /**
     * Starts or stops the Wi-Fi hotspot like the system settings do. TetheringManager is a system API; apps may call
     * it with the "modify system settings" access when the operator needs no tethering check, as in the car.
     */
    @SuppressLint("WrongConstant")
    private static void setHotspotEnabled(Context context, boolean enable) {
        if (!Settings.System.canWrite(context)) {
            Toast.makeText(context, "Разрешите изменение системных настроек, чтобы переключать точку доступа",
                    Toast.LENGTH_LONG).show();
            context.startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + context.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        try {
            Object tethering = context.getSystemService("tethering");
            Class<?> manager = Class.forName("android.net.TetheringManager");
            if (!enable) {
                manager.getMethod("stopTethering", int.class).invoke(tethering, 0 /* TETHERING_WIFI */);
                return;
            }
            Class<?> callback = Class.forName("android.net.TetheringManager$StartTetheringCallback");
            Object listener = Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[]{callback}, (proxy, method, args) -> {
                if ("onTetheringFailed".equals(method.getName()))
                    Toast.makeText(context, "Не удалось включить точку доступа", Toast.LENGTH_SHORT).show();
                return null;
            });
            manager.getMethod("startTethering", int.class, Executor.class, callback)
                    .invoke(tethering, 0 /* TETHERING_WIFI */, (Executor) new Handler(Looper.getMainLooper())::post, listener);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Toast.makeText(context, "Не удалось переключить точку доступа", Toast.LENGTH_SHORT).show();
        }
    }

    private static TextView tileText(Context context, String value, float sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    /** The connected network; Android hides its name without location access or with location off. */
    private static String wifiNetwork(WifiManager wifi) {
        WifiInfo info = wifi.getConnectionInfo();
        if (info == null || info.getSupplicantState() != SupplicantState.COMPLETED) return "Не подключено";
        String ssid = info.getSSID();
        if (ssid == null || "<unknown ssid>".equals(ssid)) return "Подключено";
        return ssid.length() > 1 && ssid.startsWith("\"") && ssid.endsWith("\"") ? ssid.substring(1, ssid.length() - 1) : ssid;
    }

    @SuppressLint("MissingPermission")
    private static String bluetoothDevice(BluetoothAdapter bluetooth) {
        for (BluetoothDevice device : bluetooth.getBondedDevices()) {
            try {
                // isConnected is hidden on API 30 but open to apps; the public API reports only per profile.
                if ((boolean) BluetoothDevice.class.getMethod("isConnected").invoke(device)) {
                    return device.getName() != null ? device.getName() : device.getAddress();
                }
            } catch (ReflectiveOperationException e) {
                return "Вкл";
            }
        }
        return "Не подключено";
    }
}
