package com.mmwtl.atlaslauncher;

/**
 * Recognises vertical two- and three-finger swipes from pointer samples. Two fingers adjust volume
 * in the centre of the screen and the driver or passenger temperature near the left or right edge;
 * three fingers adjust brightness anywhere, and a pinch of four or more fingers opens all apps. Every finger has to travel the same way and the
 * fingers have to keep their spread, so pinch zoom, rotation and a zoom with one finger resting
 * stay with the app. Has no Android dependencies, so it runs in JVM tests.
 */
final class MultiFingerGestures {
    enum Kind { VOLUME, TEMPERATURE_LEFT, TEMPERATURE_RIGHT, BRIGHTNESS, ALL_APPS }

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
    /** Spread change that is always tolerated; beyond it, it must stay under half the travel. */
    static final int SPREAD_TOLERANCE_PX = 40;
    /** A pinch has brought the fingers to this share of their starting spread. */
    static final float PINCH_RATIO = 0.6f;

    private final int screenWidth;
    private final Listener listener;
    private float[] xs = new float[0];
    private float[] ys = new float[0];
    private float[] fingerAnchorYs = new float[0];
    private int count;
    private Kind kind;
    private float anchorX;
    private float anchorY;
    private float startY;
    private float startSpread;
    private boolean claimed;

    MultiFingerGestures(int screenWidth, Listener listener) {
        this.screenWidth = screenWidth;
        this.listener = listener;
    }

    /**
     * Feeds one motion event: {@code actionId} is the pointer id that went down or up, and the
     * arrays hold every pointer of the event, including the one that is going up. Returns whether
     * the touch sequence is claimed: a gesture has made a step since the first finger went down,
     * so the rest of the sequence, up to and including the last finger up, belongs to it.
     */
    synchronized boolean onEvent(int action, int actionId, int pointerCount, int[] ids, float[] x, float[] y) {
        if (action == ACTION_DOWN) {
            claimed = false;
        }
        if (action == ACTION_UP || action == ACTION_CANCEL) {
            setPointers(0, null, null, null, -1);
        } else {
            setPointers(pointerCount, ids, x, y, action == ACTION_POINTER_UP ? actionId : -1);
        }
        return claimed;
    }

    private void setPointers(int pointerCount, int[] ids, float[] x, float[] y, int skippedId) {
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
            start();
        } else if (kind != null) {
            follow();
        }
    }

    /** The finger count changed: begin a new gesture, or none if the fingers do not fit one. */
    private void start() {
        kind = null;
        if (count >= 4) {
            kind = Kind.ALL_APPS;
        } else if (count == 3) {
            kind = Kind.BRIGHTNESS;
        } else if (count == 2) {
            int zone = zone(xs[0]);
            if (zone == zone(xs[1])) {
                kind = zone == 0 ? Kind.TEMPERATURE_LEFT : zone == 1 ? Kind.VOLUME : Kind.TEMPERATURE_RIGHT;
            }
        }
        anchorX = mean(xs);
        anchorY = mean(ys);
        startY = anchorY;
        startSpread = spread();
        System.arraycopy(ys, 0, fingerAnchorYs, 0, count);
    }

    private void follow() {
        if (kind == Kind.ALL_APPS) {
            if (spread() < startSpread * PINCH_RATIO) {
                // Once per touch: the pinch is done.
                kind = null;
                claimed = true;
                listener.onSteps(Kind.ALL_APPS, 1);
            }
            return;
        }
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
        int steps = (int) (-dy / stepPx);
        if (steps != 0 && everyFingerMoved(steps > 0 ? -1 : 1, stepPx / 2f)) {
            anchorY -= steps * stepPx;
            System.arraycopy(ys, 0, fingerAnchorYs, 0, count);
            claimed = true;
            listener.onSteps(kind, steps);
        }
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
