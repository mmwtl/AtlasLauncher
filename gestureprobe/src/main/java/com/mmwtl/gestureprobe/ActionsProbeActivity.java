package com.mmwtl.gestureprobe;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks whether an unprivileged app can read and change volume, brightness and temperature
 * the way the OEM gesture service does. Each button moves a value by one step; Restore returns
 * the values captured on the first read. Temperature and brightness go either through
 * GInputBridge broadcasts (the path of AtlasClimateWidget) or straight through ECarX adaptapi;
 * the toggle button switches between them. Nothing runs without a button press except the
 * read-only connect.
 */
public final class ActionsProbeActivity extends Activity {
    private static final String TAG = "ActionsProbe";
    // Verified against XCGestureService 1.0.20250623G(312) and ecarx.adaptapi.jar.
    private static final String ADAPT = "com.ecarx.xui.adaptapi.";
    private static final int TEMP = 268828928;
    private static final int TEMP_MAX = 268829184;
    private static final int TEMP_MIN = 268829440;
    private static final int TEMP_STEP = 268829696;
    private static final int BRIGHT_CSD = 688063744;
    private static final int BRIGHT_MAX = 538248448;
    private static final int BRIGHT_MIN = 538248704;
    private static final int ROW_LEFT = 1;
    private static final int ROW_RIGHT = 4;
    private static final int NO_ZONE = Integer.MIN_VALUE;
    private static final String GIB = "com.salat.gbinder";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private HandlerThread workThread;
    private Handler work;
    private AudioManager audio;
    // Car state belongs to the work thread.
    private Object car;
    private Object function;
    private Method getValue;
    private Method getValueNoZone;
    private Method setValue;
    private final Map<String, Float> gibValues = new ConcurrentHashMap<>();
    private final Map<String, Float> gibOriginals = new ConcurrentHashMap<>();
    private final BroadcastReceiver gibReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String id = extra(intent, "id");
            String area = extra(intent, "area");
            String value = extra(intent, "value");
            log("GIB <- " + action.substring(action.lastIndexOf('.') + 1)
                    + " id=" + id + " area=" + area + " value=" + value);
            try {
                gibValues.put(id + "_" + area, Float.parseFloat(value));
            } catch (RuntimeException ignored) {
                // Logged above; a non-numeric reply is a result in itself.
            }
        }
    };
    private boolean useGib = true;
    private Button pathButton;
    private float tempLeft0 = Float.NaN;
    private float tempRight0 = Float.NaN;
    private float bright0 = Float.NaN;
    private int volume0 = -1;
    private TextView logView;
    private ScrollView logScroll;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        workThread = new HandlerThread("ActionsProbeWork");
        workThread.start();
        work = new Handler(workThread.getLooper());
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        column.setPadding(padding, padding, padding, padding);
        TextView description = new TextView(this);
        description.setText(R.string.actions_description);
        column.addView(description);
        pathButton = button("Path: GIB", () -> {
            useGib = !useGib;
            pathButton.setText(useGib ? "Path: GIB" : "Path: DIRECT");
            log("PATH " + (useGib ? "GInputBridge broadcasts" : "direct ECarX adaptapi"));
        });
        addRow(column, button("Connect + read", () -> work.post(this::connect)), pathButton);
        addRow(column, button("Restore", () -> work.post(this::restore)),
                button("Clear log", () -> logView.setText("")));
        addRow(column, button("Temp L +", () -> work.post(() -> tempStep(ROW_LEFT, 1))),
                button("Temp L -", () -> work.post(() -> tempStep(ROW_LEFT, -1))));
        addRow(column, button("Temp R +", () -> work.post(() -> tempStep(ROW_RIGHT, 1))),
                button("Temp R -", () -> work.post(() -> tempStep(ROW_RIGHT, -1))));
        addRow(column, button("Bright +", () -> work.post(() -> brightStep(1))),
                button("Bright -", () -> work.post(() -> brightStep(-1))));
        addRow(column, button("Vol +", () -> volumeStep(1)), button("Vol -", () -> volumeStep(-1)));
        logScroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextIsSelectable(true);
        logScroll.addView(logView);
        column.addView(logScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(column);
        registerReceiver(gibReceiver, gibFilter());
        volume0 = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        log("START uid=" + android.os.Process.myUid() + " music volume=" + volume0
                + "/" + audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        if (state == null && getIntent().getBooleanExtra("connect", false)) {
            work.post(this::connect);
        }
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener((View view) -> action.run());
        return button;
    }

    private void addRow(LinearLayout column, Button first, Button second) {
        LinearLayout row = new LinearLayout(this);
        row.addView(first, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(second, new LinearLayout.LayoutParams(0, -2, 1));
        column.addView(row);
    }

    private static IntentFilter gibFilter() {
        IntentFilter filter = new IntentFilter();
        for (String name : new String[] {"PROPERTY_FLOAT_RESULT", "PROPERTY_FLOAT_CHANGED",
                "PROPERTY_INT_RESULT", "PROPERTY_INT_CHANGED"}) {
            filter.addAction(GIB + "." + name);
        }
        return filter;
    }

    private static String extra(Intent intent, String name) {
        Bundle extras = intent.getExtras();
        Object value = extras == null ? null : extras.get(name);
        return value == null ? null : value.toString().trim();
    }

    private void gib(String action, int id, int area, Float value) {
        Intent intent = new Intent(GIB + "." + action).setPackage(GIB)
                .putExtra("id", id).putExtra("area", area);
        if (value != null) {
            intent.putExtra("value", value);
        }
        log("GIB -> " + action + " id=" + id + " area=" + area + " value=" + value);
        try {
            sendBroadcast(intent);
        } catch (RuntimeException error) {
            log("GIB send failed: " + error);
        }
    }

    private void gibReadAll() {
        gib("LISTEN_PROPERTY_CHANGES", TEMP, ROW_LEFT, null);
        gib("LISTEN_PROPERTY_CHANGES", TEMP, ROW_RIGHT, null);
        gib("LISTEN_PROPERTY_CHANGES", BRIGHT_CSD, NO_ZONE, null);
        gib("GET_FLOAT_PROPERTY", TEMP, ROW_LEFT, null);
        gib("GET_FLOAT_PROPERTY", TEMP, ROW_RIGHT, null);
        gib("GET_FLOAT_PROPERTY", BRIGHT_CSD, NO_ZONE, null);
        gib("GET_INT_PROPERTY", BRIGHT_CSD, NO_ZONE, null);
    }

    /** Reads the value, waits for the reply, then writes the clamped neighbour. */
    private void gibStep(String name, int id, int area, float delta, float min, float max) {
        String key = id + "_" + area;
        gibValues.remove(key);
        gib("GET_FLOAT_PROPERTY", id, area, null);
        work.postDelayed(() -> {
            Float current = gibValues.get(key);
            if (current == null || current < 0) {
                log("GIB " + name + ": no usable reply (" + current + "), not writing");
                return;
            }
            gibOriginals.putIfAbsent(key, current);
            gib("SET_FLOAT_PROPERTY", id, area, Math.max(min, Math.min(max, current + delta)));
            work.postDelayed(() -> gib("GET_FLOAT_PROPERTY", id, area, null), 800);
        }, 600);
    }

    private void connect() {
        if (useGib) {
            gibReadAll();
            return;
        }
        try {
            Class<?> carClass = Class.forName(ADAPT + "car.Car");
            car = carClass.getMethod("create", Context.class).invoke(null, getApplicationContext());
            Class<?> connectable = Class.forName(ADAPT + "binder.IConnectable");
            Class<?> watcher = Class.forName(ADAPT + "binder.IConnectable$IConnectWatcher");
            log("CAR created " + car + " connectable=" + connectable.isInstance(car));
            Object proxy = Proxy.newProxyInstance(watcher.getClassLoader(), new Class<?>[] {watcher},
                    (self, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return method.getName().equals("equals") ? self == args[0]
                                    : method.getName().equals("hashCode") ? 0 : "watcher";
                        }
                        log("CAR " + method.getName());
                        if (method.getName().equals("onConnected")) {
                            work.post(this::onConnected);
                        }
                        return null;
                    });
            connectable.getMethod("registerConnectWatcher", watcher).invoke(car, proxy);
            connectable.getMethod("connect").invoke(car);
        } catch (ReflectiveOperationException | RuntimeException error) {
            log("CAR connect failed: " + unwrap(error));
        }
    }

    private void onConnected() {
        try {
            function = Class.forName(ADAPT + "car.ICar").getMethod("getICarFunction").invoke(car);
            log("CAR ICarFunction=" + (function == null ? "null" : "non-null"));
            if (function == null) {
                return;
            }
            Class<?> functionClass = Class.forName(ADAPT + "car.base.ICarFunction");
            getValue = functionClass.getMethod("getCustomizeFunctionValue", int.class, int.class);
            getValueNoZone = functionClass.getMethod("getCustomizeFunctionValue", int.class);
            setValue = functionClass.getMethod("setCustomizeFunctionValue", int.class, int.class,
                    float.class);
            readAll();
        } catch (ReflectiveOperationException | RuntimeException error) {
            log("CAR ICarFunction failed: " + unwrap(error));
        }
    }

    private void readAll() {
        float left = read("TEMP L", TEMP, ROW_LEFT);
        float right = read("TEMP R", TEMP, ROW_RIGHT);
        read("TEMP L min", TEMP_MIN, ROW_LEFT);
        read("TEMP L max", TEMP_MAX, ROW_LEFT);
        read("TEMP step", TEMP_STEP, ROW_LEFT);
        float bright = read("BRIGHT CSD", BRIGHT_CSD, NO_ZONE);
        readNoZone("BRIGHT min", BRIGHT_MIN);
        readNoZone("BRIGHT max", BRIGHT_MAX);
        if (Float.isNaN(tempLeft0)) {
            tempLeft0 = left;
            tempRight0 = right;
            bright0 = bright;
            log("ORIGINAL captured temp L=" + left + " R=" + right + " bright=" + bright
                    + " volume=" + volume0);
        }
    }

    private float read(String name, int id, int zone) {
        try {
            float value = (Float) getValue.invoke(function, id, zone);
            log("READ " + name + " = " + value);
            return value;
        } catch (ReflectiveOperationException | RuntimeException error) {
            log("READ " + name + " failed: " + unwrap(error));
            return Float.NaN;
        }
    }

    private float readNoZone(String name, int id) {
        try {
            float value = (Float) getValueNoZone.invoke(function, id);
            log("READ " + name + " = " + value);
            return value;
        } catch (ReflectiveOperationException | RuntimeException error) {
            log("READ " + name + " failed: " + unwrap(error));
            return Float.NaN;
        }
    }

    private void write(String name, int id, int zone, float value) {
        try {
            log("WRITE " + name + " " + value + " -> " + setValue.invoke(function, id, zone, value));
        } catch (ReflectiveOperationException | RuntimeException error) {
            log("WRITE " + name + " failed: " + unwrap(error));
        }
    }

    private boolean ready() {
        if (function == null) {
            log("Not connected: press Connect + read first");
            return false;
        }
        return true;
    }

    private void tempStep(int row, int direction) {
        if (useGib) {
            gibStep("TEMP row " + row, TEMP, row, direction * 0.5f, 16f, 28f);
            return;
        }
        if (!ready()) {
            return;
        }
        float current = read("TEMP row " + row, TEMP, row);
        float step = read("TEMP step", TEMP_STEP, ROW_LEFT);
        float min = read("TEMP min", TEMP_MIN, row);
        float max = read("TEMP max", TEMP_MAX, row);
        if (Float.isNaN(current) || Float.isNaN(min) || Float.isNaN(max)) {
            return;
        }
        float target = Math.max(min, Math.min(max, current + direction * (step > 0 ? step : 0.5f)));
        write("TEMP row " + row, TEMP, row, target);
        work.postDelayed(() -> read("TEMP row " + row + " after", TEMP, row), 700);
    }

    private void brightStep(int direction) {
        if (useGib) {
            gibStep("BRIGHT CSD", BRIGHT_CSD, NO_ZONE, direction, 1f, Float.MAX_VALUE);
            return;
        }
        if (!ready()) {
            return;
        }
        float current = read("BRIGHT CSD", BRIGHT_CSD, NO_ZONE);
        float min = readNoZone("BRIGHT min", BRIGHT_MIN);
        float max = readNoZone("BRIGHT max", BRIGHT_MAX);
        if (Float.isNaN(current) || Float.isNaN(min) || Float.isNaN(max)) {
            return;
        }
        write("BRIGHT CSD", BRIGHT_CSD, NO_ZONE, Math.max(min, Math.min(max, current + direction)));
        work.postDelayed(() -> read("BRIGHT CSD after", BRIGHT_CSD, NO_ZONE), 700);
    }

    private void volumeStep(int direction) {
        int before = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        try {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
            log("VOLUME adjust " + direction + " returned; before=" + before);
        } catch (RuntimeException error) {
            log("VOLUME adjust failed: " + error);
        }
        ui.postDelayed(() -> log("VOLUME after=" + audio.getStreamVolume(AudioManager.STREAM_MUSIC)),
                500);
    }

    private void restore() {
        for (Map.Entry<String, Float> original : gibOriginals.entrySet()) {
            String[] key = original.getKey().split("_");
            gib("SET_FLOAT_PROPERTY", Integer.parseInt(key[0]), Integer.parseInt(key[1]),
                    original.getValue());
        }
        if (function != null && !Float.isNaN(tempLeft0)) {
            write("TEMP L restore", TEMP, ROW_LEFT, tempLeft0);
            write("TEMP R restore", TEMP, ROW_RIGHT, tempRight0);
            write("BRIGHT restore", BRIGHT_CSD, NO_ZONE, bright0);
        } else {
            log("RESTORE: car values were not captured");
        }
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume0, 0);
            log("VOLUME restored to " + volume0);
        } catch (RuntimeException error) {
            log("VOLUME restore failed: " + error);
        }
    }

    private static String unwrap(Exception error) {
        Throwable cause = error instanceof InvocationTargetException
                ? ((InvocationTargetException) error).getTargetException() : error;
        return cause.toString();
    }

    private void log(String message) {
        String line = SystemClock.elapsedRealtime() + " " + message;
        Log.i(TAG, line);
        ui.post(() -> {
            logView.append(line + "\n");
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(gibReceiver);
        workThread.quitSafely();
        super.onDestroy();
    }
}
