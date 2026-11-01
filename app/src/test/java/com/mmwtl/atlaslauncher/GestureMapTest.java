package com.mmwtl.atlaslauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.mmwtl.atlaslauncher.GestureMap.Gesture;
import com.mmwtl.atlaslauncher.MultiFingerGestures.Kind;

import org.junit.Test;

public class GestureMapTest {
    @Test
    public void defaultsAreTheGesturesBeforeTheyCouldBeChanged() {
        GestureMap map = new GestureMap();
        assertEquals("VOLUME", map.action(Gesture.TWO_CENTER_DOWN));
        assertEquals("TEMPERATURE_LEFT", map.action(Gesture.TWO_LEFT_UP));
        assertEquals("TEMPERATURE_RIGHT", map.action(Gesture.TWO_RIGHT_DOWN));
        assertEquals("BRIGHTNESS", map.action(Gesture.THREE_DOWN));
        assertEquals("FAN", map.action(Gesture.THREE_LEFT));
        assertEquals(GestureMap.HOME, map.action(Gesture.FOUR_PINCH));
        assertEquals(GestureMap.ALL_APPS, map.action(Gesture.FOUR_UP));
        assertFalse(map.bound(Gesture.TWO_SWIPE_LEFT));
        assertFalse(map.bound(Gesture.FOUR_TAP));
    }

    @Test
    public void adjustmentTakesBothDirections() {
        GestureMap map = new GestureMap();
        map.set(Gesture.THREE_DOWN, Kind.VOLUME.name());
        assertEquals("VOLUME", map.action(Gesture.THREE_UP));
        assertEquals("VOLUME", map.action(Gesture.THREE_DOWN));
    }

    @Test
    public void oneOffActionFreesTheOtherDirectionFromAnAdjustment() {
        GestureMap map = new GestureMap();
        map.set(Gesture.THREE_UP, GestureMap.BACK);
        assertEquals(GestureMap.BACK, map.action(Gesture.THREE_UP));
        assertEquals(GestureMap.NONE, map.action(Gesture.THREE_DOWN));
    }

    @Test
    public void oneOffActionKeepsAnotherOneOffInTheOtherDirection() {
        GestureMap map = new GestureMap();
        map.set(Gesture.THREE_UP, GestureMap.BACK);
        map.set(Gesture.THREE_DOWN, GestureMap.HOME);
        map.set(Gesture.THREE_UP, GestureMap.RECENTS);
        assertEquals(GestureMap.HOME, map.action(Gesture.THREE_DOWN));
    }

    @Test
    public void everySwipeThatTakesAnAdjustmentHasItsOppositeDirection() {
        for (Gesture gesture : Gesture.values()) {
            if (!gesture.takesAdjustment()) continue;
            assertEquals(gesture, gesture.opposite().opposite());
            assertTrue(gesture.opposite().takesAdjustment());
        }
        assertEquals(Gesture.TWO_SWIPE_LEFT, Gesture.TWO_SWIPE_RIGHT.opposite());
        assertEquals(Gesture.THREE_LEFT, Gesture.THREE_RIGHT.opposite());
        assertEquals(Gesture.TWO_RIGHT_DOWN, Gesture.TWO_RIGHT_UP.opposite());
    }

    @Test
    public void fourFingersDoNotTakeAdjustments() {
        assertFalse(Gesture.FOUR_UP.takesAdjustment());
        assertFalse(Gesture.FOUR_PINCH.takesAdjustment());
        assertFalse(Gesture.THREE_TAP.takesAdjustment());
    }

    @Test
    public void savedMapIsReadBack() {
        GestureMap map = new GestureMap();
        map.set(Gesture.TWO_SWIPE_LEFT, GestureMap.APP_PREFIX + "com.example/.Main");
        map.set(Gesture.FOUR_PINCH, GestureMap.NONE);
        GestureMap read = GestureMap.parse(map.toString());
        assertEquals("app:com.example/.Main", read.action(Gesture.TWO_SWIPE_LEFT));
        assertFalse(read.bound(Gesture.FOUR_PINCH));
        assertEquals("VOLUME", read.action(Gesture.TWO_CENTER_UP));
    }

    @Test
    public void missingAndUnknownGesturesKeepTheDefaults() {
        GestureMap read = GestureMap.parse("FUTURE_GESTURE=BACK\nnot a line\nFOUR_UP=BACK\n");
        assertEquals(GestureMap.BACK, read.action(Gesture.FOUR_UP));
        assertEquals(GestureMap.HOME, read.action(Gesture.FOUR_PINCH));
        assertEquals("VOLUME", GestureMap.parse(null).action(Gesture.TWO_CENTER_UP));
    }

    @Test
    public void kindRecognisesOnlyAdjustments() {
        assertEquals(Kind.FAN, GestureMap.kind("FAN"));
        assertNull(GestureMap.kind(GestureMap.HOME));
        assertNull(GestureMap.kind(GestureMap.APP_PREFIX + "x/.Y"));
    }
}
