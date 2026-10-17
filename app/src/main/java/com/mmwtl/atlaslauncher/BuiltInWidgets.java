package com.mmwtl.atlaslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;

/** The clock and volume widgets built into HOME; settings show them as previews. */
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
}
