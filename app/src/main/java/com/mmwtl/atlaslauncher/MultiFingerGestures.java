package com.mmwtl.atlaslauncher;

import com.mmwtl.atlaslauncher.GestureMap.Gesture;

/**
 * Recognises multi-finger gestures from pointer samples and reports what {@link GestureMap} binds
 * to them. Two fingers swipe up and down in three zones of the screen (the centre and each edge)
 * or sideways anywhere; three fingers swipe in four directions anywhere; four or more swipe,
 * pinch and spread anywhere; three and four fingers also tap. Every finger has to travel the same
 * way and the fingers have to keep their spread, so pinch zoom, rotation and a zoom with one finger
 * resting stay with the app. A gesture nothing is bound to is not recognised at all, so the app
 * keeps those touches.
 *
 * <p>Fingers land and lift one by one, so a two- or three-finger gesture waits until the finger
 * count has settled, a touch belongs to the most fingers it has had, and it makes at most one
 * gesture. Four or more fingers start at once, as nothing above them could be mistaken for them,
 * and a fifth finger keeps the progress already made. An adjustment moves step by step for as long
 * as the fingers go on; in scale mode (an option for each adjustment) it starts the same way, but
 * then the position of the fingers sets the value: the area from bottom to top, or from left to
 * right on a sideways swipe, spans the whole range. A one-off action fires once. Has no Android
 * dependencies, so it runs in JVM tests.
 */
final class MultiFingerGestures {
    enum Kind { VOLUME, TEMPERATURE_LEFT, TEMPERATURE_RIGHT, BRIGHTNESS, FAN }

    /** What the touch sequence is so far. */
    enum Decision {
        /** Not a gesture: the app gets the events. */
        PASS,
        /** Several fingers that may still become a gesture. */
        UNDECIDED,
        /** A gesture has acted; the rest of the sequence, up to the last finger up, is its own. */
        CLAIMED
    }

    interface Listener {
        /** {@code steps} is positive for a swipe up or right and negative for down or left. */
        void onSteps(Kind kind, int steps);

        /**
         * Scale mode: {@code level} is 0 for the minimum and 1 for the maximum, {@code sideways}
         * tells the axis and {@code cross} is where the fingers are on the other axis, in pixels.
         */
        default void onLevel(Kind kind, float level, boolean sideways, float cross) {
        }

        /** A one-off action from {@link GestureMap}. */
        default void onAction(String action) {
        }

        /** The last finger of a touch that made a gesture is up. */
        default void onEnd() {
        }
    }

    // Same numbers as MotionEvent, so the Binder side passes its actions straight through.
    static final int ACTION_DOWN = 0;
    static final int ACTION_UP = 1;
    static final int ACTION_MOVE = 2;
    static final int ACTION_CANCEL = 3;
    static final int ACTION_POINTER_DOWN = 5;
    static final int ACTION_POINTER_UP = 6;

    static final int VOLUME_STEP_PX = 60;
    static final int TEMPERATURE_STEP_PX = 60;
    static final int BRIGHTNESS_STEP_PX = 70;
    static final int FAN_STEP_PX = 80;
    /** After the finger count changes, no step is made for this long; the movement still counts. */
    static final long SETTLE_MS = 200;
    /** Spread change that is always tolerated; beyond it, it must stay under half the travel. */
    static final int SPREAD_TOLERANCE_PX = 40;
    /** Travel along an axis with nothing bound after which the touch stops being a candidate. */
    static final int UNBOUND_TRAVEL_PX = 60;
    /** Tap: from the first finger down to the last finger up, with no finger moving further. */
    static final long TAP_MAX_MS = 400;
    static final int TAP_SLOP_PX = 30;
    /** Trigger levels for {@link #setTrigger}: how much travel a gesture needs before it acts. */
    static final int TRIGGER_EARLY = 0;
    static final int TRIGGER_NORMAL = 1;
    static final int TRIGGER_LATE = 2;
    // Per trigger level. The first step of a swipe needs this much more travel than the next ones.
    private static final float[] FIRST_STEP_FACTORS = {1f, 1.25f, 1.5f};
    // A pinch has brought the fingers to this share of their starting spread.
    private static final float[] PINCH_RATIOS = {0.8f, 0.75f, 0.6f};
    // Travel of a swipe that fires a one-off action.
    private static final int[] SWIPES_PX = {120, 150, 180};
    /** Scale mode: the fingers reach the minimum or maximum this far from the edge of the area. */
    static final int SCALE_MARGIN_PX = 150;
    /** Bits for {@link #setScale}: which adjustments use scale mode. */
    static final int SCALE_VOLUME = 1;
    static final int SCALE_TEMPERATURE = 2;
    static final int SCALE_BRIGHTNESS = 4;
    static final int SCALE_FAN = 8;

