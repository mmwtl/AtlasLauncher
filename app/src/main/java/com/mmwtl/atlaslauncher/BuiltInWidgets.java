package com.mmwtl.atlaslauncher;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioManager;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
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

/** The clock, volume and Wi-Fi/Bluetooth widgets built into HOME; settings show them as previews. */
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
    static final String CONNECTIVITY_STYLE = "connectivity_style";
    static final int STYLE_ICONS = 0;
    static final int STYLE_STATE = 1;
    static final int STYLE_NETWORK = 2;
    // Volume up, volume down for each icon style; mute uses one icon in every style.
    private static final int[][] VOLUME_ICON_STYLES = {
            {R.drawable.ic_volume_up, R.drawable.ic_volume_down},
            {R.drawable.ic_plus, R.drawable.ic_minus},
            {R.drawable.ic_chevron_up, R.drawable.ic_chevron_down}
    };

    private BuiltInWidgets() {
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
        int format = prefs.getInt(CLOCK_FORMAT, 0);
        TextClock clock = (TextClock) timeLine.getChildAt(0);
        clock.setFormat24Hour(format == 2 ? "h:mm" : "HH:mm");
        clock.setFormat12Hour(format == 1 ? "HH:mm" : "h:mm");
        if (timeLine.getChildCount() > 1) {
            boolean periodVisible = format == 2 || (format == 0 && !android.text.format.DateFormat.is24HourFormat(context));
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
        boolean showMute = prefs.getBoolean(VOLUME_MUTE, true);
        boolean joined = prefs.getBoolean(VOLUME_JOINED, false);
        int[] icons = VOLUME_ICON_STYLES[Math.max(0, Math.min(VOLUME_ICON_STYLES.length - 1, prefs.getInt(VOLUME_ICONS, 0)))];
        panel.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        // A joined block draws one card behind the buttons; separate buttons have a card each.
        panel.setBackground(joined ? Ui.rounded(HomeActivity.NEUTRAL_SURFACE, Ui.dp(context, 24)) : null);
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
            button.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(40, 255, 255, 255)),
                    muted ? Ui.rounded(ATLAS_ACTIVE, Ui.dp(context, radius)) : joined ? null : Ui.rounded(HomeActivity.NEUTRAL_SURFACE, Ui.dp(context, radius)),
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
     * Rebuilds the Wi-Fi and Bluetooth tiles: in a row for a wide widget, in a column for a tall one. A wide tile
     * puts its text beside the icon, a narrow one under it.
     */
    // BLUETOOTH_CONNECT exists from API 31; with targetSdk 30 the BLUETOOTH and BLUETOOTH_ADMIN grants apply.
    @SuppressLint("MissingPermission")
    static void updateConnectivity(Context context, View hostView, HomeActivity.WidgetPlacement placement) {
        LinearLayout panel = (LinearLayout) hostView;
        panel.removeAllViews();
        SharedPreferences prefs = context.getSharedPreferences(HomeActivity.PREFS, Context.MODE_PRIVATE);
        WifiManager wifi = prefs.getBoolean(CONNECTIVITY_WIFI, true)
                ? context.getApplicationContext().getSystemService(WifiManager.class) : null;
        BluetoothAdapter bluetooth = prefs.getBoolean(CONNECTIVITY_BLUETOOTH, true) ? BluetoothAdapter.getDefaultAdapter() : null;
        int style = prefs.getInt(CONNECTIVITY_STYLE, STYLE_STATE);
        int tiles = (wifi == null ? 0 : 1) + (bluetooth == null ? 0 : 1);
        if (tiles == 0) return;
        boolean horizontal = placement.width >= placement.height;
        panel.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        int tileWidth = horizontal ? placement.width / tiles : placement.width;
        int tileHeight = horizontal ? placement.height : placement.height / tiles;
        boolean beside = style != STYLE_ICONS && tileWidth >= tileHeight * 2;
        boolean below = style != STYLE_ICONS && !beside;
        int iconSize = Math.max(16, Math.min(48, Math.min(tileWidth, tileHeight) * (below ? 1 : 2) / (below ? 3 : 5)));
        for (int i = 0; i < 2; i++) {
            boolean isWifi = i == 0;
            if (isWifi ? wifi == null : bluetooth == null) continue;
            int state = isWifi ? wifi.getWifiState() : bluetooth.getState();
            boolean on = isWifi ? state == WifiManager.WIFI_STATE_ENABLED : state == BluetoothAdapter.STATE_ON;
            boolean off = isWifi ? state == WifiManager.WIFI_STATE_DISABLED : state == BluetoothAdapter.STATE_OFF;
            String name = isWifi ? "Wi-Fi" : "Bluetooth";
            String status = off ? "Выкл"
                    : !on ? (isWifi ? state == WifiManager.WIFI_STATE_ENABLING : state == BluetoothAdapter.STATE_TURNING_ON)
                            ? "Включается…" : "Выключается…"
                    : style != STYLE_NETWORK ? "Вкл"
                    : isWifi ? wifiNetwork(wifi) : bluetoothDevice(bluetooth);
            LinearLayout button = new LinearLayout(context);
            button.setOrientation(below ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            button.setGravity(Gravity.CENTER);
            button.setPadding(Ui.dp(context, 10), 0, Ui.dp(context, 10), 0);
            int radius = Ui.dp(context, 24);
            button.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(40, 255, 255, 255)),
                    Ui.rounded(on ? ATLAS_ACTIVE : HomeActivity.NEUTRAL_SURFACE, radius), Ui.rounded(Color.WHITE, radius)));
            button.setContentDescription(name + ": " + status);
            button.setOnClickListener(v -> {
                if (!on && !off) return;
                if (isWifi) toggleWifi(context, wifi, on);
                else if (!(on ? bluetooth.disable() : bluetooth.enable()))
                    Toast.makeText(context, "Не удалось переключить Bluetooth", Toast.LENGTH_SHORT).show();
            });
            ImageView image = new ImageView(context);
            image.setImageResource(isWifi ? R.drawable.ic_wifi : R.drawable.ic_bluetooth);
            image.setAlpha(on || off ? 1f : 0.5f);
            button.addView(image, new LinearLayout.LayoutParams(Ui.dp(context, iconSize), Ui.dp(context, iconSize)));
            if (beside) {
                LinearLayout texts = new LinearLayout(context);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.addView(tileText(context, name, 18, TEXT));
                texts.addView(tileText(context, status, 14, on ? TEXT : MUTED));
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-2, -2);
                textParams.leftMargin = Ui.dp(context, 12);
                button.addView(texts, textParams);
            } else if (below) {
                // The icon already names the tile; only its state goes under it.
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-2, -2);
                textParams.topMargin = Ui.dp(context, 4);
                button.addView(tileText(context, status, 14, on ? TEXT : MUTED), textParams);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(horizontal ? 0 : -1, horizontal ? -1 : 0, 1);
            int margin = Ui.dp(context, 3);
            params.setMargins(margin, margin, margin, margin);
            panel.addView(button, params);
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

    @SuppressWarnings("deprecation")
    private static void toggleWifi(Context context, WifiManager wifi, boolean on) {
        if (wifi.setWifiEnabled(!on)) return;
        // Android 10+ refuses the switch to apps targeting API 29+; the system panel has its own toggle.
        try {
            context.startActivity(new Intent(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? Settings.Panel.ACTION_WIFI : Settings.ACTION_WIFI_SETTINGS));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "Не удалось переключить Wi-Fi", Toast.LENGTH_SHORT).show();
        }
    }
}
