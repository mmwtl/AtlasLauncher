package com.mmwtl.atlaslauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import java.util.Locale;

/**
 * Translucent scale over the whole screen for an adjustment in scale mode. The screen itself is the
 * scale: its positions are the ones the fingers set the value with, so the tinted part ends right
 * under them. A strip of marks along the edge away from the fingers grows near the fingers, and the
 * value in large type rides along the level beside it. Goes away with the last finger. Needs the
 * "display over other apps" permission; touches go through it. Must be used on the main thread.
 */
final class GestureScale {
    private static final int SCRIM = Color.argb(68, 8, 10, 14);
    private static final int MUTED = Color.rgb(200, 204, 208);
    // Day: a light veil with dark marks and text, and deeper fill colors that still read on it.
    private static final int DAY_SCRIM = Color.argb(82, 245, 246, 248);
    private static final int DAY_INK = Color.rgb(32, 33, 36);
    private static final int DAY_MUTED = Color.rgb(95, 99, 104);
    private static final int BLUE_DEEP = Color.rgb(20, 105, 205);
    private static final int AMBER_DEEP = Color.rgb(205, 125, 10);
    private static final int TEAL_DEEP = Color.rgb(10, 130, 122);
    private static final int COLD = Color.rgb(46, 150, 246);
    private static final int HOT = Color.rgb(240, 110, 60);
    private static final int BLUE = Color.rgb(46, 150, 246);
    private static final int BLUE_LIGHT = Color.rgb(120, 200, 255);
    private static final int AMBER = Color.rgb(246, 176, 66);
    private static final int AMBER_LIGHT = Color.rgb(255, 232, 160);
    private static final int TEAL = Color.rgb(40, 180, 170);
    private static final int TEAL_LIGHT = Color.rgb(140, 235, 215);
    // Sizes below are in UNITS of the screen width, so the scale keeps its proportions on any screen.
    private static final int UNITS = 360;
    // Marks: they start this far from the screen edge and grow by up to MARK_GROWTH near the level.
    private static final float MARK_INSET = 12;
    private static final float MARK_GROWTH = 46;
    // How far from the level the marks still grow, roughly.
    private static final float MARK_SPREAD = 30;
    // The value stands this far from the screen edge, clear of the grown marks.
    private static final float VALUE_INSET = 78;
    private static final long FADE_IN_MS = 120;
    private static final long FADE_OUT_MS = 220;
    // How fast the drawn value catches up with the fingers: it closes 63% of the gap in this time.
    private static final float MOVE_MS = 45;
    // Held fingers send nothing new, so this only covers a last finger up that never arrives.
    private static final long SAFETY_HIDE_MS = 6000;

    private final Context context;
    private final WindowManager windows;
    private final int screenWidth;
    private final int top;
    private final int bottom;
    private final int margin;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable hide = this::fadeOut;
    private ScaleView view;
    // Not on the screen or on its way out: the next value fades it in.
    private boolean hidden;

    /** {@code top} and {@code bottom} bound the gesture area, {@code margin} the scale within it, in screen pixels. */
    GestureScale(Context context, int screenWidth, int top, int bottom, int margin) {
        this.context = context;
        windows = context.getSystemService(WindowManager.class);
        this.screenWidth = screenWidth;
        this.top = top;
        this.bottom = bottom;
        this.margin = margin;
    }

    static boolean canShow(Context context) {
        return Settings.canDrawOverlays(context);
    }

    /**
     * Shows a value on the scale.
     *
     * @param sideways the scale runs from left to right rather than from bottom to top
     * @param cross    where the fingers are on the other axis, which picks the side of the marks
     * @param fraction the value's place on the scale, 0 to 1
     * @param marks    one entry per mark from minimum to maximum; null for a mark without a label
     */
    void show(MultiFingerGestures.Kind kind, boolean sideways, float cross, int icon, String title, String value,
            float fraction, String[] marks) {
        if (view == null) {
            view = new ScaleView(context);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            // A window of a service is drawn in software otherwise, too slow for the whole screen.
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            params.windowAnimations = 0;
            view.setAlpha(0);
            hidden = true;
            windows.addView(view, params);
        }
        // Only once: fingers report far more often than this takes, and restarting it would keep it near zero.
        if (hidden) {
            hidden = false;
            // A new gesture, even while the last one's scale is still fading: its own kind and direction.
            view.begin(kind, sideways, cross, fraction);
            view.animate().cancel();
            view.animate().alpha(1).setDuration(FADE_IN_MS).start();
        }
        view.update(icon, title, value, fraction, marks);
        main.removeCallbacks(hide);
        main.postDelayed(hide, SAFETY_HIDE_MS);
    }