    private final int screenWidth;
    private final int top;
    private final int bottom;
    private final Listener listener;
    private GestureMap map = new GestureMap();
    private float firstStepFactor = FIRST_STEP_FACTORS[TRIGGER_NORMAL];
    private float pinchRatio = PINCH_RATIOS[TRIGGER_NORMAL];
    private int swipePx = SWIPES_PX[TRIGGER_NORMAL];
    private int scaleKinds;
    private float[] xs = new float[0];
    private float[] ys = new float[0];
    private float[] fingerAnchorXs = new float[0];
    private float[] fingerAnchorYs = new float[0];
    private int count;
    private int maxCount;
    // Two or three fingers that may yet make a gesture.
    private boolean following;
    // Four or more fingers: a one-off pinch, spread or swipe, decided while they move.
    private boolean manyFingers;
    private boolean claimed;
    private boolean stepped;
    // The adjustment and axis in progress; until the first step they follow the fingers.
    private Kind kind;
    private boolean sideways;
    // Which zone both of two fingers landed in, or -1 when they are in different ones.
    private int zone;
    private long settleUntil;
    private long downTime;
    private boolean moved;
    private float anchorX;
    private float anchorY;
    private float startX;
    private float startY;
    private float startSpread;

    /** Gestures start between {@code top} and {@code bottom}; scale mode spans that height. */
    MultiFingerGestures(int screenWidth, int top, int bottom, Listener listener) {
        this.screenWidth = screenWidth;
        this.top = top;
        this.bottom = bottom;
        this.listener = listener;
    }

    /** One of the {@code TRIGGER_} levels. */
    synchronized void setTrigger(int level) {
        firstStepFactor = FIRST_STEP_FACTORS[level];
        pinchRatio = PINCH_RATIOS[level];
        swipePx = SWIPES_PX[level];
    }

    /** A combination of the {@code SCALE_} bits. */
    synchronized void setScale(int kinds) {
        scaleKinds = kinds;
    }

    synchronized void setMap(GestureMap map) {
        this.map = map;
    }

    /**
     * Feeds one motion event: {@code actionId} is the pointer id that went down or up, the arrays
     * hold every pointer of the event, including the one that is going up, and {@code time} is the
     * event time in milliseconds.
     */
    synchronized Decision onEvent(int action, int actionId, int pointerCount, int[] ids, float[] x, float[] y,
            long time) {
        if (action == ACTION_DOWN) {
            claimed = false;
            maxCount = 0;
            downTime = time;
            moved = false;
        }
        if (action == ACTION_UP || action == ACTION_CANCEL) {
            if (action == ACTION_UP) {
                tap(time);
            }
            setPointers(0, null, null, null, -1, time);
            if (claimed) {
                listener.onEnd();
            }
        } else {
            setPointers(pointerCount, ids, x, y, action == ACTION_POINTER_UP ? actionId : -1, time);
        }
        return claimed ? Decision.CLAIMED : following || manyFingers ? Decision.UNDECIDED : Decision.PASS;
    }

