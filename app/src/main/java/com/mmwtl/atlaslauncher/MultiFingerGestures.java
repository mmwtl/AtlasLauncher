package com.mmwtl.atlaslauncher;

/**
 * Recognises multi-finger swipes from pointer samples. Two fingers up and down adjust volume in
 * the centre of the screen and the driver or passenger temperature near the left or right edge;
 * three fingers anywhere adjust brightness up and down and the fan sideways. Four or more fingers pinched together go home, and
 * swiped up open all apps. Every finger has to travel the same way and the fingers have to keep
 * their spread, so pinch zoom, rotation and a zoom with one finger resting stay with the app.
 *
 * <p>Fingers land and lift one by one, so a two- or three-finger gesture waits until the finger
 * count has settled, a touch belongs to the most fingers it has had, and it makes at most one
 * gesture. Four or more fingers start at once, as nothing above them could be mistaken for them,
 * and a fifth finger keeps the progress already made. Has no
 * Android dependencies, so it runs in JVM tests.
 */
final class MultiFingerGestures {
    enum Kind { VOLUME, TEMPERATURE_LEFT, TEMPERATURE_RIGHT, BRIGHTNESS, FAN, HOME, ALL_APPS }

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
    /** Trigger levels for {@link #setTrigger}: how much travel a gesture needs before it acts. */
    static final int TRIGGER_EARLY = 0;
    static final int TRIGGER_NORMAL = 1;
    static final int TRIGGER_LATE = 2;
    // Per trigger level. The first step of a swipe needs this much more travel than the next ones.
    private static final float[] FIRST_STEP_FACTORS = {1f, 1.25f, 1.5f};
    // A pinch has brought the fingers to this share of their starting spread.
    private static final float[] PINCH_RATIOS = {0.8f, 0.75f, 0.6f};
    private static final int[] ALL_APPS_SWIPES_PX = {120, 150, 180};

    private final int screenWidth;
    private final Listener listener;
    private float firstStepFactor = FIRST_STEP_FACTORS[TRIGGER_NORMAL];
    private float pinchRatio = PINCH_RATIOS[TRIGGER_NORMAL];
    private int allAppsSwipePx = ALL_APPS_SWIPES_PX[TRIGGER_NORMAL];
    private float[] xs = new float[0];
    private float[] ys = new float[0];
    private float[] fingerAnchorXs = new float[0];
    private float[] fingerAnchorYs = new float[0];
    private int count;
    private int maxCount;
    private Kind kind;
    // Four or more fingers: a one-off pinch or swipe up, decided while they move.
    private boolean manyFingers;
    private boolean claimed;
    private boolean stepped;
    private long settleUntil;
    private float anchorX;
    private float anchorY;
    private float startX;
    private float startY;
    private float startSpread;

    MultiFingerGestures(int screenWidth, Listener listener) {
        this.screenWidth = screenWidth;
        this.listener = listener;
    }

    /** One of the {@code TRIGGER_} levels. */
    synchronized void setTrigger(int level) {
        firstStepFactor = FIRST_STEP_FACTORS[level];
        pinchRatio = PINCH_RATIOS[level];
        allAppsSwipePx = ALL_APPS_SWIPES_PX[level];
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
        }
        if (action == ACTION_UP || action == ACTION_CANCEL) {
            setPointers(0, null, null, null, -1, time);
        } else {
            setPointers(pointerCount, ids, x, y, action == ACTION_POINTER_UP ? actionId : -1, time);
        }
        return claimed ? Decision.CLAIMED : kind != null || manyFingers ? Decision.UNDECIDED : Decision.PASS;
    }

    private void setPointers(int pointerCount, int[] ids, float[] x, float[] y, int skippedId, long time) {
        int previous = count;
        boolean wasMany = manyFingers;
        float pinched = wasMany ? spread() / startSpread : 1;
        float risen = wasMany ? startY - mean(ys) : 0;
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
                // Four and five fingers alike: carry the pinch or swipe over to the new set of fingers.
                startSpread = spread() / pinched;
                startY += risen;
                for (int i = 0; i < count; i++) {
                    fingerAnchorYs[i] += risen;
                }
            }
        } else if ((kind != null || manyFingers) && time >= settleUntil) {
            if (manyFingers) {
                followMany();
            } else {
                follow();
            }
        }
    }

    /** The finger count changed: begin a new gesture, or none if the fingers do not fit one. */
    private void start(long time) {
        kind = null;
        manyFingers = false;
        maxCount = Math.max(maxCount, count);
        // One gesture per touch, and only with as many fingers as the touch has had (4 and 5 alike).
        if (!claimed && Math.min(count, 4) == Math.min(maxCount, 4)) {
            if (count >= 4) {
                manyFingers = true;
            } else if (count == 3) {
                kind = Kind.BRIGHTNESS;
            } else if (count == 2) {
                int zone = zone(xs[0]);
                if (zone == zone(xs[1])) {
                    kind = zone == 0 ? Kind.TEMPERATURE_LEFT : zone == 1 ? Kind.VOLUME : Kind.TEMPERATURE_RIGHT;
                }
            }
        }
        stepped = false;
        settleUntil = manyFingers ? time : time + SETTLE_MS;
        anchor();
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

    private void followMany() {
        float spread = spread();
        float rise = startY - mean(ys);
        if (spread < startSpread * pinchRatio) {
            finish(Kind.HOME);
        } else if (rise >= allAppsSwipePx
                && Math.abs(spread - startSpread) <= Math.max(SPREAD_TOLERANCE_PX, rise / 2)
                && everyFingerMoved(false, 1, allAppsSwipePx / 2f)) {
            finish(Kind.ALL_APPS);
        } else if (spread > startSpread / pinchRatio || -rise > allAppsSwipePx / 2f
                || Math.abs(mean(xs) - startX) > allAppsSwipePx) {
            // Spreading, moving down or sideways: not ours.
            manyFingers = false;
        }
    }

    private void finish(Kind done) {
        manyFingers = false;
        claimed = true;
        listener.onSteps(done, 1);
    }

    private void follow() {
        float dx = mean(xs) - anchorX;
        float dy = mean(ys) - anchorY;
        // Three fingers choose their axis with the first step: up and down is brightness, sideways the fan.
        if (count == 3 && !stepped) {
            kind = Math.abs(dx) > Math.abs(dy) ? Kind.FAN : Kind.BRIGHTNESS;
        }
        boolean sideways = kind == Kind.FAN;
        // Positive along the axis is up or right.
        float along = sideways ? dx : -dy;
        float across = sideways ? dy : dx;
        int stepPx = kind == Kind.VOLUME ? VOLUME_STEP_PX : kind == Kind.BRIGHTNESS ? BRIGHTNESS_STEP_PX
                : kind == Kind.FAN ? FAN_STEP_PX : TEMPERATURE_STEP_PX;
        // A drag mostly across the axis is not ours; it stays ignored until the finger count changes.
        if (Math.abs(across) > stepPx && Math.abs(across) > Math.abs(along)) {
            kind = null;
            return;
        }
        // Fingers moving apart or together are a zoom, whatever their middle does.
        float travel = sideways ? mean(xs) - startX : mean(ys) - startY;
        float spreadChange = Math.abs(spread() - startSpread);
        if (spreadChange > SPREAD_TOLERANCE_PX && spreadChange > Math.abs(travel) / 2) {
            kind = null;
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
        listener.onSteps(kind, steps);
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