    /** The last finger is up. */
    void finish() {
        main.removeCallbacks(hide);
        fadeOut();
    }

    void dismiss() {
        main.removeCallbacks(hide);
        if (view != null) {
            view.animate().cancel();
            windows.removeViewImmediate(view);
            view = null;
        }
    }

    private void fadeOut() {
        if (view != null) {
            hidden = true;
            view.animate().cancel();
            view.animate().alpha(0).setDuration(FADE_OUT_MS).withEndAction(this::dismiss).start();
        }
    }

    /** Draws in screen coordinates, so the scale stays under the fingers wherever the window lands. */
    private final class ScaleView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        private final RectF rect = new RectF();
        private final int[] location = new int[2];
        private boolean sideways;
        // The marks run along the right edge, or the bottom one when sideways, rather than the opposite one.
        private boolean marksAtEnd;
        private boolean night;
        private int lowColor;
        private int highColor;
        // White marks and text at night, dark ones in the day.
        private int ink;
        private int scrim;
        private int muted;
        private Drawable iconDrawable;
        private int iconRes;
        private String title = "";
        private String value = "";
        private String[] marks = new String[0];
        private float shown;
        private float target;
        private long lastFrame;

        ScaleView(Context context) {
            super(context);
        }

        void begin(MultiFingerGestures.Kind kind, boolean sideways, float cross, float fraction) {
            this.sideways = sideways;
            // The side away from the fingers; they stay where they started on this axis more or less.
            marksAtEnd = cross < (sideways ? (top + bottom) / 2f : screenWidth / 2f);
            shown = fraction;
            target = fraction;
            boolean temperature = kind == MultiFingerGestures.Kind.TEMPERATURE_LEFT
                    || kind == MultiFingerGestures.Kind.TEMPERATURE_RIGHT;
            lowColor = temperature ? COLD : kind == MultiFingerGestures.Kind.BRIGHTNESS ? AMBER
                    : kind == MultiFingerGestures.Kind.FAN ? TEAL : BLUE;
            highColor = temperature ? HOT : kind == MultiFingerGestures.Kind.BRIGHTNESS ? AMBER_LIGHT
                    : kind == MultiFingerGestures.Kind.FAN ? TEAL_LIGHT : BLUE_LIGHT;
            night = ThemeMode.isNight(getContext());
            if (!night && !temperature) {
                // The light ends would fade into the veil: a deeper low end, the old low end on top.
                highColor = lowColor;
                lowColor = kind == MultiFingerGestures.Kind.BRIGHTNESS ? AMBER_DEEP
                        : kind == MultiFingerGestures.Kind.FAN ? TEAL_DEEP : BLUE_DEEP;
            }
            ink = night ? Color.WHITE : DAY_INK;
            scrim = night ? SCRIM : DAY_SCRIM;
            muted = night ? MUTED : DAY_MUTED;
        }

        void update(int icon, String title, String value, float fraction, String[] marks) {
            if (icon != iconRes) {
                iconRes = icon;
                iconDrawable = context.getDrawable(icon).mutate();
            }
            this.title = title.toUpperCase(Locale.ROOT);
            this.value = value;
            this.marks = marks;
            target = fraction;
            invalidate();
        }

        /**
         * Eases the drawn value toward the last one, frame by frame. An animator would not do:
         * fingers report as often as frames are drawn, and each restart would begin from no progress.
         */
        private void advance() {
            long now = SystemClock.uptimeMillis();
            float elapsed = lastFrame == 0 ? MOVE_MS : now - lastFrame;
            lastFrame = now;
            shown += (target - shown) * (1 - (float) Math.exp(-elapsed / MOVE_MS));
            if (Math.abs(target - shown) < 0.001f) {
                shown = target;
                lastFrame = 0;
            } else {
                postInvalidateOnAnimation();
            }
        }