    private void tap(long time) {
        if (claimed || moved || maxCount < 3 || time - downTime > TAP_MAX_MS) {
            return;
        }
        Gesture gesture = maxCount == 3 ? Gesture.THREE_TAP : Gesture.FOUR_TAP;
        if (map.bound(gesture)) {
            fire(gesture);
        }
    }

    private void setPointers(int pointerCount, int[] ids, float[] x, float[] y, int skippedId, long time) {
        int previous = count;
        boolean wasMany = manyFingers;
        float pinched = wasMany ? spread() / startSpread : 1;
        float risen = wasMany ? startY - mean(ys) : 0;
        float shifted = wasMany ? mean(xs) - startX : 0;
        if (xs.length < pointerCount) {
            xs = new float[pointerCount];
            ys = new float[pointerCount];
            fingerAnchorXs = new float[pointerCount];
            fingerAnchorYs = new float[pointerCount];
        }
        int kept = 0;
        for (int i = 0; i < pointerCount; i++) {
            if (ids[i] == skippedId) {
                continue;
            }
            xs[kept] = x[i];
            ys[kept] = y[i];
            kept++;
        }
        count = kept;
        if (count != previous) {
            start(time);
            if (wasMany && manyFingers) {
                // Four and five fingers alike: carry the gesture over to the new set of fingers.
                startSpread = spread() / pinched;
                startY += risen;
                startX -= shifted;
                for (int i = 0; i < count; i++) {
                    fingerAnchorYs[i] += risen;
                    fingerAnchorXs[i] -= shifted;
                }
            }
        } else {
            for (int i = 0; i < count; i++) {
                moved |= Math.hypot(xs[i] - fingerAnchorXs[i], ys[i] - fingerAnchorYs[i]) > TAP_SLOP_PX;
            }
            if ((following || manyFingers) && time >= settleUntil) {
                if (manyFingers) {
                    followMany();
                } else {
                    follow();
                }
            }
        }
    }

    /** The finger count changed: begin a new gesture, or none if the fingers do not fit one. */
    private void start(long time) {
        kind = null;
        following = false;
        manyFingers = false;
        maxCount = Math.max(maxCount, count);
        // One gesture per touch, and only with as many fingers as the touch has had (4 and 5 alike).
        if (!claimed && Math.min(count, 4) == Math.min(maxCount, 4)) {
            if (count >= 4) {
                manyFingers = anyBound(Gesture.FOUR_UP, Gesture.FOUR_DOWN, Gesture.FOUR_RIGHT, Gesture.FOUR_LEFT,
                        Gesture.FOUR_PINCH, Gesture.FOUR_SPREAD, Gesture.FOUR_TAP);
            } else if (count == 3) {
                following = anyBound(Gesture.THREE_UP, Gesture.THREE_DOWN, Gesture.THREE_RIGHT, Gesture.THREE_LEFT,
                        Gesture.THREE_TAP);
            } else if (count == 2) {
                zone = zone(xs[0]) == zone(xs[1]) ? zone(xs[0]) : -1;
                following = anyBound(gesture(false, 1), gesture(false, -1), gesture(true, 1), gesture(true, -1));
            }
        }
        stepped = false;
        settleUntil = manyFingers ? time : time + SETTLE_MS;
        anchor();
    }

    private boolean anyBound(Gesture... gestures) {
        for (Gesture gesture : gestures) {
            if (map.bound(gesture)) {
                return true;
            }
        }
        return false;
    }

    private void anchor() {
        if (count == 0) {
            return;
        }
        anchorX = mean(xs);
        anchorY = mean(ys);
        startX = anchorX;
        startY = anchorY;
        startSpread = spread();
        anchorFingers();
    }

    private void anchorFingers() {
        System.arraycopy(xs, 0, fingerAnchorXs, 0, count);
        System.arraycopy(ys, 0, fingerAnchorYs, 0, count);
    }

