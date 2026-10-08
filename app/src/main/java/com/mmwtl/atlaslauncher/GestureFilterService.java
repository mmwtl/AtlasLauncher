package com.mmwtl.atlaslauncher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import android.util.Log;
import android.view.Display;
import android.view.InputEvent;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;
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
 * only once they turn out not to be one, at most {@link #HOLD_MAX_MS} late. Volume goes through AudioManager, temperature and brightness through
 * GInputBridge broadcasts, as AtlasClimateWidget does. {@link GestureHud} shows each new
 * temperature or brightness; OneOS shows its own volume indicator. Four fingers pinched together go
 * home; four fingers swiped up do what the "All apps" dock button does.
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
    private static final int GLOBAL_AREA = Integer.MIN_VALUE;
    private static final int MAX_VOLUME_STEPS = 3;
    private static final long GIB_REFRESH_MS = 60_000;
    private static final String CHANNEL = "gestures";
    static final String EXTRA_PRIORITY = "priority";
    private static final long HOLD_MAX_MS = 500;

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean priority;
    private final GestureFilter filter = new GestureFilter();
    private final MultiFingerGestures gestures = new MultiFingerGestures(SCREEN_WIDTH, this::onSteps);
    // GInputBridge subscriptions live in its memory only, so they are repeated.
    private final Map<String, Float> gibValues = new ConcurrentHashMap<>();
    private final BroadcastReceiver gibReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            Object id = extras == null ? null : extras.get("id");
            Object area = extras == null ? null : extras.get("area");
            Object value = extras == null ? null : extras.get("value");
            try {
                gibValues.put(id.toString().trim() + "_" + area.toString().trim(),
                        Float.parseFloat(value.toString().trim()));
            } catch (RuntimeException error) {
                // Not a number or an incomplete reply: the value stays unknown.
            }
        }
    };
    private final Runnable gibRefresh = new Runnable() {
        @Override
        public void run() {
            refreshGib();
            actions.postDelayed(this, GIB_REFRESH_MS);
        }
    };
    private HandlerThread controlThread;
    private HandlerThread actionThread;
    private Handler control;
    private Handler actions;
    private AudioManager audio;
    private GestureHud hud;
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
                .setContentText("Два пальца: громкость и температура, три: яркость, четыре: домой и приложения")
                .build());
        controlThread = new HandlerThread("GesturesControl");
        controlThread.start();
        control = new Handler(controlThread.getLooper());
        actionThread = new HandlerThread("GesturesActions");
        actionThread.start();
        actions = new Handler(actionThread.getLooper());
        audio = getSystemService(AudioManager.class);
        hud = new GestureHud(this);
        IntentFilter replies = new IntentFilter();
        replies.addAction(GIB + ".PROPERTY_FLOAT_RESULT");
        replies.addAction(GIB + ".PROPERTY_FLOAT_CHANGED");
        registerReceiver(gibReceiver, replies);
        actions.post(gibRefresh);
        control.post(this::register);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int onStartCommand(Intent intent, int flags, int startId) {
        // HOME passes the current value; a restart after the process died rereads the file it writes.
        priority = intent != null && intent.hasExtra(EXTRA_PRIORITY)
                ? intent.getBooleanExtra(EXTRA_PRIORITY, false)
                : getSharedPreferences(HomeActivity.PREFS, MODE_MULTI_PROCESS)
                        .getBoolean(HomeActivity.GESTURES_PRIORITY, false);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(gibReceiver);
        hud.dismiss();
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
                    gibStep(kind, BRIGHTNESS, GLOBAL_AREA, steps,
                            gibValues.getOrDefault(BRIGHTNESS_MIN_ID + "_" + GLOBAL_AREA, BRIGHTNESS_MIN_DEFAULT),
                            gibValues.getOrDefault(BRIGHTNESS_MAX_ID + "_" + GLOBAL_AREA, BRIGHTNESS_MAX_DEFAULT));
                    break;
                case HOME:
                    main.post(this::openHome);
                    break;
                case ALL_APPS:
                    main.post(this::openAllApps);
                    break;
            }
        });
    }

    /** Opens AtlasLauncher straight away when it is HOME, so the system does not pick Launcher3 first. */
    private void openHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolved != null && getPackageName().equals(resolved.activityInfo.packageName)) {
            home = new Intent(this, HomeActivity.class);
        }
        try {
            startActivity(home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException error) {
            Log.w(TAG, "Cannot open HOME", error);
        }
    }

    /**
     * Opens the chosen "All apps" activity directly, so GLauncher or another app does not show
     * HOME first; the built-in catalog is part of HOME. Runs from the background, which Android 11
     * allows only with the "display over other apps" permission; being HOME is not enough.
     */
    @SuppressWarnings("deprecation")
    private void openAllApps() {
        // HOME writes this preference in its own process; MULTI_PROCESS rereads the changed file.
        String target = getSharedPreferences(HomeActivity.PREFS, MODE_MULTI_PROCESS)
                .getString(HomeActivity.DRAWER_ACTIVITY, "");
        ComponentName component = ComponentName.unflattenFromString(target);
        if (component != null) {
            try {
                startActivity(new Intent(Intent.ACTION_MAIN).setComponent(component)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
                return;
            } catch (ActivityNotFoundException | SecurityException error) {
                Log.w(TAG, "Cannot open " + target + "; HOME will report it", error);
            }
        }
        startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(StockHomeRedirectService.EXTRA_OPEN_ALL_APPS, true));
    }

    /** Moves a GInputBridge property from its last known value; no value yet means ask and skip. */
    private void gibStep(MultiFingerGestures.Kind kind, int id, int area, float delta, float min, float max) {
        String key = id + "_" + area;
        Float current = gibValues.get(key);
        if (current == null || current < 0) {
            refreshGib();
            return;
        }
        float target = Math.max(min, Math.min(max, current + delta));
        // Shown at a limit too, so the driver sees why nothing changes.
        main.post(() -> hud.show(kind, target, min, max));
        if (target == current) {
            return;
        }
        // The bridge reports the real value back and corrects this guess.
        gibValues.put(key, target);
        sendGib("SET_FLOAT_PROPERTY", id, area, target);
    }

    private void refreshGib() {
        for (int[] property : new int[][] {{TEMPERATURE, TEMPERATURE_ROW_LEFT}, {TEMPERATURE, TEMPERATURE_ROW_RIGHT},
                {BRIGHTNESS, GLOBAL_AREA}}) {
            sendGib("LISTEN_PROPERTY_CHANGES", property[0], property[1], null);
            sendGib("GET_FLOAT_PROPERTY", property[0], property[1], null);
        }
        sendGib("GET_FLOAT_PROPERTY", BRIGHTNESS_MIN_ID, GLOBAL_AREA, null);
        sendGib("GET_FLOAT_PROPERTY", BRIGHTNESS_MAX_ID, GLOBAL_AREA, null);
    }

    private void sendGib(String action, int id, int area, Float value) {
        Intent intent = new Intent(GIB + "." + action).setPackage(GIB).putExtra("id", id).putExtra("area", area);
        if (value != null) {
            intent.putExtra("value", value);
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
            if (lastRelayed == null) {
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