        /** Screen position along the scale. */
        private float position(float fraction) {
            float start = sideways ? margin : bottom - margin;
            float end = sideways ? screenWidth - margin : top + margin;
            return start + fraction * (end - start);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            getLocationOnScreen(location);
            canvas.translate(-location[0], -location[1]);
            advance();
            canvas.drawColor(scrim);
            float level = position(shown);
            // The color of the value where the fingers are.
            int color = mix(lowColor, highColor, shown);
            int accent = night ? mix(color, Color.WHITE, 0.5f) : mix(color, Color.BLACK, 0.2f);
            drawFill(canvas, level, color);
            drawMarks(canvas, level, accent);
            drawValue(canvas, level, color);
        }

        /** Brightest at the level and fading toward the minimum, with a glow on the other side of the edge. */
        private void drawFill(Canvas canvas, float level, int color) {
            // Toward the minimum: down the screen, or left when sideways.
            float minimum = sideways ? 0 : bottom;
            float glow = sideways ? level + u(40) : level - u(40);
            paint.setStyle(Paint.Style.FILL);
            // A shader is drawn at the alpha of the paint's color, which the marks leave low.
            paint.setColor(Color.BLACK);
            paint.setShader(sideways
                    ? new LinearGradient(level, 0, minimum, 0, new int[] {alpha(color, night ? 128 : 107),
                            alpha(color, 51), alpha(lowColor, 10)}, new float[] {0, 0.35f, 1}, Shader.TileMode.CLAMP)
                    : new LinearGradient(0, level, 0, minimum, new int[] {alpha(color, night ? 128 : 107),
                            alpha(color, 51), alpha(lowColor, 10)}, new float[] {0, 0.35f, 1}, Shader.TileMode.CLAMP));
            alongRect(level, minimum);
            canvas.drawRect(rect, paint);
            int glowColor = alpha(color, night ? 97 : 77);
            paint.setShader(sideways
                    ? new LinearGradient(glow, 0, level, 0, alpha(color, 0), glowColor, Shader.TileMode.CLAMP)
                    : new LinearGradient(0, glow, 0, level, alpha(color, 0), glowColor, Shader.TileMode.CLAMP));
            alongRect(glow, level);
            canvas.drawRect(rect, paint);
            paint.setShader(null);
            // The edge of the fill, where the fingers are.
            paint.setColor(alpha(night ? mix(color, Color.WHITE, 0.6f) : mix(color, Color.BLACK, 0.15f), 242));
            float half = u(1.6f) / 2;
            alongRect(level - half, level + half);
            canvas.drawRect(rect, paint);
        }

        /** A band across the whole gesture area between two positions along the scale. */
        private void alongRect(float a, float b) {
            if (sideways) {
                rect.set(Math.min(a, b), top, Math.max(a, b), bottom);
            } else {
                rect.set(0, Math.min(a, b), screenWidth, Math.max(a, b));
            }
        }

        /** A mark for every step along the edge: the ones near the level grow and take the scale's color. */
        private void drawMarks(Canvas canvas, float level, int accent) {
            // The screen edge the marks stand on, and which way is inward from it.
            float edge = sideways ? (marksAtEnd ? bottom : top) : marksAtEnd ? screenWidth : 0;
            int inward = marksAtEnd ? -1 : 1;
            float from = edge + inward * u(MARK_INSET);
            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setLetterSpacing(0);
            for (int i = 0; i < marks.length; i++) {
                float at = marks.length == 1 ? position(0) : position(i / (marks.length - 1f));
                boolean labelled = marks[i] != null;
                float distance = Math.abs(at - level) / u(MARK_SPREAD);
                float near = (float) Math.exp(-distance * distance);
                float reach = u(labelled ? 12 : 6) + u(MARK_GROWTH) * near;
                paint.setColor(near > 0.5f ? alpha(accent, Math.round(255 * (0.4f + 0.6f * near)))
                        : alpha(ink, Math.round(255 * (0.22f + 0.5f * near))));
                float half = u(labelled ? 1.6f : 1) / 2;
                markRect(from, from + inward * reach, at - half, at + half);
                canvas.drawRect(rect, paint);
                // The large value names the level; only the marks away from it are labelled.
                if (labelled && Math.abs(at - level) > u(MARK_GROWTH)) {
                    float far = Math.abs(at - level) / u(80);
                    textPaint.setTextSize(u(10) + u(4) * (float) Math.exp(-far * far));
                    textPaint.setColor(muted);
                    drawMarkLabel(canvas, marks[i], from + inward * (reach + u(5)), at, inward);
                }
            }
        }