    /** The gesture of two or three fingers going this way; +1 is right or up. Null if there is none. */
    private Gesture gesture(boolean side, int direction) {
        boolean positive = direction > 0;
        if (count == 3) {
            return side ? (positive ? Gesture.THREE_RIGHT : Gesture.THREE_LEFT)
                    : positive ? Gesture.THREE_UP : Gesture.THREE_DOWN;
        }
        if (side) {
            return positive ? Gesture.TWO_SWIPE_RIGHT : Gesture.TWO_SWIPE_LEFT;
        }
        switch (zone) {
            case 0: return positive ? Gesture.TWO_LEFT_UP : Gesture.TWO_LEFT_DOWN;
            case 1: return positive ? Gesture.TWO_CENTER_UP : Gesture.TWO_CENTER_DOWN;
            case 2: return positive ? Gesture.TWO_RIGHT_UP : Gesture.TWO_RIGHT_DOWN;
            default: return null;
        }
    }

    private void followMany() {
        float spread = spread();
        float rise = startY - mean(ys);
        float shift = mean(xs) - startX;
        if (spread < startSpread * pinchRatio || spread > startSpread / pinchRatio) {
            // Moving apart or together decides it, whatever the middle of the hand does.
            Gesture gesture = spread < startSpread ? Gesture.FOUR_PINCH : Gesture.FOUR_SPREAD;
            if (map.bound(gesture)) {
                fire(gesture);
            } else {
                manyFingers = false;
            }
            return;
        }
        boolean side = Math.abs(shift) > Math.abs(rise);
        int direction = (side ? shift : rise) > 0 ? 1 : -1;
        Gesture gesture = side ? (direction > 0 ? Gesture.FOUR_RIGHT : Gesture.FOUR_LEFT)
                : direction > 0 ? Gesture.FOUR_UP : Gesture.FOUR_DOWN;
        float travel = Math.max(Math.abs(shift), Math.abs(rise));
        if (!map.bound(gesture)) {
            // Moving where nothing is bound: not ours. Sideways drifts more before it counts.
            if (travel > (side ? swipePx : swipePx / 2f)) {
                manyFingers = false;
            }
        } else if (travel >= swipePx && Math.abs(spread - startSpread) <= Math.max(SPREAD_TOLERANCE_PX, travel / 2)
                && everyFingerMoved(side, direction, swipePx / 2f)) {
            fire(gesture);
        }
    }

    private void fire(Gesture gesture) {
        following = false;
        manyFingers = false;
        claimed = true;
        listener.onAction(map.action(gesture));
    }

    private void follow() {
        float dx = mean(xs) - anchorX;
        float dy = mean(ys) - anchorY;
        if (!stepped) {
            // Fingers choose their axis with the first step: up and down or sideways.
            sideways = Math.abs(dx) > Math.abs(dy);
            int way = (sideways ? dx : -dy) > 0 ? 1 : -1;
            Gesture gesture = gesture(sideways, way);
            if (!map.bound(gesture)) {
                // A drag along an axis with nothing bound is not ours; it stays ignored until the finger count changes.
                if (!map.bound(gesture(sideways, -way)) && Math.abs(sideways ? dx : dy) > UNBOUND_TRAVEL_PX) {
                    following = false;
                }
                return;
            }
            kind = GestureMap.kind(map.action(gesture));
            if (kind == null) {
                followOneOff(gesture, way, Math.abs(sideways ? dx : dy));
                return;
            }
        }
        boolean scale = (scaleKinds & scaleBit(kind)) != 0;
        if (scale && stepped) {
            // The gesture is already the fingers' own: they only move along the scale now.
            reportLevel();
            return;
        }
        // Positive along the axis is up or right.
        float along = sideways ? dx : -dy;
        float across = sideways ? dy : dx;
        int stepPx = kind == Kind.VOLUME ? VOLUME_STEP_PX : kind == Kind.BRIGHTNESS ? BRIGHTNESS_STEP_PX
                : kind == Kind.FAN ? FAN_STEP_PX : TEMPERATURE_STEP_PX;
        // A drag mostly across the axis is not ours; it stays ignored until the finger count changes.
        if (Math.abs(across) > stepPx && Math.abs(across) > Math.abs(along)) {
            following = false;
            return;
        }
        if (zoomed()) {
            following = false;
            return;
        }
        float needed = stepped ? stepPx : stepPx * firstStepFactor;
        int direction = along > 0 ? 1 : -1;
        if (Math.abs(along) < needed || !everyFingerMoved(sideways, direction, needed / 2)) {
            return;
        }
        int steps = stepped ? (int) (along / stepPx) : direction;
        float shift = stepped ? steps * stepPx : direction * needed;
        if (sideways) {
            anchorX += shift;
        } else {
            anchorY -= shift;
        }
        anchorFingers();
        stepped = true;
        claimed = true;
        if (scale) {
            reportLevel();
        } else {
            listener.onSteps(kind, steps);
        }
    }

