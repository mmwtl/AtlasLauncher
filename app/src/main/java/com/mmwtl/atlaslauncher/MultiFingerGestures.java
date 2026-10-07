package com.mmwtl.atlaslauncher;

/**
 * Recognises vertical two- and three-finger swipes from pointer samples. Two fingers adjust volume
 * in the centre of the screen and the driver or passenger temperature near the left or right edge;
 * three fingers adjust brightness anywhere. Has no Android dependencies, so it runs in JVM tests.
 */
final class MultiFingerGestures {
    enum Kind { VOLUME, TEMPERATURE_LEFT, TEMPERATURE_RIGHT, BRIGHTNESS }

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

    private final int screenWidth;
    private final Listener listener;
    private float[] xs = new float[0];
    private float[] ys = new float[0];
    private int count;
    private Kind kind;
    private float anchorX;
    private float anchorY;

    MultiFingerGestures(int screenWidth, Listener listener) {
        this.screenWidth = screenWidth;
        this.listener = listener;
    }

    /**
     * Feeds one motion event: {@code actionId} is the pointer id that went down or up, and the
     * arrays hold every pointer of the event, including the one that is going up.
     */
    synchronized void onEvent(int action, int actionId, int pointerCount, int[] ids, float[] x, float[] y) {
        if (action == ACTION_UP || action == ACTION_CANCEL) {
            setPointers(0, null, null, null, -1);
            return;
        }
        setPointers(pointerCount, ids, x, y, action == ACTION_POINTER_UP ? actionId : -1);
    }

    private void setPointers(int pointerCount, int[] ids, float[] x, float[] y, int skippedId) {
        int previous = count;
        if (xs.length < pointerCount) {
            xs = new float[pointerCount];
            ys = new float[pointerCount];
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
        if (count == 3) {
            kind = Kind.BRIGHTNESS;
        } else if (count == 2) {
            int zone = zone(xs[0]);
            if (zone == zone(xs[1])) {
                kind = zone == 0 ? Kind.TEMPERATURE_LEFT : zone == 1 ? Kind.VOLUME : Kind.TEMPERATURE_RIGHT;
            }
        }
        anchorX = mean(xs);
        anchorY = mean(ys);
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
        int steps = (int) (-dy / stepPx);
        if (steps != 0) {
            anchorY -= steps * stepPx;
            listener.onSteps(kind, steps);
        }
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