        /** A rectangle given across and along the scale, whichever way the scale runs. */
        private void markRect(float acrossA, float acrossB, float alongA, float alongB) {
            float acrossLow = Math.min(acrossA, acrossB);
            float acrossHigh = Math.max(acrossA, acrossB);
            if (sideways) {
                rect.set(alongA, acrossLow, alongB, acrossHigh);
            } else {
                rect.set(acrossLow, alongA, acrossHigh, alongB);
            }
        }

        private void drawMarkLabel(Canvas canvas, String label, float across, float along, int inward) {
            if (sideways) {
                textPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(label, along, inward > 0 ? across - textPaint.ascent() : across - textPaint.descent(),
                        textPaint);
            } else {
                textPaint.setTextAlign(inward > 0 ? Paint.Align.LEFT : Paint.Align.RIGHT);
                canvas.drawText(label, across, along - (textPaint.ascent() + textPaint.descent()) / 2, textPaint);
            }
        }

        /** The value in large type beside the marks, with the icon and what it is above it, next to the level. */
        private void drawValue(Canvas canvas, float level, int color) {
            textPaint.setTypeface(light);
            textPaint.setLetterSpacing(0);
            textPaint.setTextSize(valueTextSize());
            float valueWidth = textPaint.measureText(value);
            // The height of a digit, about; the ascent of the font is taller than any of them.
            float valueHeight = textPaint.getTextSize() * 0.72f;
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextSize(u(11));
            textPaint.setLetterSpacing(0.14f);
            float iconSize = u(14);
            float titleGap = u(5);
            float titleWidth = iconSize + titleGap + textPaint.measureText(title);
            float titleHeight = Math.max(iconSize, -textPaint.ascent());
            float blockWidth = Math.max(valueWidth, titleWidth);
            float blockHeight = titleHeight + u(7) + valueHeight;
            float gap = u(12);

            // The block's edge toward the marks, along the line it is aligned to, and its baseline.
            boolean alignEnd;
            float x;
            float baseline;
            if (sideways) {
                baseline = marksAtEnd ? bottom - u(VALUE_INSET) : top + u(VALUE_INSET) + blockHeight;
                // Before the level when there is room, after it otherwise.
                alignEnd = level - gap - blockWidth > 0;
                x = alignEnd ? level - gap : level + gap;
            } else {
                alignEnd = marksAtEnd;
                x = marksAtEnd ? screenWidth - u(VALUE_INSET) : u(VALUE_INSET);
                // Above the level when there is room, below it otherwise.
                baseline = level - top > blockHeight + gap * 2 ? level - gap : level + gap + blockHeight;
            }

            textPaint.setTextAlign(alignEnd ? Paint.Align.RIGHT : Paint.Align.LEFT);
            int titleColor = night ? mix(color, Color.WHITE, 0.55f) : mix(color, Color.BLACK, 0.35f);
            float titleBaseline = baseline - valueHeight - u(7);
            float titleStart = alignEnd ? x - titleWidth : x;
            if (iconDrawable != null) {
                float iconTop = titleBaseline + (textPaint.ascent() - iconSize) / 2;
                iconDrawable.setTint(titleColor);
                iconDrawable.setBounds(Math.round(titleStart), Math.round(iconTop), Math.round(titleStart + iconSize),
                        Math.round(iconTop + iconSize));
                iconDrawable.draw(canvas);
            }
            textPaint.setColor(titleColor);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(title, titleStart + iconSize + titleGap, titleBaseline, textPaint);

            textPaint.setTypeface(light);
            textPaint.setLetterSpacing(0);
            textPaint.setTextSize(valueTextSize());
            textPaint.setTextAlign(alignEnd ? Paint.Align.RIGHT : Paint.Align.LEFT);
            textPaint.setColor(ink);
            if (night) {
                // Keeps the thin digits apart from a bright wallpaper.
                textPaint.setShadowLayer(u(9), 0, 0, Color.argb(90, 0, 0, 0));
            }
            canvas.drawText(value, x, baseline, textPaint);
            textPaint.clearShadowLayer();
        }

        private int alpha(int color, int alpha) {
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        }

        private int mix(int a, int b, float f) {
            return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                    Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                    Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
        }

        /** A word, such as a fan profile, is smaller than a number. */
        private float valueTextSize() {
            return u(!value.isEmpty() && Character.isLetter(value.charAt(0)) ? 56 : 84);
        }

        private float u(float units) {
            return units * screenWidth / UNITS;
        }
    }
}