    private void followOneOff(Gesture gesture, int direction, float travel) {
        if (zoomed()) {
            following = false;
        } else if (travel >= swipePx && everyFingerMoved(sideways, direction, swipePx / 2f)) {
            fire(gesture);
        }
    }

    /** Fingers moving apart or together are a zoom, whatever their middle does. */
    private boolean zoomed() {
        float travel = sideways ? mean(xs) - startX : mean(ys) - startY;
        float spreadChange = Math.abs(spread() - startSpread);
        return spreadChange > SPREAD_TOLERANCE_PX && spreadChange > Math.abs(travel) / 2;
    }

    private void reportLevel() {
        listener.onLevel(kind, level(sideways), sideways, sideways ? mean(ys) : mean(xs));
    }

    private static int scaleBit(Kind kind) {
        return kind == Kind.VOLUME ? SCALE_VOLUME : kind == Kind.BRIGHTNESS ? SCALE_BRIGHTNESS
                : kind == Kind.FAN ? SCALE_FAN : SCALE_TEMPERATURE;
    }

    /** Where the fingers are on the scale: 0 at the bottom or left, 1 at the top or right. */
    private float level(boolean sideways) {
        float position = sideways ? mean(xs) - SCALE_MARGIN_PX : bottom - SCALE_MARGIN_PX - mean(ys);
        float length = (sideways ? screenWidth : bottom - top) - 2 * SCALE_MARGIN_PX;
        return Math.max(0, Math.min(1, position / length));
    }

    /**
     * Whether each finger has moved at least {@code distance} in {@code direction} along the axis
     * since the last step; +1 is right when {@code sideways}, up otherwise.
     */
    private boolean everyFingerMoved(boolean sideways, int direction, float distance) {
        for (int i = 0; i < count; i++) {
            float moved = sideways ? xs[i] - fingerAnchorXs[i] : fingerAnchorYs[i] - ys[i];
            if (moved * direction < distance) {
                return false;
            }
        }
        return true;
    }

    /** Mean distance of the fingers from their middle point. */
    private float spread() {
        float middleX = mean(xs);
        float middleY = mean(ys);
        float sum = 0;
        for (int i = 0; i < count; i++) {
            sum += (float) Math.hypot(xs[i] - middleX, ys[i] - middleY);
        }
        return sum / count;
    }

    /** 0 for the left quarter, 1 for the centre half, 2 for the right quarter. */
    private int zone(float x) {
        return x < screenWidth / 4f ? 0 : x < screenWidth * 3 / 4f ? 1 : 2;
    }

    private float mean(float[] values) {
        float sum = 0;
        for (int i = 0; i < count; i++) {
            sum += values[i];
        }
        return sum / count;
    }
}
