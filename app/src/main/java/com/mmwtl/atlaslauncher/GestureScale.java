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

/**
 * Translucent scale over the whole screen for an adjustment in scale mode. The screen itself is the
 * scale: its positions are the ones the fingers set the value with, so the tinted part ends right
 * under them. A rail with the marks and the current value stands on the side away from the fingers.
 * Goes away with the last finger. Needs the "display over other apps" permission; touches go through
 * it. Must be used on the main thread.
 */
final class GestureScale {
    private static final int SCRIM = Color.argb(90, 8, 10, 14);
    private static final int CARD = Color.argb(225, 28, 30, 34);
    private static final int MUTED = Color.rgb(200, 204, 208);
    private static final int COLD = Color.rgb(46, 150, 246);
    private static final int HOT = Color.rgb(240, 110, 60);
    private static final int BLUE = Color.rgb(46, 150, 246);
    private static final int BLUE_LIGHT = Color.rgb(120, 200, 255);
    private static final int AMBER = Color.rgb(246, 176, 66);
    private static final int AMBER_LIGHT = Color.rgb(255, 232, 160);
    private static final int TEAL = Color.rgb(40, 180, 170);
    private static final int TEAL_LIGHT = Color.rgb(140, 235, 215);
    private static final int FILL_ALPHA = 85;
    private static final int RAIL_INSET_DP = 130;
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
     * @param cross    where the fingers are on the other axis, which picks the side of the rail
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
        private final RectF rect = new RectF();
        private final int[] location = new int[2];
        private boolean sideways;
        private boolean railAtEnd;
        private int lowColor;
        private int highColor;
        private Shader fillShader;
        private Shader railShader;
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
            railAtEnd = cross < (sideways ? (top + bottom) / 2f : screenWidth / 2f);
            shown = fraction;
            target = fraction;
            boolean temperature = kind == MultiFingerGestures.Kind.TEMPERATURE_LEFT
                    || kind == MultiFingerGestures.Kind.TEMPERATURE_RIGHT;
            lowColor = temperature ? COLD : kind == MultiFingerGestures.Kind.BRIGHTNESS ? AMBER
                    : kind == MultiFingerGestures.Kind.FAN ? TEAL : BLUE;
            highColor = temperature ? HOT : kind == MultiFingerGestures.Kind.BRIGHTNESS ? AMBER_LIGHT
                    : kind == MultiFingerGestures.Kind.FAN ? TEAL_LIGHT : BLUE_LIGHT;
            // Built from the colors and direction on the next frame.
            fillShader = null;
            railShader = null;
        }

        void update(int icon, String title, String value, float fraction, String[] marks) {
            if (icon != iconRes) {
                iconRes = icon;
                iconDrawable = context.getDrawable(icon);
            }
            this.title = title;
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

        private Shader gradient(int alpha) {
            int low = Color.argb(alpha, Color.red(lowColor), Color.green(lowColor), Color.blue(lowColor));
            int high = Color.argb(alpha, Color.red(highColor), Color.green(highColor), Color.blue(highColor));
            return sideways ? new LinearGradient(position(0), 0, position(1), 0, low, high, Shader.TileMode.CLAMP)
                    : new LinearGradient(0, position(0), 0, position(1), low, high, Shader.TileMode.CLAMP);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            getLocationOnScreen(location);
            canvas.translate(-location[0], -location[1]);
            advance();
            canvas.drawColor(SCRIM);
            if (fillShader == null) {
                fillShader = gradient(FILL_ALPHA);
                railShader = gradient(255);
            }
            float level = position(shown);
            drawFill(canvas, level);
            float rail = sideways ? (railAtEnd ? bottom - dp(RAIL_INSET_DP) * 2 : top + dp(RAIL_INSET_DP) * 2)
                    : railAtEnd ? screenWidth - dp(RAIL_INSET_DP) : dp(RAIL_INSET_DP);
            drawRail(canvas, rail, level);
            drawCard(canvas, rail, level);
        }

        private void drawFill(Canvas canvas, float level) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(fillShader);
            if (sideways) {
                rect.set(0, top, level, bottom);
            } else {
                rect.set(0, level, screenWidth, bottom);
            }
            canvas.drawRect(rect, paint);
            paint.setShader(null);
            // The edge of the fill, where the fingers are.
            paint.setColor(Color.argb(210, 255, 255, 255));
            float half = dp(2);
            if (sideways) {
                rect.set(level - half, top, level + half, bottom);
            } else {
                rect.set(0, level - half, screenWidth, level + half);
            }
            canvas.drawRect(rect, paint);
        }

        private void drawRail(Canvas canvas, float rail, float level) {
            float half = dp(5);
            float start = position(0);
            float end = position(1);
            paint.setColor(Color.argb(70, 255, 255, 255));
            railRect(rail - half, rail + half, start, end);
            canvas.drawRoundRect(rect, half, half, paint);
            paint.setShader(railShader);
            railRect(rail - half, rail + half, start, level);
            canvas.drawRoundRect(rect, half, half, paint);
            paint.setShader(null);

            // Labels and long marks point to the middle of the screen, away from the screen edge.
            int inward = railAtEnd ? -1 : 1;
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextSize(sp(22));
            for (int i = 0; i < marks.length; i++) {
                float at = marks.length == 1 ? start : position(i / (marks.length - 1f));
                boolean labelled = marks[i] != null;
                paint.setColor(labelled ? Color.argb(235, 255, 255, 255) : Color.argb(130, 255, 255, 255));
                float reach = dp(labelled ? 26 : 14);
                float from = rail + inward * dp(10);
                railRect(Math.min(from, from + inward * reach), Math.max(from, from + inward * reach),
                        at - dp(labelled ? 2 : 1), at + dp(labelled ? 2 : 1));
                canvas.drawRect(rect, paint);
                if (labelled) {
                    drawMarkLabel(canvas, marks[i], from + inward * (reach + dp(10)), at, inward);
                }
            }

            paint.setColor(Color.WHITE);
            float radius = dp(20);
            canvas.drawCircle(sideways ? level : rail, sideways ? rail : level, radius, paint);
            paint.setShader(railShader);
            canvas.drawCircle(sideways ? level : rail, sideways ? rail : level, radius - dp(6), paint);
            paint.setShader(null);
        }

        /** A rectangle given across and along the rail, whichever way the rail runs. */
        private void railRect(float acrossLow, float acrossHigh, float alongA, float alongB) {
            float alongLow = Math.min(alongA, alongB);
            float alongHigh = Math.max(alongA, alongB);
            if (sideways) {
                rect.set(alongLow, acrossLow, alongHigh, acrossHigh);
            } else {
                rect.set(acrossLow, alongLow, acrossHigh, alongHigh);
            }
        }

        private void drawMarkLabel(Canvas canvas, String label, float across, float along, int inward) {
            textPaint.setColor(Color.argb(235, 255, 255, 255));
            float baselineShift = (textPaint.ascent() + textPaint.descent()) / 2;
            if (sideways) {
                textPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(label, along, inward > 0 ? across - textPaint.ascent() : across - textPaint.descent(),
                        textPaint);
            } else {
                textPaint.setTextAlign(inward > 0 ? Paint.Align.LEFT : Paint.Align.RIGHT);
                canvas.drawText(label, across, along - baselineShift, textPaint);
            }
        }

        /** The value and what it is, beside the rail and level with the fingers. */
        private void drawCard(Canvas canvas, float rail, float level) {
            float padding = dp(28);
            float iconSize = dp(34);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextSize(sp(26));
            float titleWidth = iconSize + dp(12) + textPaint.measureText(title);
            float titleHeight = Math.max(iconSize, textPaint.descent() - textPaint.ascent());
            float titleAscent = textPaint.ascent();
            float titleDescent = textPaint.descent();
            textPaint.setTextSize(valueTextSize());
            float valueWidth = textPaint.measureText(value);
            float valueAscent = textPaint.ascent();
            float valueHeight = textPaint.descent() - valueAscent;
            float width = Math.max(titleWidth, valueWidth) + padding * 2;
            float height = titleHeight + dp(6) + valueHeight + padding * 1.5f;

            float left;
            float cardTop;
            if (sideways) {
                // Above a rail in the lower half, below one in the upper half.
                float clear = dp(92);
                cardTop = railAtEnd ? rail - clear - height : rail + clear;
                left = Math.max(dp(24), Math.min(screenWidth - dp(24) - width, level - width / 2));
            } else {
                float clear = dp(150);
                left = railAtEnd ? rail - clear - width : rail + clear;
                cardTop = Math.max(top + dp(24), Math.min(bottom - dp(24) - height, level - height / 2));
            }
            paint.setColor(CARD);
            rect.set(left, cardTop, left + width, cardTop + height);
            canvas.drawRoundRect(rect, dp(28), dp(28), paint);

            float rowCenter = cardTop + padding * 0.75f + titleHeight / 2;
            if (iconDrawable != null) {
                iconDrawable.setBounds(Math.round(left + padding), Math.round(rowCenter - iconSize / 2),
                        Math.round(left + padding + iconSize), Math.round(rowCenter + iconSize / 2));
                iconDrawable.draw(canvas);
            }
            textPaint.setTextSize(sp(26));
            textPaint.setColor(MUTED);
            canvas.drawText(title, left + padding + iconSize + dp(12), rowCenter - (titleAscent + titleDescent) / 2,
                    textPaint);
            textPaint.setTextSize(valueTextSize());
            textPaint.setColor(Color.WHITE);
            canvas.drawText(value, left + padding, cardTop + padding * 0.75f + titleHeight + dp(6) - valueAscent,
                    textPaint);
        }

        /** A word, such as a fan profile, is smaller than a number. */
        private float valueTextSize() {
            return sp(!value.isEmpty() && Character.isLetter(value.charAt(0)) ? 58 : 84);
        }

        private float dp(int value) {
            return value * getResources().getDisplayMetrics().density;
        }

        private float sp(int value) {
            return value * getResources().getDisplayMetrics().scaledDensity;
        }
    }
}
