package com.mmwtl.atlaslauncher;

/**
 * Recognises vertical two- and three-finger swipes from pointer samples. Two fingers adjust volume
 * in the centre of the screen and the driver or passenger temperature near the left or right edge;
 * three fingers adjust brightness anywhere. Four or more fingers pinched together go home, and
 * swiped up open all apps. Every finger has to travel the same way and the fingers have to keep
 * their spread, so pinch zoom, rotation and a zoom with one finger resting stay with the app.
 *
 * <p>Fingers land and lift one by one, so a gesture waits until the finger count has settled,
 * a touch belongs to the most fingers it has had, and it makes at most one gesture. Has no
 * Android dependencies, so it runs in JVM tests.
 */
final class MultiFingerGestures {
    enum Kind { VOLUME, TEMPERATURE_LEFT, TEMPERATURE_RIGHT, BRIGHTNESS, HOME, ALL_APPS }

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
        /** {@code steps} is positive for an upward swipe and negative for a downward one. */
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
    static final int TEMPERATURE_STEP_PX = 80;
    static final int BRIGHTNESS_STEP_PX = 70;
    /** The first step of a swipe needs this much more travel than the next ones. */
    static final float FIRST_STEP_FACTOR = 1.5f;
    /** After the finger count changes, movement only moves the starting point for this long. */
    static final long SETTLE_MS = 200;
    /** Spread change that is always tolerated; beyond it, it must stay under half the travel. */
    static final int SPREAD_TOLERANCE_PX = 40;
    /** A pinch has brought the fingers to this share of their starting spread. */
    static final float PINCH_RATIO = 0.6f;
    static final int ALL_APPS_SWIPE_PX = 150;

    private final int screenWidth;
    private final Listener listener;
    private float[] xs = new float[0];
    private float[] ys = new float[0];
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
        if (xs.length < pointerCount) {
            xs = new float[pointerCount];
            ys = new float[pointerCount];
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
        } else if (kind != null || manyFingers) {
            if (time < settleUntil) {
                anchor();
            } else if (manyFingers) {
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
        settleUntil = time + SETTLE_MS;
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
        System.arraycopy(ys, 0, fingerAnchorYs, 0, count);
    }

    private void followMany() {
        float spread = spread();
        float rise = startY - mean(ys);
        if (spread < startSpread * PINCH_RATIO) {
            finish(Kind.HOME);
        } else if (rise >= ALL_APPS_SWIPE_PX
                && Math.abs(spread - startSpread) <= Math.max(SPREAD_TOLERANCE_PX, rise / 2)
                && everyFingerMoved(-1, ALL_APPS_SWIPE_PX / 2f)) {
            finish(Kind.ALL_APPS);
        } else if (spread > startSpread / PINCH_RATIO || -rise > ALL_APPS_SWIPE_PX / 2f
                || Math.abs(mean(xs) - startX) > ALL_APPS_SWIPE_PX) {
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
        int stepPx = kind == Kind.VOLUME ? VOLUME_STEP_PX
                : kind == Kind.BRIGHTNESS ? BRIGHTNESS_STEP_PX : TEMPERATURE_STEP_PX;
        // A mostly horizontal drag is not ours; it stays ignored until the finger count changes.
        if (Math.abs(dx) > stepPx && Math.abs(dx) > Math.abs(dy)) {
            kind = null;
            return;
        }
        // Fingers moving apart or together are a zoom, whatever their middle does.
        float spreadChange = Math.abs(spread() - startSpread);
        if (spreadChange > SPREAD_TOLERANCE_PX && spreadChange > Math.abs(mean(ys) - startY) / 2) {
            kind = null;
            return;
        }
        float rise = -dy;
        float needed = stepped ? stepPx : stepPx * FIRST_STEP_FACTOR;
        int direction = rise > 0 ? 1 : -1;
        if (Math.abs(rise) < needed || !everyFingerMoved(-direction, needed / 2)) {
            return;
        }
        int steps = stepped ? (int) (rise / stepPx) : direction;
        anchorY -= stepped ? steps * stepPx : direction * needed;
        System.arraycopy(ys, 0, fingerAnchorYs, 0, count);
        stepped = true;
        claimed = true;
        listener.onSteps(kind, steps);
    }

    /** Whether each finger has moved at least {@code distance} along {@code direction} (+1 is down). */
    private boolean everyFingerMoved(int direction, float distance) {
        for (int i = 0; i < count; i++) {
            if ((ys[i] - fingerAnchorYs[i]) * direction < distance) {
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
