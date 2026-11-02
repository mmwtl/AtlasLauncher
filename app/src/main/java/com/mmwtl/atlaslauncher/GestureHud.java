package com.mmwtl.atlaslauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Card over other apps that shows the value a gesture has just set: a title, a large value and
 * a bar across the allowed range, near the top of the screen. Driver temperature appears at the
 * left edge, passenger at the right, brightness and fan in the centre. Needs the "display over
 * other apps" permission; without it the gestures still work, only silently. Must be used on the
 * main thread.
 */
final class GestureHud {
    // Surface, outline, bar and text follow the day or night mode, as the dock and built-in widgets do.
    private static final int ACCENT = Color.rgb(46, 150, 246);
    private static final int COLD = Color.rgb(46, 150, 246);
    private static final int HOT = Color.rgb(240, 110, 60);
    private static final int CARD_WIDTH = 460;
    private static final int BAR_HEIGHT = 14;
    private static final int EDGE_MARGIN = 48;
    // Below the status bar strip.
    private static final int TOP_MARGIN = 112;
    private static final long VISIBLE_MS = 1500;

    private final Context context;
    private final WindowManager windows;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable hide = this::fadeOut;
    private LinearLayout card;
    private ImageView icon;
    private TextView title;
    private TextView value;
    private FrameLayout bar;
    private View fill;
    private ClipDrawable temperatureFill;
    private ClipDrawable brightnessFill;
    private float fillFraction;
    private ValueAnimator fillAnimator;
    private WindowManager.LayoutParams params;
    // The mode the card was built in.
    private boolean night;

    GestureHud(Context context) {
        this.context = context;
        windows = context.getSystemService(WindowManager.class);
    }

    void show(MultiFingerGestures.Kind kind, float current, float min, float max) {
        if (max <= min) {
            return;
        }
        boolean temperature = kind == MultiFingerGestures.Kind.TEMPERATURE_LEFT
                || kind == MultiFingerGestures.Kind.TEMPERATURE_RIGHT;
        present(kind, kind == MultiFingerGestures.Kind.TEMPERATURE_LEFT ? "Водитель"
                        : kind == MultiFingerGestures.Kind.TEMPERATURE_RIGHT ? "Пассажир" : "Яркость",
                temperature ? String.format(Locale.ROOT, "%.1f°", current) : String.valueOf(Math.round(current)),
                Math.max(0, Math.min(1, (current - min) / (max - min))));
    }

    /** Fan speed or, in climate AUTO, the auto-fan profile; {@code fraction} places it on the bar. */
    void showFan(String title, String text, float fraction) {
        present(MultiFingerGestures.Kind.FAN, title, text, fraction);
    }

