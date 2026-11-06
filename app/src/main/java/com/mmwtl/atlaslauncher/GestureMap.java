package com.mmwtl.atlaslauncher;

import java.util.EnumMap;
import java.util.Map;

/**
 * What each gesture does. An action is either one of the adjustments (the name of a
 * {@link MultiFingerGestures.Kind}), which the fingers move for as long as they stay on the screen
 * and which therefore takes both directions of a swipe, or a one-off action that fires once per
 * touch. Saved as one string in the settings; gestures missing from it keep their default, so the
 * defaults are what the app did before gestures could be changed. Has no Android dependencies.
 */
final class GestureMap {
    /** The two directions of a swipe stand next to each other; {@link #opposite} relies on it. */
    enum Gesture {
        TWO_CENTER_UP, TWO_CENTER_DOWN,
        TWO_LEFT_UP, TWO_LEFT_DOWN,
        TWO_RIGHT_UP, TWO_RIGHT_DOWN,
        TWO_SWIPE_RIGHT, TWO_SWIPE_LEFT,
        THREE_UP, THREE_DOWN,
        THREE_RIGHT, THREE_LEFT,
        FOUR_UP, FOUR_DOWN,
        FOUR_RIGHT, FOUR_LEFT,
        FOUR_PINCH, FOUR_SPREAD,
        THREE_TAP, FOUR_TAP;

        /** Two and three fingers can hold an adjustment; four move as one hand and only fire. */
        boolean takesAdjustment() {
            return ordinal() <= THREE_LEFT.ordinal();
        }

        Gesture opposite() {
            return values()[ordinal() ^ 1];
        }
    }

    static final String NONE = "NONE";
    static final String HOME = "HOME";
    static final String BACK = "BACK";
    static final String ALL_APPS = "ALL_APPS";
    static final String RECENTS = "RECENTS";
    // The notification shade opened by a key does not open on OneOS; a saved binding falls back to the default.
    private static final String REMOVED_NOTIFICATIONS = "NOTIFICATIONS";
    static final String SCREENSHOT = "SCREENSHOT";
    static final String PLAY_PAUSE = "PLAY_PAUSE";
    static final String NEXT_TRACK = "NEXT_TRACK";
    static final String PREVIOUS_TRACK = "PREVIOUS_TRACK";
    static final String MUTE = "MUTE";
    static final String CLIMATE_SCREEN = "CLIMATE_SCREEN";
    static final String CLIMATE_PANEL = "CLIMATE_PANEL";
    static final String CLIMATE_POWER = "CLIMATE_POWER";
    static final String CLIMATE_AUTO = "CLIMATE_AUTO";
    static final String AC = "AC";
    static final String RECIRCULATION = "RECIRCULATION";
    static final String DEFROST_FRONT = "DEFROST_FRONT";
    static final String DEFROST_REAR = "DEFROST_REAR";
    static final String WIFI = "WIFI";
    static final String BLUETOOTH = "BLUETOOTH";
    /** Followed by the flattened component of the activity to open. */
    static final String APP_PREFIX = "app:";
    /** Followed by the flattened component of any exported activity, opened without the launcher category. */
    static final String ACTIVITY_PREFIX = "activity:";

    private final Map<Gesture, String> actions = new EnumMap<>(Gesture.class);

    GestureMap() {
        for (Gesture gesture : Gesture.values()) actions.put(gesture, NONE);
        both(Gesture.TWO_CENTER_UP, MultiFingerGestures.Kind.VOLUME);
        both(Gesture.TWO_LEFT_UP, MultiFingerGestures.Kind.TEMPERATURE_LEFT);
        both(Gesture.TWO_RIGHT_UP, MultiFingerGestures.Kind.TEMPERATURE_RIGHT);
        both(Gesture.THREE_UP, MultiFingerGestures.Kind.BRIGHTNESS);
        both(Gesture.THREE_RIGHT, MultiFingerGestures.Kind.FAN);
        actions.put(Gesture.FOUR_PINCH, HOME);
        actions.put(Gesture.FOUR_UP, ALL_APPS);
    }

    private void both(Gesture gesture, MultiFingerGestures.Kind kind) {
        actions.put(gesture, kind.name());
        actions.put(gesture.opposite(), kind.name());
    }

    /** Reads what {@link #toString} wrote; null, unknown gestures and missing ones keep the defaults. */
    static GestureMap parse(String saved) {
        GestureMap map = new GestureMap();
        if (saved == null) return map;
        for (String line : saved.split("\n")) {
            int separator = line.indexOf('=');
            if (separator <= 0 || line.substring(separator + 1).equals(REMOVED_NOTIFICATIONS)) continue;
            try {
                map.actions.put(Gesture.valueOf(line.substring(0, separator)), line.substring(separator + 1));
            } catch (IllegalArgumentException unknown) {
                // Saved by a newer version.
            }
        }
        return map;
    }

    @Override
    public String toString() {
        StringBuilder saved = new StringBuilder();
        for (Map.Entry<Gesture, String> entry : actions.entrySet()) {
            saved.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        return saved.toString();
    }

    String action(Gesture gesture) {
        return gesture == null ? NONE : actions.get(gesture);
    }

    boolean bound(Gesture gesture) {
        return !NONE.equals(action(gesture));
    }

    /**
     * Binds an action. An adjustment takes the opposite direction of the swipe too, and a one-off
     * action frees it from the adjustment that was there, which needs both directions.
     */
    void set(Gesture gesture, String action) {
        if (gesture.takesAdjustment()) {
            if (kind(action) != null) actions.put(gesture.opposite(), action);
            else if (kind(action(gesture.opposite())) != null) actions.put(gesture.opposite(), NONE);
        }
        actions.put(gesture, action);
    }

    /** The adjustment this action is, or null for a one-off action. */
    static MultiFingerGestures.Kind kind(String action) {
        for (MultiFingerGestures.Kind kind : MultiFingerGestures.Kind.values()) {
            if (kind.name().equals(action)) return kind;
        }
        return null;
    }
}
