package com.mmwtl.atlaslauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.mmwtl.atlaslauncher.MultiFingerGestures.Decision;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MultiFingerGesturesTest {
    private static final int DOWN = MultiFingerGestures.ACTION_DOWN;
    private static final int UP = MultiFingerGestures.ACTION_UP;
    private static final int MOVE = MultiFingerGestures.ACTION_MOVE;
    private static final int CANCEL = MultiFingerGestures.ACTION_CANCEL;
    private static final int POINTER_DOWN = MultiFingerGestures.ACTION_POINTER_DOWN;
    private static final int POINTER_UP = MultiFingerGestures.ACTION_POINTER_UP;

    private final List<String> events = new ArrayList<>();
    private MultiFingerGestures gestures;
    private long time;

    @Before
    public void setUp() {
        gestures = new MultiFingerGestures(1440, (kind, steps) -> events.add(kind + ":" + steps));
    }

    /** Sends one event 16 ms after the previous one; pointer ids are the indices of the pairs. */
    private Decision send(int action, int actionId, float... xy) {
        time += 16;
        int count = xy.length / 2;
        int[] ids = new int[count];
        float[] xs = new float[count];
        float[] ys = new float[count];
        for (int i = 0; i < count; i++) {
            ids[i] = i;
            xs[i] = xy[2 * i];
            ys[i] = xy[2 * i + 1];
        }
        return gestures.onEvent(action, actionId, count, ids, xs, ys, time);
    }

    /** Fingers held still past the settling time. */
    private void settle(float... xy) {
        time += MultiFingerGestures.SETTLE_MS;
        send(MOVE, 0, xy);
    }

    private void twoFingersDown(float x1, float x2, float y) {
        send(DOWN, 0, x1, y);
        send(POINTER_DOWN, 1, x1, y, x2, y);
        settle(x1, y, x2, y);
    }

    private void fingersDown(float... xy) {
        send(DOWN, 0, xy[0], xy[1]);
        for (int n = 2; n * 2 <= xy.length; n++) {
            float[] part = new float[n * 2];
            System.arraycopy(xy, 0, part, 0, part.length);
            send(POINTER_DOWN, n - 1, part);
        }
        settle(xy);
    }

    @Test
    public void centreTwoFingersUpRaiseVolume() {
        twoFingersDown(600, 800, 1000);
        send(MOVE, 0, 600, 900, 800, 900);
        send(MOVE, 0, 600, 850, 800, 850);
        assertEquals(List.of("VOLUME:1", "VOLUME:1"), events);
    }

    @Test
    public void firstStepNeedsLongerTravel() {
        twoFingersDown(600, 800, 1000);
        assertEquals(Decision.UNDECIDED, send(MOVE, 0, 600, 930, 800, 930));
        assertTrue(events.isEmpty());
        assertEquals(Decision.CLAIMED, send(MOVE, 0, 600, 905, 800, 905));
        assertEquals(List.of("VOLUME:1"), events);
    }

    @Test
    public void earlyTriggerActsAfterOneStep() {
        gestures.setTrigger(MultiFingerGestures.TRIGGER_EARLY);
        twoFingersDown(600, 800, 1000);
        send(MOVE, 0, 600, 945, 800, 945);
        assertTrue(events.isEmpty());
        send(MOVE, 0, 600, 940, 800, 940);
        assertEquals(List.of("VOLUME:1"), events);
    }

    @Test
    public void lateTriggerNeedsOneAndAHalfSteps() {
        gestures.setTrigger(MultiFingerGestures.TRIGGER_LATE);
        twoFingersDown(600, 800, 1000);
        send(MOVE, 0, 600, 915, 800, 915);
        assertTrue(events.isEmpty());
        send(MOVE, 0, 600, 910, 800, 910);
        assertEquals(List.of("VOLUME:1"), events);
    }

    @Test
    public void centreTwoFingersDownLowerVolume() {
        twoFingersDown(600, 800, 1000);
        send(MOVE, 0, 600, 1100, 800, 1100);
        send(MOVE, 0, 600, 1220, 800, 1220);
        assertEquals(List.of("VOLUME:-1", "VOLUME:-2"), events);
    }

    @Test
    public void edgeZonesChooseTheTemperatureSide() {
        twoFingersDown(100, 250, 1000);
        send(MOVE, 0, 100, 870, 250, 870);
        send(UP, 0, 100, 870);
        twoFingersDown(1200, 1350, 1000);
        send(MOVE, 0, 1200, 870, 1350, 870);
        assertEquals(List.of("TEMPERATURE_LEFT:1", "TEMPERATURE_RIGHT:1"), events);
    }

    @Test
    public void fingersInDifferentZonesAreNotAGesture() {
        twoFingersDown(200, 700, 1000);
        assertEquals(Decision.PASS, send(MOVE, 0, 200, 800, 700, 800));
        assertTrue(events.isEmpty());
    }

    @Test
    public void threeFingersChangeBrightnessAnywhere() {
        fingersDown(100, 1000, 700, 1000, 1300, 1000);
        send(MOVE, 0, 100, 890, 700, 890, 1300, 890);
        assertEquals(List.of("BRIGHTNESS:1"), events);
    }

    @Test
    public void threeFingersSidewaysChangeTheFan() {
        fingersDown(400, 1000, 600, 1000, 500, 1150);
        send(MOVE, 0, 530, 1005, 730, 1005, 630, 1150);
        send(MOVE, 0, 610, 1010, 810, 1010, 710, 1160);
        send(MOVE, 0, 450, 1010, 650, 1010, 550, 1160);
        assertEquals(List.of("FAN:1", "FAN:1", "FAN:-1"), events);
    }

    @Test
    public void threeFingersKeepTheirFirstAxis() {
        fingersDown(400, 1000, 600, 1000, 500, 1150);
        send(MOVE, 0, 400, 890, 600, 890, 500, 1040);
        // After a brightness step a sideways drag is not the fan.
        send(MOVE, 0, 600, 890, 800, 890, 700, 1040);
        assertEquals(List.of("BRIGHTNESS:1"), events);
    }

    @Test
    public void oneFingerNeverTriggers() {
        assertEquals(Decision.PASS, send(DOWN, 0, 700, 1000));
        assertEquals(Decision.PASS, send(MOVE, 0, 700, 300));
        assertTrue(events.isEmpty());
    }

    @Test
    public void horizontalTwoFingerDragIsIgnored() {
        // Trace recorded on the head unit: both fingers slide right with a slight downward drift.
        twoFingersDown(418, 550, 1284);
        send(MOVE, 0, 459, 1301, 569, 1286);
        send(MOVE, 0, 604, 1332, 715, 1331);
        assertEquals(Decision.PASS, send(MOVE, 0, 943, 1371, 1051, 1398));
        assertTrue(events.isEmpty());
    }

    @Test
    public void movementWhileFingersSettleCountsOnceSettled() {
        send(DOWN, 0, 600, 1000);
        send(POINTER_DOWN, 1, 600, 1000, 800, 1000);
        send(MOVE, 0, 600, 900, 800, 900);
        assertTrue(events.isEmpty());
        settle(600, 900, 800, 900);
        assertEquals(List.of("VOLUME:1"), events);
    }

    @Test
    public void threeFingersOnTheWayToFourDoNotChangeBrightness() {
        send(DOWN, 0, 500, 900);
        send(POINTER_DOWN, 1, 500, 900, 900, 900);
        send(POINTER_DOWN, 2, 500, 900, 900, 900, 500, 1300);
        send(MOVE, 0, 500, 800, 900, 800, 500, 1200);
        send(POINTER_DOWN, 3, 500, 780, 900, 780, 500, 1180, 900, 1180);
        assertTrue(events.isEmpty());
    }

    @Test
    public void fingersLeftAfterAFourFingerTouchDoNothing() {
        fingersDown(500, 900, 900, 900, 500, 1300, 900, 1300);
        send(POINTER_UP, 3, 500, 900, 900, 900, 500, 1300, 900, 1300);
        send(POINTER_UP, 2, 500, 900, 900, 900, 500, 1300);
        settle(500, 900, 900, 900);
        assertEquals(Decision.PASS, send(MOVE, 0, 500, 700, 900, 700));
        assertTrue(events.isEmpty());
    }

    @Test
    public void oneGesturePerTouch() {
        twoFingersDown(600, 800, 1000);
        send(MOVE, 0, 600, 900, 800, 900);
        send(POINTER_DOWN, 2, 600, 900, 800, 900, 700, 1000);
        settle(600, 900, 800, 900, 700, 1000);
        assertEquals(Decision.CLAIMED, send(MOVE, 0, 600, 700, 800, 700, 700, 800));
        assertEquals(List.of("VOLUME:1"), events);
    }

    @Test
    public void sequenceIsClaimedFromTheFirstStepUntilTheNextTouch() {
        assertEquals(Decision.PASS, send(DOWN, 0, 600, 1000));
        assertEquals(Decision.UNDECIDED, send(POINTER_DOWN, 1, 600, 1000, 800, 1000));
        settle(600, 1000, 800, 1000);
        assertEquals(Decision.UNDECIDED, send(MOVE, 0, 600, 960, 800, 960));
        assertEquals(Decision.CLAIMED, send(MOVE, 0, 600, 900, 800, 900));
        assertEquals(Decision.CLAIMED, send(POINTER_UP, 1, 600, 900, 800, 900));
        assertEquals(Decision.CLAIMED, send(UP, 0, 600, 900));
        assertEquals(Decision.PASS, send(DOWN, 0, 600, 1000));
    }

    @Test
    public void zoomWithOneFingerRestingIsNotAGesture() {
        twoFingersDown(600, 800, 1000);
        send(MOVE, 0, 600, 1000, 800, 900);
        send(MOVE, 0, 600, 1000, 800, 820);
        send(MOVE, 0, 600, 1000, 800, 650);
        assertTrue(events.isEmpty());
    }

    @Test
    public void verticalPinchIsNotAGesture() {
        twoFingersDown(700, 720, 1000);
        send(MOVE, 0, 700, 900, 720, 1100);
        assertEquals(Decision.PASS, send(MOVE, 0, 700, 800, 720, 1150));
        assertTrue(events.isEmpty());
    }

    @Test
    public void slightSpreadDriftKeepsTheGesture() {
        twoFingersDown(600, 800, 1000);
        send(MOVE, 0, 590, 905, 815, 910);
        send(MOVE, 0, 585, 845, 820, 850);
        assertEquals(List.of("VOLUME:1", "VOLUME:1"), events);
    }

    @Test
    public void directionCanReverseWithinOneTouch() {
        twoFingersDown(600, 800, 1000);
        send(MOVE, 0, 600, 850, 800, 850);
        send(MOVE, 0, 600, 1050, 800, 1050);
        assertEquals(List.of("VOLUME:1", "VOLUME:-2"), events);
    }

    @Test
    public void fourFingerPinchGoesHomeOnce() {
        fingersDown(500, 900, 900, 900, 500, 1300, 900, 1300);
        assertEquals(Decision.UNDECIDED, send(MOVE, 0, 520, 920, 880, 920, 520, 1280, 880, 1280));
        // A quarter closer is enough.
        assertEquals(Decision.CLAIMED, send(MOVE, 0, 555, 955, 845, 955, 555, 1245, 845, 1245));
        send(MOVE, 0, 680, 1080, 720, 1080, 680, 1120, 720, 1120);
        assertEquals(List.of("HOME:1"), events);
    }

    @Test
    public void earlyTriggerGoesHomeAfterAShorterPinch() {
        gestures.setTrigger(MultiFingerGestures.TRIGGER_EARLY);
        fingersDown(500, 900, 900, 900, 500, 1300, 900, 1300);
        send(MOVE, 0, 520, 920, 880, 920, 520, 1280, 880, 1280);
        assertTrue(events.isEmpty());
        send(MOVE, 0, 545, 945, 855, 945, 545, 1255, 855, 1255);
        assertEquals(List.of("HOME:1"), events);
    }

    @Test
    public void lateTriggerNeedsALongerPinch() {
        gestures.setTrigger(MultiFingerGestures.TRIGGER_LATE);
        fingersDown(500, 900, 900, 900, 500, 1300, 900, 1300);
        send(MOVE, 0, 555, 955, 845, 955, 555, 1245, 845, 1245);
        assertTrue(events.isEmpty());
        send(MOVE, 0, 585, 985, 815, 985, 585, 1215, 815, 1215);
        assertEquals(List.of("HOME:1"), events);
    }

    @Test
    public void pinchCountsFromTheMomentFourFingersLand() {
        send(DOWN, 0, 500, 900);
        send(POINTER_DOWN, 1, 500, 900, 900, 900);
        send(POINTER_DOWN, 2, 500, 900, 900, 900, 500, 1300);
        send(POINTER_DOWN, 3, 500, 900, 900, 900, 500, 1300, 900, 1300);
        send(MOVE, 0, 555, 955, 845, 955, 555, 1245, 845, 1245);
        assertEquals(List.of("HOME:1"), events);
    }

    @Test
    public void fifthFingerKeepsThePinchProgress() {
        fingersDown(500, 900, 900, 900, 500, 1300, 900, 1300);
        send(MOVE, 0, 530, 930, 870, 930, 530, 1270, 870, 1270);
        send(POINTER_DOWN, 4, 530, 930, 870, 930, 530, 1270, 870, 1270, 700, 1100);
        assertTrue(events.isEmpty());
        // Without the carried progress the fifth finger would restart the count here.
        send(MOVE, 0, 555, 955, 845, 955, 555, 1245, 845, 1245, 700, 1100);
        assertEquals(List.of("HOME:1"), events);
    }

    @Test
    public void fiveFingersPinchGoesHomeToo() {
        fingersDown(500, 900, 900, 900, 500, 1300, 900, 1300, 700, 1400);
        send(MOVE, 0, 650, 1050, 750, 1050, 650, 1150, 750, 1150, 700, 1180);
        assertEquals(List.of("HOME:1"), events);
    }

    @Test
    public void fourFingersUpOpenAllAppsOnce() {
        fingersDown(600, 1000, 800, 1000, 600, 1200, 800, 1200);
        assertEquals(Decision.UNDECIDED, send(MOVE, 0, 600, 900, 800, 905, 600, 1100, 800, 1100));
        assertEquals(Decision.CLAIMED, send(MOVE, 0, 605, 840, 800, 845, 600, 1040, 795, 1045));
        send(MOVE, 0, 605, 600, 800, 600, 600, 800, 795, 800);
        assertEquals(List.of("ALL_APPS:1"), events);
    }

    @Test
    public void allAppsSwipeLengthFollowsTheTrigger() {
        gestures.setTrigger(MultiFingerGestures.TRIGGER_EARLY);
        fingersDown(600, 1000, 800, 1000, 600, 1200, 800, 1200);
        send(MOVE, 0, 600, 875, 800, 875, 600, 1075, 800, 1075);
        assertEquals(List.of("ALL_APPS:1"), events);
        send(UP, 0, 600, 875);
        events.clear();
        gestures.setTrigger(MultiFingerGestures.TRIGGER_LATE);
        fingersDown(600, 1000, 800, 1000, 600, 1200, 800, 1200);
        send(MOVE, 0, 600, 840, 800, 840, 600, 1040, 800, 1040);
        assertTrue(events.isEmpty());
        send(MOVE, 0, 600, 815, 800, 815, 600, 1015, 800, 1015);
        assertEquals(List.of("ALL_APPS:1"), events);
    }

    @Test
    public void fourFingersSpreadingDownOrOneFingerUpDoNothing() {
        fingersDown(600, 1000, 800, 1000, 600, 1200, 800, 1200);
        assertEquals(Decision.PASS, send(MOVE, 0, 400, 800, 1000, 800, 400, 1400, 1000, 1400));
        send(UP, 0, 400, 800);
        fingersDown(600, 1000, 800, 1000, 600, 1200, 800, 1200);
        assertEquals(Decision.PASS, send(MOVE, 0, 600, 1100, 800, 1100, 600, 1300, 800, 1300));
        send(UP, 0, 600, 1100);
        fingersDown(600, 1000, 800, 1000, 600, 1200, 800, 1200);
        send(MOVE, 0, 600, 1000, 800, 1000, 600, 1200, 800, 500);
        assertTrue(events.isEmpty());
    }

    @Test
    public void threeFingerPinchDoesNotChangeBrightness() {
        fingersDown(500, 900, 900, 900, 700, 1300);
        send(MOVE, 0, 560, 860, 840, 860, 700, 1140);
        send(MOVE, 0, 620, 820, 780, 820, 700, 1000);
        assertTrue(events.isEmpty());
    }

    @Test
    public void cancelEndsTheGesture() {
        twoFingersDown(600, 800, 1000);
        send(CANCEL, 0, 600, 1000, 800, 1000);
        send(MOVE, 0, 600, 800);
        assertTrue(events.isEmpty());
    }
}