    /** A switch that a gesture has just flipped: no bar, as a state has no range. */
    void showState(int iconRes, String titleText, String state) {
        if (!Settings.canDrawOverlays(context)) {
            return;
        }
        prepare();
        icon.setImageResource(iconRes);
        title.setText(titleText);
        value.setText(state);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 72);
        bar.setVisibility(View.GONE);
        reveal(Gravity.CENTER_HORIZONTAL);
    }

    private void present(MultiFingerGestures.Kind kind, String titleText, String valueText, float fraction) {
        if (!Settings.canDrawOverlays(context)) {
            return;
        }
        prepare();
        bar.setVisibility(View.VISIBLE);
        boolean temperature = kind == MultiFingerGestures.Kind.TEMPERATURE_LEFT
                || kind == MultiFingerGestures.Kind.TEMPERATURE_RIGHT;
        icon.setImageResource(temperature ? R.drawable.ic_temperature
                : kind == MultiFingerGestures.Kind.FAN ? R.drawable.ic_fan : R.drawable.ic_brightness);
        title.setText(titleText);
        value.setText(valueText);
        // A fan profile name is a word, not a number: smaller, so the longest stays on one line. All
        // names share one size, or the card would jump while the gesture steps through them.
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, Character.isLetter(valueText.charAt(0)) ? 72 : 104);
        fill.setBackground(temperature ? temperatureFill : brightnessFill);
        animateFill(fraction);
        reveal(kind == MultiFingerGestures.Kind.TEMPERATURE_LEFT ? Gravity.START
                : kind == MultiFingerGestures.Kind.TEMPERATURE_RIGHT ? Gravity.END : Gravity.CENTER_HORIZONTAL);
    }

    /** Builds the card, again after the day or night mode has changed. */
    private void prepare() {
        boolean nightNow = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (card != null && nightNow == night) {
            return;
        }
        dismiss();
        night = nightNow;
        create();
    }

    private void reveal(int gravity) {
        if (params.gravity != (gravity | Gravity.TOP) || card.getParent() == null) {
            params.gravity = gravity | Gravity.TOP;
            params.y = TOP_MARGIN;
            params.x = gravity == Gravity.CENTER_HORIZONTAL ? 0 : EDGE_MARGIN;
            if (card.getParent() == null) {
                windows.addView(card, params);
            } else {
                windows.updateViewLayout(card, params);
            }
        }
        card.animate().cancel();
        card.setVisibility(View.VISIBLE);
        card.animate().alpha(1).setDuration(120).start();
        main.removeCallbacks(hide);
        main.postDelayed(hide, VISIBLE_MS);
    }

    void dismiss() {
        main.removeCallbacks(hide);
        if (card != null && card.getParent() != null) {
            windows.removeViewImmediate(card);
        }
    }

    private void create() {
        int surface = context.getColor(R.color.neutral_surface);
        int raised = night ? context.getColor(R.color.neutral_raised) : context.getColor(R.color.tile_off);
        int text = context.getColor(R.color.neutral_text);
        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(36), dp(30), dp(36), dp(36));
        GradientDrawable background = rounded(surface, dp(36));
        background.setStroke(dp(1), raised);
        card.setBackground(background);
        card.setElevation(dp(12));
        card.setAlpha(0);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        icon = new ImageView(context);
        // The icons are drawn light for the night mode.
        icon.setImageTintList(ColorStateList.valueOf(text));
        header.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));
        title = new TextView(context);
        title.setTextColor(context.getColor(R.color.neutral_muted));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(14), 0, 0, 0);
        header.addView(title);
        card.addView(header);

        value = new TextView(context);
        value.setTextColor(text);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 104);
        value.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        value.setIncludeFontPadding(false);
        value.setSingleLine();
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(-2, -2);
        valueParams.topMargin = dp(14);
        valueParams.bottomMargin = dp(26);
        card.addView(value, valueParams);

        bar = new FrameLayout(context);
        bar.setBackground(rounded(raised, dp(BAR_HEIGHT / 2)));
        // The fill reveals a fixed cold-to-warm gradient up to the current value.
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {COLD, HOT});
        gradient.setCornerRadius(dp(BAR_HEIGHT / 2));
        temperatureFill = new ClipDrawable(gradient, Gravity.START, ClipDrawable.HORIZONTAL);
        brightnessFill = new ClipDrawable(rounded(ACCENT, dp(BAR_HEIGHT / 2)), Gravity.START, ClipDrawable.HORIZONTAL);
        fill = new View(context);
        bar.addView(fill, new FrameLayout.LayoutParams(-1, -1));
        card.addView(bar, new LinearLayout.LayoutParams(-1, dp(BAR_HEIGHT)));

        params = new WindowManager.LayoutParams(dp(CARD_WIDTH), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.windowAnimations = 0;
    }

    private void animateFill(float fraction) {
        if (fillAnimator != null) {
            fillAnimator.cancel();
        }
        fillAnimator = ValueAnimator.ofFloat(fillFraction, fraction);
        fillAnimator.setDuration(160);
        fillAnimator.addUpdateListener(animation -> {
            fillFraction = (float) animation.getAnimatedValue();
            temperatureFill.setLevel(Math.round(10_000 * fillFraction));
            brightnessFill.setLevel(Math.round(10_000 * fillFraction));
        });
        fillAnimator.start();
    }

    private void fadeOut() {
        card.animate().alpha(0).setDuration(250).withEndAction(() -> card.setVisibility(View.INVISIBLE)).start();
    }

    private static GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }


    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
