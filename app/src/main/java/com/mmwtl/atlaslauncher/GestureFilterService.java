package com.mmwtl.atlaslauncher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-finger gestures through the OneOS input dispatcher (see {@link MultiFingerGestures}).
 * Runs in its own process: this app sits between the OEM filters and the system input, so any
 * failure ends only this process, which removes the filter and gives touches back to the OEM
 * ones. Every event is returned to the system unchanged before any other work until a gesture
 * makes its first step; then the app under the fingers gets a cancel and the rest of that touch
 * sequence stays with the gesture, so a map or list does not move along with it. In priority mode
 * (an option) touches that may still become a gesture are held back instead and handed to the app
 * only once they turn out not to be one, at most {@link #HOLD_MAX_MS} late. In scale mode (an
 * option for each adjustment) the fingers set the value directly once the gesture has started, and
 * {@link GestureScale} draws the scale over the screen. What each gesture does comes from
 * {@link GestureMap}. Volume goes through AudioManager; car values go through GInputBridge broadcasts,
 * as AtlasClimateWidget does, or through {@link DirectCar} without the bridge. {@link GestureHud} shows
 * each new temperature, brightness or fan setting; OneOS shows its own volume indicator. One-off
 * actions are in {@link GestureActions}, except the climate switches, which need the car values.
 */
public final class GestureFilterService extends Service {
    private static final String TAG = "AtlasGestures";
    // Binder layout verified against OneOS framework.jar and EcarxInputDispatcherService.
    private static final String SERVICE_NAME = "EcarxInputDispatcherService";
    private static final String DISPATCHER_DESCRIPTOR = "android.content.IInputDispatcherService";
    private static final String FILTER_DESCRIPTOR = "android.view.IInputFilter";
    private static final String HOST_DESCRIPTOR = "android.view.IInputFilterHost";
    private static final int REGISTER = 1;
    private static final int UNREGISTER = 2;
    private static final int INSTALL = 1;
    private static final int UNINSTALL = 2;
    private static final int FILTER_EVENT = 3;
    private static final int SEND_EVENT = 1;
    // WindowManagerPolicyConstants.FLAG_PASS_TO_USER: without it the dispatcher drops an injected key.
    private static final int POLICY_PASS_TO_USER = 0x40000000;
    private static final int PRIORITY_HIGH = 2;
    private static final int SCREEN_WIDTH = 1440;
    // Between the top panel strip (y < 64) and the climate panel strip (y >= 1728): the OEM
    // SystemUI filters there keep their touches. A filter with a higher priority takes over
    // every OEM filter it overlaps, including the screen gesture service.
    private static final Rect REGION = new Rect(0, 65, SCREEN_WIDTH, 1727);

