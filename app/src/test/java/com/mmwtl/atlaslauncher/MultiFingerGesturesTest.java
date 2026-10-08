package com.mmwtl.atlaslauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MultiFingerGesturesTest {
    private final List<String> events = new ArrayList<>();
    private MultiFingerGestures gestures;

    @Before
    public void setUp() {
        gestures = new MultiFingerGestures(1440, (kind, steps) -> events.add(kind + ":" + steps));
    }

    /** Sends one event; pointer ids are the indices of the coordinate pairs. */
    private boolean send(int action, int actionId, float... xy) {
        int count = xy.length / 2;
        int[] ids = new int[count];
        float[] xs = new float[count];
        float[] ys = new float[count];
        for (int i = 0; i < count; i++) {
            ids[i] = i;
            xs[i] = xy[2 * i];
            ys[i] = xy[2 * i + 1];
        }
        return gestures.onEvent(action, actionId, count, ids, xs, ys);
    }

    private void twoFingersDown(float x1, float x2, float y) {
        send(MultiFingerGestures.ACTION_DOWN, 0, x1, y);
        send(MultiFingerGestures.ACTION_POINTER_DOWN, 1, x1, y, x2, y);
    }

    @Test
    public void centreTwoFingersUpRaiseVolume() {
        twoFingersDown(600, 800, 1000);
        send(MultiFingerGestures.ACTION_MOVE, 0, 600, 940, 800, 940);
        send(MultiFingerGestures.ACTION_MOVE, 0, 600, 870, 800, 870);
        assertEquals(List.of("VOLUME:1", "VOLUME:1"), events);
    }

    @Test
    public void centreTwoFingersDownLowerVolume() {
        twoFingersDown(600, 800, 1000);
        send(MultiFingerGestures.ACTION_MOVE, 0, 600, 1130, 800, 1130);
        assertEquals(List.of("VOLUME:-2"), events);
    }

    @Test
    public void edgeZonesChooseTheTemperatureSide() {
        twoFingersDown(100, 250, 1000);
        send(MultiFingerGestures.ACTION_MOVE, 0, 100, 900, 250, 900);
        send(MultiFingerGestures.ACTION_UP, 0, 100, 900);
        twoFingersDown(1200, 1350, 1000);
        send(MultiFingerGestures.ACTION_MOVE, 0, 1200, 900, 1350, 900);
        assertEquals(List.of("TEMPERATURE_LEFT:1", "TEMPERATURE_RIGHT:1"), events);
    }

    @Test
    public void fingersInDifferentZonesAreNotAGesture() {
        twoFingersDown(200, 700, 1000);
        send(MultiFingerGestures.ACTION_MOVE, 0, 200, 800, 700, 800);
        assertTrue(events.isEmpty());
    }

    @Test
    public void threeFingersChangeBrightnessAnywhere() {
        send(MultiFingerGestures.ACTION_DOWN, 0, 100, 1000);
        send(MultiFingerGestures.ACTION_POINTER_DOWN, 1, 100, 1000, 700, 1000);
        send(MultiFingerGestures.ACTION_POINTER_DOWN, 2, 100, 1000, 700, 1000, 1300, 1000);
        send(MultiFingerGestures.ACTION_MOVE, 0, 100, 930, 700, 930, 1300, 930);
        assertEquals(List.of("BRIGHTNESS:1"), events);
    }

    @Test
    public void oneFingerNeverTriggers() {
        send(MultiFingerGestures.ACTION_DOWN, 0, 700, 1000);
        send(MultiFingerGestures.ACTION_MOVE, 0, 700, 300);
        assertTrue(events.isEmpty());
    }

    @Test
    public void horizontalTwoFingerDragIsIgnored() {
        // Trace recorded on the head unit: both fingers slide right with a slight downward drift.
        twoFingersDown(418, 550, 1284);
        send(MultiFingerGestures.ACTION_MOVE, 0, 459, 1301, 569, 1286);
        send(MultiFingerGestures.ACTION_MOVE, 0, 604, 1332, 715, 1331);
        send(MultiFingerGestures.ACTION_MOVE, 0, 943, 1371, 1051, 1398);
        assertTrue(events.isEmpty());
    }

    @Test
    public void secondFingerLandingAndLiftingMakesNoSteps() {
        send(MultiFingerGestures.ACTION_DOWN, 0, 600, 1000);
        send(MultiFingerGestures.ACTION_MOVE, 0, 600, 700);
        send(MultiFingerGestures.ACTION_POINTER_DOWN, 1, 600, 700, 800, 1200);
        send(MultiFingerGestures.ACTION_POINTER_UP, 1, 600, 700, 800, 1200);
        send(MultiFingerGestures.ACTION_MOVE, 0, 600, 690);
        assertTrue(events.isEmpty());
    }

    @Test
    public void liftingOneOfThreeFingersStartsATwoFingerGesture() {
        send(MultiFingerGestures.ACTION_DOWN, 0, 600, 1000);
        send(MultiFingerGestures.ACTION_POINTER_DOWN, 1, 600, 1000, 800, 1000);
        send(MultiFingerGestures.ACTION_POINTER_DOWN, 2, 600, 1000, 800, 1000, 700, 1000);
        send(MultiFingerGestures.ACTION_POINTER_UP, 2, 600, 1000, 800, 1000, 700, 1000);
        send(MultiFingerGestures.ACTION_MOVE, 0, 600, 920, 800, 920);
        assertEquals(List.of("VOLUME:1"), events);
    }

    @Test
    public void sequenceIsClaimedFromTheFirstStepUntilTheNextTouch() {
        assertFalse(send(MultiFingerGestures.ACTION_DOWN, 0, 600, 1000));
        assertFalse(send(MultiFingerGestures.ACTION_POINTER_DOWN, 1, 600, 1000, 800, 1000));
        assertFalse(send(MultiFingerGestures.ACTION_MOVE, 0, 600, 970, 800, 970));
        assertTrue(send(MultiFingerGestures.ACTION_MOVE, 0, 600, 930, 800, 930));
        assertTrue(send(MultiFingerGestures.ACTION_POINTER_UP, 1, 600, 930, 800, 930));
        assertTrue(send(MultiFingerGestures.ACTION_MOVE, 0, 600, 900));
        assertTrue(send(MultiFingerGestures.ACTION_UP, 0, 600, 900));
        assertFalse(send(MultiFingerGestures.ACTION_DOWN, 0, 600, 1000));
    }

    @Test
    public void ignoredTwoFingerTouchIsNeverClaimed() {
        twoFingersDown(418, 550, 1284);
        assertFalse(send(MultiFingerGestures.ACTION_MOVE, 0, 943, 1371, 1051, 1398));
        assertFalse(send(MultiFingerGestures.ACTION_UP, 0, 943, 1371));
    }

    @Test
    public void zoomWithOneFingerRestingIsNotAGesture() {
        twoFingersDown(600, 800, 1000);
        // Only the second finger travels up: the middle moves 75 px, a volume step without the checks.
        send(MultiFingerGestures.ACTION_MOVE, 0, 600, 1000, 800, 900);
        assertFalse(send(MultiFingerGestures.ACTION_MOVE, 0, 600, 1000, 800, 850));
        send(MultiFingerGestures.ACTION_MOVE, 0, 600, 1000, 800, 700);
        assertTrue(events.isEmpty());
    }

    @Test
    public void verticalPinchIsNotAGesture() {
        twoFingersDown(700, 720, 1000);
        send(MultiFingerGestures.ACTION_MOVE, 0, 700, 900, 720, 1100);
        send(MultiFingerGestures.ACTION_MOVE, 0, 700, 800, 720, 1150);
        assertTrue(events.isEmpty());
    }

    @Test
    public void slightSpreadDriftKeepsTheGesture() {
        twoFingersDown(600, 800, 1000);
        send(MultiFingerGestures.ACTION_MOVE, 0, 590, 930, 815, 935);
        send(MultiFingerGestures.ACTION_MOVE, 0, 585, 860, 820, 870);
        assertEquals(List.of("VOLUME:1", "VOLUME:1"), events);
    }

    @Test
    public void directionCanReverseWithinOneTouch() {
        twoFingersDown(600, 800, 1000);
        send(MultiFingerGestures.ACTION_MOVE, 0, 600, 870, 800, 870);
        send(MultiFingerGestures.ACTION_MOVE, 0, 600, 1000, 800, 1000);
        assertEquals(List.of("VOLUME:2", "VOLUME:-2"), events);
    }

    @Test
    public void cancelEndsTheGesture() {
        twoFingersDown(600, 800, 1000);
        send(MultiFingerGestures.ACTION_CANCEL, 0, 600, 1000, 800, 1000);
        send(MultiFingerGestures.ACTION_MOVE, 0, 600, 800);
        assertTrue(events.isEmpty());
    }
}
