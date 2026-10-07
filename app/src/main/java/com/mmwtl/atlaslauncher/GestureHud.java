package com.mmwtl.atlaslauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
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
 * Card over other apps that shows the value a gesture has just set: a title, a large number and
 * a bar across the allowed range. Driver temperature appears at the left edge, passenger at the
 * right, brightness in the centre. Needs the "display over other apps" permission; without it
 * the gestures still work, only silently. Must be used on the main thread.
 */
final class GestureHud {
    private static final int SURFACE = Color.argb(240, 35, 37, 40);
    private static final int RAISED = Color.rgb(64, 67, 71);
    private static final int TEXT = Color.rgb(241, 242, 244);
    private static final int MUTED = Color.rgb(196, 199, 202);
    private static final int ACCENT = Color.rgb(46, 150, 246);
    private static final int COLD = Color.rgb(46, 150, 246);
    private static final int HOT = Color.rgb(240, 110, 60);
    private static final int CARD_WIDTH = 340;
    private static final int BAR_HEIGHT = 10;
    private static final int EDGE_MARGIN = 48;
    private static final long VISIBLE_MS = 1500;

    private final Context context;
    private final WindowManager windows;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable hide = this::fadeOut;
    private LinearLayout card;
    private ImageView icon;
    private TextView title;
    private TextView value;
    private View fill;
    private float fillFraction;
    private ValueAnimator fillAnimator;
    private WindowManager.LayoutParams params;

    GestureHud(Context context) {
        this.context = context;
        windows = context.getSystemService(WindowManager.class);
    }

    void show(MultiFingerGestures.Kind kind, float current, float min, float max) {
        if (!Settings.canDrawOverlays(context) || max <= min) {
            return;
        }
        if (card == null) {
            create();
        }
        boolean temperature = kind != MultiFingerGestures.Kind.BRIGHTNESS;
        float fraction = Math.max(0, Math.min(1, (current - min) / (max - min)));
        icon.setImageResource(temperature ? R.drawable.ic_temperature : R.drawable.ic_brightness);
        title.setText(kind == MultiFingerGestures.Kind.TEMPERATURE_LEFT ? "Водитель"
                : kind == MultiFingerGestures.Kind.TEMPERATURE_RIGHT ? "Пассажир" : "Яркость");
        value.setText(temperature ? String.format(Locale.ROOT, "%.1f°", current)
                : String.valueOf(Math.round(current)));
        ((GradientDrawable) fill.getBackground()).setColor(temperature ? blend(COLD, HOT, fraction) : ACCENT);
        animateFill(fraction);

        int gravity = kind == MultiFingerGestures.Kind.TEMPERATURE_LEFT ? Gravity.START
                : kind == MultiFingerGestures.Kind.TEMPERATURE_RIGHT ? Gravity.END : Gravity.CENTER_HORIZONTAL;
        if (params.gravity != (gravity | Gravity.CENTER_VERTICAL) || card.getParent() == null) {
            params.gravity = gravity | Gravity.CENTER_VERTICAL;
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
        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(28), dp(24), dp(28), dp(28));
        card.setBackground(rounded(SURFACE, dp(28)));
        card.setElevation(dp(12));
        card.setAlpha(0);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        icon = new ImageView(context);
        header.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));
        title = new TextView(context);
        title.setTextColor(MUTED);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title);
        card.addView(header);

        value = new TextView(context);
        value.setTextColor(TEXT);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        value.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        value.setIncludeFontPadding(false);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(-2, -2);
        valueParams.topMargin = dp(12);
        valueParams.bottomMargin = dp(20);
        card.addView(value, valueParams);

        FrameLayout bar = new FrameLayout(context);
        bar.setBackground(rounded(RAISED, dp(BAR_HEIGHT / 2)));
        fill = new View(context);
        fill.setBackground(rounded(ACCENT, dp(BAR_HEIGHT / 2)));
        bar.addView(fill, new FrameLayout.LayoutParams(0, -1));
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
        int barWidth = dp(CARD_WIDTH - 56);
        fillAnimator = ValueAnimator.ofFloat(fillFraction, fraction);
        fillAnimator.setDuration(160);
        fillAnimator.addUpdateListener(animation -> {
            fillFraction = (float) animation.getAnimatedValue();
            fill.getLayoutParams().width = Math.max(dp(BAR_HEIGHT), Math.round(barWidth * fillFraction));
            fill.requestLayout();
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

    private static int blend(int from, int to, float fraction) {
        return Color.rgb(
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * fraction),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * fraction),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * fraction));
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