    private static final String GIB = "com.salat.gbinder";
    private static final int TEMPERATURE = 268828928;
    private static final int TEMPERATURE_ROW_LEFT = 1;
    private static final int TEMPERATURE_ROW_RIGHT = 4;
    private static final float TEMPERATURE_STEP = 0.5f;
    private static final float TEMPERATURE_MIN = 16;
    private static final float TEMPERATURE_MAX = 28;
    private static final int BRIGHTNESS = 688063744;
    private static final int BRIGHTNESS_MIN_ID = 538248704;
    private static final int BRIGHTNESS_MAX_ID = 538248448;
    private static final float BRIGHTNESS_MIN_DEFAULT = 1;
    private static final float BRIGHTNESS_MAX_DEFAULT = 12;
    // Climate values as AtlasClimateWidget uses them on Atlas.
    private static final int CLIMATE_AUTO = 268501504;
    private static final int CLIMATE_AUTO_ZONE = 1;
    private static final int FAN_SPEED = 268566784;
    private static final int FAN_SPEED_AUTO = 268566794;
    private static final int FAN_SPEED_LEVEL_1 = 268566785;
    private static final int FAN_LEVELS = 9;
    private static final int AUTO_FAN_SETTING = 268567040;
    private static final int FAN_ZONE = 8;
    private static final int[] FAN_PROFILES = {268567044, 268567041, 268567042, 268567043, 268567045};
    private static final String[] FAN_PROFILE_NAMES = {"Тихо", "Мягко", "Комфорт", "Сильно", "Макс"};
    private static final int GLOBAL_AREA = Integer.MIN_VALUE;
    // Switches as AtlasClimateWidget's tiles send them: {function, zone, on value, off value}.
    private static final int[] CLIMATE_POWER = {268501248, GLOBAL_AREA, 1, 0};
    private static final int[] CLIMATE_AUTO_SWITCH = {CLIMATE_AUTO, CLIMATE_AUTO_ZONE, 1, 0};
    private static final int[] AC = {268501760, GLOBAL_AREA, 1, 0};
    private static final int[] RECIRCULATION = {268632320, GLOBAL_AREA, 268632321, 268632322};
    private static final int[] DEFROST_FRONT = {268698112, GLOBAL_AREA, 1, 0};
    private static final int[] DEFROST_REAR = {268698368, GLOBAL_AREA, 1, 0};
    private static final int[][] CLIMATE_SWITCHES = {CLIMATE_POWER, CLIMATE_AUTO_SWITCH, AC, RECIRCULATION,
            DEFROST_FRONT, DEFROST_REAR};
    private static final int MAX_VOLUME_STEPS = 3;
    private static final long GIB_REFRESH_MS = 60_000;
    private static final String CHANNEL = "gestures";
    static final String EXTRA_PRIORITY = "priority";
    static final String EXTRA_TRIGGER = "trigger";
    static final String EXTRA_SCALE = "scale";
    static final String EXTRA_SCALE_OVERLAY = "scale_overlay";
    static final String EXTRA_MAP = "map";
    static final String EXTRA_DIRECT = "direct";
    private static final long HOLD_MAX_MS = 500;
    // Within this time of a change the car may not report it yet, so a direct read could undo steps.
    private static final long DIRECT_SETTLE_MS = 1500;

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean priority;
    private final GestureFilter filter = new GestureFilter();
    private final MultiFingerGestures gestures = new MultiFingerGestures(SCREEN_WIDTH, REGION.top, REGION.bottom,
            new MultiFingerGestures.Listener() {
                @Override
                public void onSteps(MultiFingerGestures.Kind kind, int steps) {
                    GestureFilterService.this.onSteps(kind, steps);
                }

                @Override
                public void onLevel(MultiFingerGestures.Kind kind, float level, boolean sideways, float cross) {
                    GestureFilterService.this.onLevel(kind, level, sideways, cross);
                }

                @Override
                public void onAction(String action) {
                    GestureFilterService.this.onAction(action);
                }

                @Override
                public void onEnd() {
                    // Through the queue the values go through, so no value shown late brings the scale back.
                    actions.post(() -> main.post(scale::finish));
                }
            });
    // GInputBridge subscriptions live in its memory only, so they are repeated.
    private final Map<String, Float> gibValues = new ConcurrentHashMap<>();
    // Integer properties apart: their values are enum codes too large for a float.
    private final Map<String, Integer> gibInts = new ConcurrentHashMap<>();
    private final BroadcastReceiver gibReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (car != null) {
                return;
            }
            Bundle extras = intent.getExtras();
            Object id = extras == null ? null : extras.get("id");
            Object area = extras == null ? null : extras.get("area");
            Object value = extras == null ? null : extras.get("value");
            try {
                String key = id.toString().trim() + "_" + area.toString().trim();
                if (intent.getAction().contains("_INT_")) {
                    gibInts.put(key, (int) Math.round(Double.parseDouble(value.toString().trim())));
                } else {
                    gibValues.put(key, Float.parseFloat(value.toString().trim()));
                }
            } catch (RuntimeException error) {
                // Not a number or an incomplete reply: the value stays unknown.
            }
        }
    };
    private final Runnable gibRefresh = new Runnable() {
        @Override
        public void run() {
            if (car != null) {
                readFresh();
            } else {
                refreshGib();
            }
            actions.postDelayed(this, GIB_REFRESH_MS);
        }
    };
    private HandlerThread controlThread;
    private HandlerThread actionThread;
    private Handler control;
    private Handler actions;
    private AudioManager audio;
    private GestureHud hud;
    private GestureScale scale;
    private GestureActions oneOffActions;
    private volatile boolean scaleOverlay = true;
    // Null while car values go through the bridge. Action thread, apart from the check in the receiver.
    private volatile DirectCar car;
    private long lastCarChange;
    // Dispatcher state belongs to the control thread.
    private IBinder dispatcher;
    private boolean registrationAttempted;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "Жесты",
                NotificationManager.IMPORTANCE_LOW));
        startForeground(1, new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("Жесты включены")
                .setContentText("Что делает каждый жест, настраивается в настройках AtlasLauncher")
                .build());
        controlThread = new HandlerThread("GesturesControl");
        controlThread.start();
        control = new Handler(controlThread.getLooper());
        actionThread = new HandlerThread("GesturesActions");
        actionThread.start();
        actions = new Handler(actionThread.getLooper());
        audio = getSystemService(AudioManager.class);
        hud = new GestureHud(this);
        scale = new GestureScale(this, SCREEN_WIDTH, REGION.top, REGION.bottom, MultiFingerGestures.SCALE_MARGIN_PX);
        oneOffActions = new GestureActions(this, filter::sendKey, new GestureActions.Feedback() {
            @Override
            public void show(int icon, String title, String state) {
                main.post(() -> hud.showState(icon, title, state));
            }

            @Override
            public void showAction(int icon, String title, int glyph, String text) {
                main.post(() -> hud.showAction(icon, title, glyph, text));
            }
        });
        IntentFilter replies = new IntentFilter();
        replies.addAction(GIB + ".PROPERTY_FLOAT_RESULT");
        replies.addAction(GIB + ".PROPERTY_FLOAT_CHANGED");
        replies.addAction(GIB + ".PROPERTY_INT_RESULT");
        replies.addAction(GIB + ".PROPERTY_INT_CHANGED");
        registerReceiver(gibReceiver, replies);
        actions.post(gibRefresh);
        control.post(this::register);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int onStartCommand(Intent intent, int flags, int startId) {
        // HOME passes the current values; a restart after the process died rereads the file it writes.
        SharedPreferences prefs = getSharedPreferences(HomeActivity.PREFS, MODE_MULTI_PROCESS);
        boolean fromHome = intent != null && intent.hasExtra(EXTRA_PRIORITY);
        priority = fromHome ? intent.getBooleanExtra(EXTRA_PRIORITY, false)
                : prefs.getBoolean(HomeActivity.GESTURES_PRIORITY, false);
        gestures.setTrigger(fromHome ? intent.getIntExtra(EXTRA_TRIGGER, MultiFingerGestures.TRIGGER_NORMAL)
                : prefs.getInt(HomeActivity.GESTURES_TRIGGER, MultiFingerGestures.TRIGGER_NORMAL));
        gestures.setScale(fromHome ? intent.getIntExtra(EXTRA_SCALE, 0) : prefs.getInt(HomeActivity.GESTURES_SCALE, 0));
        scaleOverlay = fromHome ? intent.getBooleanExtra(EXTRA_SCALE_OVERLAY, true)
                : prefs.getBoolean(HomeActivity.GESTURES_SCALE_OVERLAY, true);
        gestures.setMap(GestureMap.parse(fromHome ? intent.getStringExtra(EXTRA_MAP)
                : prefs.getString(HomeActivity.GESTURES_MAP, null)));
        boolean direct = fromHome ? intent.getBooleanExtra(EXTRA_DIRECT, false)
                : prefs.getBoolean(HomeActivity.GESTURES_DIRECT, directByDefault(this));
        actions.post(() -> useDirectCar(direct));
        return START_STICKY;
    }

    /** Without the bridge there is nothing else to reach the car through. */
    static boolean directByDefault(Context context) {
        try {
            context.getPackageManager().getPackageInfo(GIB, 0);
            return false;
        } catch (PackageManager.NameNotFoundException missing) {
            return true;
        }
    }

    private void useDirectCar(boolean direct) {
        // Without the API the bridge stays in use.
        DirectCar next = direct && car == null ? DirectCar.connect(this) : direct ? car : null;
        if (next == car) {
            return;
        }
        car = next;
        gibValues.clear();
        gibInts.clear();
        refreshGib();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(gibReceiver);
        hud.dismiss();
        scale.dismiss();
        control.post(() -> {
            unregister();
            Log.i(TAG, "Filter removed; ending the gesture process");
            Process.killProcess(Process.myPid());
        });
        // Registration must not outlive the service even if the dispatcher hangs.
        main.postDelayed(() -> stopProcess("unregister did not finish within 2s"), 2_000);
        super.onDestroy();
    }

    private void register() {
        Point size = new Point();
        Display display = getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        display.getRealSize(size);
        if (size.x != SCREEN_WIDTH || size.y < REGION.bottom + 50) {
            Log.w(TAG, "Not registering: unexpected display " + size.x + "x" + size.y);
            stopSelf();
            return;
        }
        try {
            dispatcher = (IBinder) Class.forName("android.os.ServiceManager")
                    .getDeclaredMethod("getService", String.class).invoke(null, SERVICE_NAME);
            if (dispatcher == null || !DISPATCHER_DESCRIPTOR.equals(dispatcher.getInterfaceDescriptor())) {
                Log.w(TAG, "Not registering: input dispatcher service not found");
                dispatcher = null;
                stopSelf();
                return;
            }
            // Even a failed reply might follow a successful server-side registration.
            registrationAttempted = true;
            if (dispatcherTransaction(REGISTER)) {
                Log.i(TAG, "Filter registered for " + REGION.toShortString());
            } else {
                Log.w(TAG, "Dispatcher refused the filter");
                stopSelf();
            }
        } catch (ReflectiveOperationException | RemoteException | RuntimeException error) {
            Log.e(TAG, "Registration failed", error);
            stopSelf();
        }
    }

    private void unregister() {
        if (!registrationAttempted) {
            return;
        }
        try {
            if (!dispatcherTransaction(UNREGISTER)) {
                stopProcess("unregister returned false; registration state uncertain");
            }
        } catch (RemoteException | RuntimeException error) {
            stopProcess("unregister failed: " + error);
        }
    }

    private boolean dispatcherTransaction(int code) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DISPATCHER_DESCRIPTOR);
            if (code == REGISTER) {
                data.writeInt(PRIORITY_HIGH);
                data.writeInt(1);
                REGION.writeToParcel(data, 0);
            }
            data.writeStrongBinder(filter);
            if (!dispatcher.transact(code, data, reply, 0)) {
                throw new RemoteException("dispatcher transact returned false");
            }
            reply.readException();
            return reply.readInt() != 0;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private void stopProcess(String reason) {
        Log.e(TAG, "Ending the gesture process: " + reason);
        Process.killProcess(Process.myPid());
    }

    private void onSteps(MultiFingerGestures.Kind kind, int steps) {
        actions.post(() -> {
            switch (kind) {
                case VOLUME:
                    int direction = steps > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
                    for (int i = 0; i < Math.min(Math.abs(steps), MAX_VOLUME_STEPS); i++) {
                        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
                    }
                    break;
                case TEMPERATURE_LEFT:
                    gibStep(kind, TEMPERATURE, TEMPERATURE_ROW_LEFT, steps * TEMPERATURE_STEP,
                            TEMPERATURE_MIN, TEMPERATURE_MAX);
                    break;
                case TEMPERATURE_RIGHT:
                    gibStep(kind, TEMPERATURE, TEMPERATURE_ROW_RIGHT, steps * TEMPERATURE_STEP,
                            TEMPERATURE_MIN, TEMPERATURE_MAX);
                    break;
                case BRIGHTNESS:
                    gibStep(kind, BRIGHTNESS, GLOBAL_AREA, steps, brightnessLimit(BRIGHTNESS_MIN_ID, BRIGHTNESS_MIN_DEFAULT),
                            brightnessLimit(BRIGHTNESS_MAX_ID, BRIGHTNESS_MAX_DEFAULT));
                    break;
                case FAN:
                    fan(steps, Float.NaN, false, 0);
                    break;
            }
        });
    }

    private void onAction(String action) {
        actions.post(() -> {
            if (oneOffActions.run(action)) {
                return;
            }
            switch (action) {
                case GestureMap.CLIMATE_POWER:
                    toggle(CLIMATE_POWER, R.drawable.ic_power, "Климат", "Вкл", "Выкл");
                    break;
                case GestureMap.CLIMATE_AUTO:
                    toggle(CLIMATE_AUTO_SWITCH, R.drawable.ic_fan, "Климат AUTO", "Вкл", "Выкл");
                    break;
                case GestureMap.AC:
                    toggle(AC, R.drawable.ic_ac, "Кондиционер", "Вкл", "Выкл");
                    break;
                case GestureMap.RECIRCULATION:
                    toggle(RECIRCULATION, R.drawable.ic_recirculation, "Рециркуляция", "Вкл", "Выкл");
                    break;
                case GestureMap.DEFROST_FRONT:
                    toggle(DEFROST_FRONT, R.drawable.ic_defrost_front, "Обдув стекла", "Вкл", "Выкл");
                    break;
                case GestureMap.DEFROST_REAR:
                    toggle(DEFROST_REAR, R.drawable.ic_defrost_rear, "Обогрев заднего стекла", "Вкл", "Выкл");
                    break;
                default:
                    Log.w(TAG, "Unknown action " + action);
                    break;
            }
        });
    }

    /**
     * Flips a climate switch from its last known state. With no state yet it asks for one and
     * skips the gesture, saying so, or the gesture would seem to do nothing.
     */
    private void toggle(int[] function, int icon, String title, String onText, String offText) {
        readFresh();
        String key = function[0] + "_" + function[1];
        Integer current = gibInts.get(key);
        if (current == null) {
            refreshGib();
            main.post(() -> hud.showState(icon, title, "Нет данных"));
            return;
        }
        boolean on = current != function[2];
        int next = on ? function[2] : function[3];
        gibInts.put(key, next);
        sendInt(function[0], function[1], next);
        main.post(() -> hud.showState(icon, title, on ? onText : offText));
    }

    /** Scale mode: sets the value at {@code level} of its range, 0 to 1. */
    private void onLevel(MultiFingerGestures.Kind kind, float level, boolean sideways, float cross) {
        actions.post(() -> {
            // Without the permission the card and the overlay are both silent, so OneOS's own indicator stays.
            boolean overlay = scaleOverlay && GestureScale.canShow(this);
            switch (kind) {
                case VOLUME:
                    int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int volume = Math.round(level * max);
                    if (volume != audio.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, overlay ? 0 : AudioManager.FLAG_SHOW_UI);
                    }
                    if (overlay) {
                        showScale(kind, sideways, cross, R.drawable.ic_volume_up, "Громкость",
                                String.valueOf(volume), volume / (float) max, numberMarks(0, max));
                    }
                    break;
                case TEMPERATURE_LEFT:
                case TEMPERATURE_RIGHT:
                    float temperature = TEMPERATURE_MIN
                            + Math.round(level * (TEMPERATURE_MAX - TEMPERATURE_MIN) / TEMPERATURE_STEP) * TEMPERATURE_STEP;
                    boolean left = kind == MultiFingerGestures.Kind.TEMPERATURE_LEFT;
                    gibSet(kind, TEMPERATURE, left ? TEMPERATURE_ROW_LEFT : TEMPERATURE_ROW_RIGHT, temperature,
                            TEMPERATURE_MIN, TEMPERATURE_MAX, !overlay);
                    if (overlay) {
                        showScale(kind, sideways, cross, R.drawable.ic_temperature, left ? "Водитель" : "Пассажир",
                                String.format(Locale.ROOT, "%.1f°", temperature),
                                (temperature - TEMPERATURE_MIN) / (TEMPERATURE_MAX - TEMPERATURE_MIN), temperatureMarks());
                    }
                    break;
                case BRIGHTNESS:
                    float min = brightnessLimit(BRIGHTNESS_MIN_ID, BRIGHTNESS_MIN_DEFAULT);
                    float top = brightnessLimit(BRIGHTNESS_MAX_ID, BRIGHTNESS_MAX_DEFAULT);
                    int brightness = Math.round(min + level * (top - min));
                    gibSet(kind, BRIGHTNESS, GLOBAL_AREA, brightness, min, top, !overlay);
                    if (overlay && top > min) {
                        showScale(kind, sideways, cross, R.drawable.ic_brightness, "Яркость",
                                String.valueOf(brightness), (brightness - min) / (top - min),
                                numberMarks(Math.round(min), Math.round(top)));
                    }
                    break;
                case FAN:
                    fan(0, level, sideways, cross);
                    break;
                default:
                    break;
            }
        });
    }

    /** The car's limit; a car without the property reports -1 through the bridge. */
    private float brightnessLimit(int id, float fallback) {
        Float limit = gibValues.get(id + "_" + GLOBAL_AREA);
        return limit == null || limit < 0 ? fallback : limit;
    }

    private void showScale(MultiFingerGestures.Kind kind, boolean sideways, float cross, int icon, String title,
            String value, float fraction, String[] marks) {
        main.post(() -> scale.show(kind, sideways, cross, icon, title, value, fraction, marks));
    }

    /** A mark for every value; labelled at every one when they fit and every fifth otherwise. */
    private static String[] numberMarks(int min, int max) {
        String[] marks = new String[max - min + 1];
        int every = marks.length > 16 ? 5 : 1;
        for (int value = min; value <= max; value++) {
            if (value % every == 0 || value == min || value == max) {
                marks[value - min] = String.valueOf(value);
            }
        }
        return marks;
    }

    /** A mark for each half degree, labelled every two degrees. */
    private static String[] temperatureMarks() {
        String[] marks = new String[(int) ((TEMPERATURE_MAX - TEMPERATURE_MIN) / TEMPERATURE_STEP) + 1];
        for (int i = 0; i < marks.length; i += 4) {
            marks[i] = String.valueOf((int) (TEMPERATURE_MIN + i * TEMPERATURE_STEP));
        }
        return marks;
    }

    /** Moves a GInputBridge property from its last known value; no value yet means ask and skip. */
    private void gibStep(MultiFingerGestures.Kind kind, int id, int area, float delta, float min, float max) {
        readFresh();
        String key = id + "_" + area;
        Float current = gibValues.get(key);
        if (current == null || current < 0) {
            refreshGib();
            return;
        }
        gibSet(kind, id, area, current + delta, min, max, true);
    }

    /** Sets a GInputBridge property, kept within its range, unless it already has that value. */
    private void gibSet(MultiFingerGestures.Kind kind, int id, int area, float value, float min, float max,
            boolean showCard) {
        String key = id + "_" + area;
        Float current = gibValues.get(key);
        float target = Math.max(min, Math.min(max, value));
        // Shown at a limit too, so the driver sees why nothing changes.
        if (showCard) {
            main.post(() -> hud.show(kind, target, min, max));
        }
        if (current != null && target == current) {
            return;
        }
        // The bridge reports the real value back and corrects this guess.
        gibValues.put(key, target);
        lastCarChange = SystemClock.uptimeMillis();
        DirectCar direct = car;
        if (direct != null) {
            direct.setFloat(id, area, target);
        } else {
            sendGib("SET_FLOAT_PROPERTY", id, area, target);
        }
    }

    private void sendInt(int id, int area, int value) {
        lastCarChange = SystemClock.uptimeMillis();
        DirectCar direct = car;
        if (direct != null) {
            direct.setInt(id, area, value);
        } else {
            sendGib("SET_INT_PROPERTY", id, area, value);
        }
    }

    /**
     * Direct mode has no subscription, so a gesture starts from what the car says now. While
     * values are changing it goes on from its own: the car may not report the last one yet.
     */
    private void readFresh() {
        if (car != null && SystemClock.uptimeMillis() - lastCarChange > DIRECT_SETTLE_MS) {
            refreshGib();
        }
    }

    /**
     * In climate AUTO the car ignores a manual fan speed, so there the gesture moves the auto-fan
     * profile instead; outside AUTO it sets the speed, 1 to 9. Same rules as AtlasClimateWidget.
     * Moves by {@code steps}, or in scale mode goes to {@code level} of the range, 0 to 1.
     */
    private void fan(int steps, float level, boolean sideways, float cross) {
        boolean byLevel = !Float.isNaN(level);
        readFresh();
        boolean overlay = byLevel && scaleOverlay && GestureScale.canShow(this);
        Integer auto = gibInts.get(CLIMATE_AUTO + "_" + CLIMATE_AUTO_ZONE);
        Integer speed = gibInts.get(FAN_SPEED + "_" + FAN_ZONE);
        if (auto == null && speed == null) {
            refreshGib();
            return;
        }
        if (auto != null ? auto == 1 : speed == FAN_SPEED_AUTO) {
            String key = AUTO_FAN_SETTING + "_" + FAN_ZONE;
            Integer profile = gibInts.get(key);
            int index = -1;
            for (int i = 0; profile != null && i < FAN_PROFILES.length; i++) {
                if (FAN_PROFILES[i] == profile) {
                    index = i;
                }
            }
            // A profile outside these five snaps to the middle one, as in the climate widget.
            int next = byLevel ? Math.round(level * (FAN_PROFILES.length - 1)) : index < 0 ? FAN_PROFILES.length / 2
                    : Math.max(0, Math.min(FAN_PROFILES.length - 1, index + steps));
            if (overlay) {
                showScale(MultiFingerGestures.Kind.FAN, sideways, cross, R.drawable.ic_fan, "Обдув · авто",
                        FAN_PROFILE_NAMES[next], next / (FAN_PROFILES.length - 1f), FAN_PROFILE_NAMES);
            } else {
                main.post(() -> hud.showFan("Обдув · авто", FAN_PROFILE_NAMES[next],
                        next / (FAN_PROFILES.length - 1f)));
            }
            if (next != index) {
                gibInts.put(key, FAN_PROFILES[next]);
                sendInt(AUTO_FAN_SETTING, FAN_ZONE, FAN_PROFILES[next]);
            }
            return;
        }
        int speedLevel = speed == null ? 0 : speed - FAN_SPEED_LEVEL_1 + 1;
        if (speedLevel < 1 || speedLevel > FAN_LEVELS) {
            speedLevel = 0;
        }
        int next = byLevel ? 1 + Math.round(level * (FAN_LEVELS - 1)) : Math.max(1, Math.min(FAN_LEVELS, speedLevel + steps));
        if (overlay) {
            showScale(MultiFingerGestures.Kind.FAN, sideways, cross, R.drawable.ic_fan, "Обдув", String.valueOf(next),
                    (next - 1f) / (FAN_LEVELS - 1), numberMarks(1, FAN_LEVELS));
        } else {
            main.post(() -> hud.showFan("Обдув", String.valueOf(next), (next - 1f) / (FAN_LEVELS - 1)));
        }
        if (next != speedLevel) {
            gibInts.put(FAN_SPEED + "_" + FAN_ZONE, FAN_SPEED_LEVEL_1 + next - 1);
            sendInt(FAN_SPEED, FAN_ZONE, FAN_SPEED_LEVEL_1 + next - 1);
        }
    }

    /** Asks the car for every value gestures use: directly, or through the bridge, which answers later. */
    private void refreshGib() {
        for (int[] property : new int[][] {{TEMPERATURE, TEMPERATURE_ROW_LEFT}, {TEMPERATURE, TEMPERATURE_ROW_RIGHT},
                {BRIGHTNESS, GLOBAL_AREA}}) {
            refreshFloat(property[0], property[1], true);
        }
        refreshFloat(BRIGHTNESS_MIN_ID, GLOBAL_AREA, false);
        refreshFloat(BRIGHTNESS_MAX_ID, GLOBAL_AREA, false);
        for (int[] property : new int[][] {{CLIMATE_AUTO, CLIMATE_AUTO_ZONE}, {FAN_SPEED, FAN_ZONE},
                {AUTO_FAN_SETTING, FAN_ZONE}}) {
            refreshInt(property[0], property[1]);
        }
        for (int[] function : CLIMATE_SWITCHES) {
            refreshInt(function[0], function[1]);
        }
    }

    private void refreshFloat(int id, int area, boolean listen) {
        DirectCar direct = car;
        if (direct != null) {
            Float value = direct.getFloat(id, area);
            if (value != null) {
                gibValues.put(id + "_" + area, value);
            }
            return;
        }
        if (listen) {
            sendGib("LISTEN_PROPERTY_CHANGES", id, area, null);
        }
        sendGib("GET_FLOAT_PROPERTY", id, area, null);
    }

    private void refreshInt(int id, int area) {
        DirectCar direct = car;
        if (direct != null) {
            Integer value = direct.getInt(id, area);
            if (value != null) {
                gibInts.put(id + "_" + area, value);
            }
            return;
        }
        sendGib("LISTEN_PROPERTY_CHANGES", id, area, null);
        sendGib("GET_INT_PROPERTY", id, area, null);
    }

    private void sendGib(String action, int id, int area, Number value) {
        Intent intent = new Intent(GIB + "." + action).setPackage(GIB).putExtra("id", id).putExtra("area", area);
        if (value instanceof Integer) {
            intent.putExtra("value", value.intValue());
        } else if (value != null) {
            intent.putExtra("value", value.floatValue());
        }
        try {
            sendBroadcast(intent);
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot send " + action, error);
        }
    }

    private final class GestureFilter extends Binder {
        private final int[] ids = new int[32];
        private final float[] xs = new float[32];
        private final float[] ys = new float[32];
        private volatile IBinder host;
        // Touch sequence state, shared by Binder calls and the hold timeout under this object's lock.
        // The sequence belongs to a gesture:
        private boolean claimed;
        // Priority mode: events held back while the touch may still become a gesture, and whether
        // this touch has already been handed to the app, so it is not held again.
        private final List<MotionEvent> held = new ArrayList<>();
        private final List<Integer> heldFlags = new ArrayList<>();
        private boolean released;
        // What the app has seen last, to end its touch when a gesture takes over.
        private MotionEvent lastRelayed;
        private int lastFlags;
        private final Runnable holdTimeout = () -> {
            synchronized (this) {
                try {
                    flushHeld();
                } catch (RemoteException | RuntimeException error) {
                    stopProcess("hold flush failure: " + error);
                }
            }
        };

        GestureFilter() {
            attachInterface(null, FILTER_DESCRIPTOR);
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(FILTER_DESCRIPTOR);
                return true;
            }
            if (code != INSTALL && code != UNINSTALL && code != FILTER_EVENT) {
                return super.onTransact(code, data, reply, flags);
            }
            InputEvent event = null;
            try {
                data.enforceInterface(FILTER_DESCRIPTOR);
                if (code == INSTALL) {
                    host = data.readStrongBinder();
                    if (host == null) {
                        throw new IllegalStateException("install host=null");
                    }
                } else if (code == FILTER_EVENT) {
                    event = data.readInt() != 0 ? InputEvent.CREATOR.createFromParcel(data) : null;
                    int policyFlags = data.readInt();
                    if (event == null || host == null) {
                        throw new IllegalStateException("event or host is null");
                    }
                    if (event instanceof MotionEvent) {
                        filterMotion((MotionEvent) event, policyFlags);
                    } else {
                        relay(event, policyFlags);
                    }
                }
                // UNINSTALL keeps the host: the dispatcher caches the chosen filter until UP/CANCEL
                // even after unregister, and those last events must still reach the system.
                return true;
            } catch (RemoteException | RuntimeException error) {
                // The callback is oneway, so throwing would not make the dispatcher pass the event on.
                stopProcess("filter failure: " + error);
                return true;
            } finally {
                if (event instanceof MotionEvent) {
                    ((MotionEvent) event).recycle();
                }
            }
        }

        /**
         * Presses a key the way the OEM dispatcher sends its own Back: the filter host accepts any input
         * event and hands it to the system as trusted. Safe from any thread: the call is oneway.
         */
        void sendKey(int keyCode) {
            if (host == null) {
                return;
            }
            long now = SystemClock.uptimeMillis();
            try {
                for (int action : new int[] {KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
                    KeyEvent key = new KeyEvent(now, now, action, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                            KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY, InputDevice.SOURCE_KEYBOARD);
                    onMainDisplay(key);
                    relay(key, POLICY_PASS_TO_USER);
                }
            } catch (RemoteException | RuntimeException error) {
                Log.w(TAG, "Cannot send key " + keyCode, error);
            }
        }

        /** Otherwise the key goes to the focused display, which after boot may not be this one. */
        private void onMainDisplay(KeyEvent key) {
            try {
                KeyEvent.class.getMethod("setDisplayId", int.class).invoke(key, Display.DEFAULT_DISPLAY);
            } catch (ReflectiveOperationException error) {
                Log.d(TAG, "Key stays on the focused display: " + error);
            }
        }

        private void relay(InputEvent event, int policyFlags) throws RemoteException {
            Parcel outgoing = Parcel.obtain();
            try {
                outgoing.writeInterfaceToken(HOST_DESCRIPTOR);
                outgoing.writeInt(1);
                event.writeToParcel(outgoing, 0);
                outgoing.writeInt(policyFlags);
                if (!host.transact(SEND_EVENT, outgoing, null, IBinder.FLAG_ONEWAY)) {
                    throw new RemoteException("sendInputEvent transact=false");
                }
            } finally {
                outgoing.recycle();
            }
        }

        private synchronized void filterMotion(MotionEvent event, int policyFlags) throws RemoteException {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                flushHeld();
                claimed = false;
                released = false;
            }
            if (claimed) {
                // The rest of the touch is the gesture's own.
                detect(event);
                return;
            }
            boolean holding = priority;
            if (!holding) {
                relayTracked(event, policyFlags);
            }
            MultiFingerGestures.Decision decision = detect(event);
            if (decision == MultiFingerGestures.Decision.CLAIMED) {
                claimed = true;
                dropHeld();
                cancelAppTouch();
            } else if (!holding) {
                return;
            } else if (decision == MultiFingerGestures.Decision.UNDECIDED && !released) {
                hold(event, policyFlags);
            } else {
                if (event.getPointerCount() > 1) {
                    released = true;
                }
                flushHeld();
                relayTracked(event, policyFlags);
            }
        }

        private void relayTracked(MotionEvent event, int policyFlags) throws RemoteException {
            relay(event, policyFlags);
            if (lastRelayed != null) {
                lastRelayed.recycle();
            }
            lastRelayed = MotionEvent.obtain(event);
            lastFlags = policyFlags;
        }

        /**
         * Ends the touch the app has seen. The dispatcher still passes the final finger up on its own,
         * and the system drops it as that touch is already over.
         */
        private void cancelAppTouch() throws RemoteException {
            // A tap fires on the last finger up, and then the app's touch is already over.
            if (lastRelayed == null || lastRelayed.getActionMasked() == MotionEvent.ACTION_UP) {
                return;
            }
            lastRelayed.setAction(MotionEvent.ACTION_CANCEL);
            try {
                relay(lastRelayed, lastFlags);
            } finally {
                lastRelayed.recycle();
                lastRelayed = null;
            }
        }

        private void hold(MotionEvent event, int policyFlags) {
            if (held.isEmpty()) {
                control.postDelayed(holdTimeout, HOLD_MAX_MS);
            }
            held.add(MotionEvent.obtain(event));
            heldFlags.add(policyFlags);
        }

        /** Hands the held events to the app, late but in order; from then on this touch is the app's. */
        private void flushHeld() throws RemoteException {
            control.removeCallbacks(holdTimeout);
            if (held.isEmpty()) {
                return;
            }
            released = true;
            try {
                for (int i = 0; i < held.size(); i++) {
                    relayTracked(held.get(i), heldFlags.get(i));
                }
            } finally {
                dropHeld();
            }
        }

        private void dropHeld() {
            control.removeCallbacks(holdTimeout);
            for (MotionEvent event : held) {
                event.recycle();
            }
            held.clear();
            heldFlags.clear();
        }

        private MultiFingerGestures.Decision detect(MotionEvent event) {
            int count = Math.min(event.getPointerCount(), ids.length);
            for (int i = 0; i < count; i++) {
                ids[i] = event.getPointerId(i);
                xs[i] = event.getX(i);
                ys[i] = event.getY(i);
            }
            return gestures.onEvent(event.getActionMasked(), event.getPointerId(event.getActionIndex()),
                    count, ids, xs, ys, event.getEventTime());
        }
    }
}
