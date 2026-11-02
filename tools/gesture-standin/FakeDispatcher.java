import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.TreeMap;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Stands in for EcarxInputDispatcherService on the emulator, speaking the same Binder protocol. */
public class FakeDispatcher {
    static final String DISP = "android.content.IInputDispatcherService";
    static final String FILT = "android.view.IInputFilter";
    static final String HOST = "android.view.IInputFilterHost";
    static volatile IBinder filter;
    static final Object injector = injector();

    static Object injector() {
        try { return Class.forName("android.hardware.input.InputManager").getMethod("getInstance").invoke(null); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    static void inject(InputEvent e) {
        try {
            injector.getClass().getMethod("injectInputEvent", InputEvent.class, int.class).invoke(injector, e, 0);
        } catch (Exception x) { System.out.println("inject failed " + x); }
    }

    static final Binder host = new Binder() {
        { attachInterface(null, HOST); }
        @Override protected boolean onTransact(int code, Parcel d, Parcel r, int f) {
            if (code == INTERFACE_TRANSACTION) { r.writeString(HOST); return true; }
            if (code != 1) return false;
            d.enforceInterface(HOST);
            InputEvent ev = d.readInt() != 0 ? InputEvent.CREATOR.createFromParcel(d) : null;
            int flags = d.readInt();
            if (ev instanceof KeyEvent) System.out.println("HOST key " + ev + " flags=" + Integer.toHexString(flags));
            else if (((MotionEvent) ev).getActionMasked() != MotionEvent.ACTION_MOVE) System.out.println("HOST motion " + MotionEvent.actionToString(((MotionEvent) ev).getAction()) + " n=" + ((MotionEvent) ev).getPointerCount());
            inject(ev);
            return true;
        }
    };

    static final Binder dispatcher = new Binder() {
        { attachInterface(null, DISP); }
        @Override protected boolean onTransact(int code, Parcel d, Parcel r, int f) throws android.os.RemoteException {
            if (code == INTERFACE_TRANSACTION) { r.writeString(DISP); return true; }
            d.enforceInterface(DISP);
            if (code == 1) {
                int priority = d.readInt();
                int has = d.readInt();
                Rect rect = has != 0 ? Rect.CREATOR.createFromParcel(d) : null;
                IBinder b = d.readStrongBinder();
                System.out.println("REGISTER prio=" + priority + " " + rect + " " + b);
                Parcel p = Parcel.obtain();
                p.writeInterfaceToken(FILT);
                p.writeStrongBinder(host);
                b.transact(1, p, null, IBinder.FLAG_ONEWAY);
                p.recycle();
                filter = b;
                r.writeNoException();
                r.writeInt(1);
                return true;
            }
            if (code == 2) {
                System.out.println("UNREGISTER");
                filter = null;
                r.writeNoException();
                r.writeInt(1);
                return true;
            }
            return false;
        }
    };

    static synchronized void send(MotionEvent e) throws Exception {
        IBinder b = filter;
        if (b == null) { inject(e); return; }
        Parcel p = Parcel.obtain();
        p.writeInterfaceToken(FILT);
        p.writeInt(1);
        e.writeToParcel(p, 0);
        p.writeInt(0x62000000);
        b.transact(3, p, null, IBinder.FLAG_ONEWAY);
        p.recycle();
    }

    static long downTime;

    static MotionEvent ev(int action, float[][] pts, long t) {
        int n = pts.length;
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[n];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[n];
        for (int i = 0; i < n; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = i;
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = pts[i][0];
            coords[i].y = pts[i][1];
            coords[i].pressure = 1;
            coords[i].size = 1;
        }
        return MotionEvent.obtain(downTime, t, action, n, props, coords, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
    }

    static float[][] slice(float[][] p, int n) { float[][] r = new float[n][]; System.arraycopy(p, 0, r, 0, n); return r; }

    /** fingers x,y;x,y end offsets dx dy, spread factor, move ms, hold ms */
    static void gesture(float[][] pts, float dx, float dy, float scale, long ms, long hold) throws Exception {
        downTime = SystemClock.uptimeMillis();
        send(ev(MotionEvent.ACTION_DOWN, slice(pts, 1), downTime));
        for (int i = 1; i < pts.length; i++) {
            Thread.sleep(16);
            send(ev(MotionEvent.ACTION_POINTER_DOWN | (i << MotionEvent.ACTION_POINTER_INDEX_SHIFT), slice(pts, i + 1), SystemClock.uptimeMillis()));
        }
        float[][] cur = new float[pts.length][2];
        for (int i = 0; i < pts.length; i++) { cur[i][0] = pts[i][0]; cur[i][1] = pts[i][1]; }
        if (ms > 0) {
            Thread.sleep(pts.length >= 4 ? 16 : 230);
            send(ev(MotionEvent.ACTION_MOVE, cur, SystemClock.uptimeMillis()));
            float cx = 0, cy = 0;
            for (float[] p : pts) { cx += p[0] / pts.length; cy += p[1] / pts.length; }
            int steps = (int) (ms / 16);
            for (int s = 1; s <= steps; s++) {
                float t = s / (float) steps;
                float k = 1 + (scale - 1) * t;
                for (int i = 0; i < pts.length; i++) {
                    cur[i][0] = cx + (pts[i][0] - cx) * k + dx * t;
                    cur[i][1] = cy + (pts[i][1] - cy) * k + dy * t;
                }
                send(ev(MotionEvent.ACTION_MOVE, cur, SystemClock.uptimeMillis()));
                Thread.sleep(16);
            }
        }
        if (hold > 0) Thread.sleep(hold);
        for (int i = pts.length - 1; i >= 1; i--) {
            send(ev(MotionEvent.ACTION_POINTER_UP | (i << MotionEvent.ACTION_POINTER_INDEX_SHIFT), slice(cur, i + 1), SystemClock.uptimeMillis()));
            Thread.sleep(8);
        }
        send(ev(MotionEvent.ACTION_UP, slice(cur, 1), SystemClock.uptimeMillis()));
    }

    // ---- live input: the emulator's real touchscreen goes through the filter, as on the head unit ----
    static final int W = 1440, H = 1920, MAX = 32767, SLOTS = 11;
    static final int EV_SYN = 0, EV_KEY = 1, EV_ABS = 3;
    static final int ABS_SLOT = 0x2f, ABS_X = 0x35, ABS_Y = 0x36, ABS_ID = 0x39;
    static final int EVIOCGRAB = 0x40044590;
    static volatile int armed = 1;
    static volatile int held = 0;

    static void grab(FileDescriptor fd) throws Exception {
        Object os = Class.forName("libcore.io.Libcore").getField("os").get(null);
        for (Method m : os.getClass().getMethods()) {
            if (!m.getName().equals("ioctlInt")) continue;
            m.setAccessible(true);
            // EVIOCGRAB only looks at whether the argument is non-zero, and a pointer is.
            if (m.getParameterCount() == 2) m.invoke(os, fd, EVIOCGRAB);
            else m.invoke(os, fd, EVIOCGRAB, Class.forName("android.system.Int32Ref").getConstructor(int.class).newInstance(1));
            System.out.println("grabbed touchscreen");
            return;
        }
        throw new IllegalStateException("no ioctlInt");
    }

    /** Offsets of the imitated fingers around the mouse. */
    static float[][] layout(int n) {
        switch (n) {
            case 2: return new float[][] {{-70, 0}, {70, 0}};
            case 3: return new float[][] {{-100, 0}, {100, 0}, {0, 150}};
            case 4: case 5: return new float[][] {{-180, 0}, {-60, 0}, {60, 0}, {180, 0}};
            default: return new float[][] {{0, 0}};
        }
    }

    static TreeMap<Integer, float[]> current = new TreeMap<>();
    static int mode = 1;
    static float startY;

    static MotionEvent evMap(int action, TreeMap<Integer, float[]> m, long t) {
        int n = m.size(), i = 0;
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[n];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[n];
        for (Map.Entry<Integer, float[]> e : m.entrySet()) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = e.getKey();
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = e.getValue()[0];
            coords[i].y = e.getValue()[1];
            coords[i].pressure = 1;
            coords[i].size = 1;
            i++;
        }
        return MotionEvent.obtain(downTime, t, action, n, props, coords, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
    }

    static int indexOf(TreeMap<Integer, float[]> m, int id) { return m.headMap(id).size(); }

    /** Sends what changed between the fingers already down and the wanted ones. */
    static void apply(TreeMap<Integer, float[]> wanted) throws Exception {
        for (Integer id : new ArrayList<>(current.keySet())) {
            if (wanted.containsKey(id)) continue;
            long t = SystemClock.uptimeMillis();
            int action = current.size() == 1 ? MotionEvent.ACTION_UP
                    : MotionEvent.ACTION_POINTER_UP | (indexOf(current, id) << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            send(evMap(action, current, t));
            current.remove(id);
        }
        boolean moved = false;
        for (Integer id : current.keySet()) {
            float[] a = current.get(id), b = wanted.get(id);
            if (a[0] != b[0] || a[1] != b[1]) { moved = true; a[0] = b[0]; a[1] = b[1]; }
        }
        long now = SystemClock.uptimeMillis();
        if (moved) { send(evMap(MotionEvent.ACTION_MOVE, current, now)); }
        for (Map.Entry<Integer, float[]> e : wanted.entrySet()) {
            if (current.containsKey(e.getKey())) continue;
            boolean first = current.isEmpty();
            current.put(e.getKey(), e.getValue().clone());
            long t = SystemClock.uptimeMillis();
            if (first) downTime = t;
            send(evMap(first ? MotionEvent.ACTION_DOWN
                    : MotionEvent.ACTION_POINTER_DOWN | (indexOf(current, e.getKey()) << MotionEvent.ACTION_POINTER_INDEX_SHIFT), current, t));
        }
    }

    static float clamp(float v, float max) { return Math.max(0, Math.min(max - 1, v)); }

    /** The kernel reports an axis only when it changes, so the reader must know where it starts. */
    static void zeroAxes() throws Exception {
        try (java.io.FileOutputStream out = new java.io.FileOutputStream("/dev/input/event1")) {
            for (int slot = 0; slot < SLOTS; slot++) {
                for (int[] e : new int[][] {{EV_ABS, ABS_SLOT, slot}, {EV_ABS, ABS_X, 0}, {EV_ABS, ABS_Y, 0}, {EV_SYN, 0, 0}}) {
                    ByteBuffer b = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
                    b.putShort(16, (short) e[0]).putShort(18, (short) e[1]).putInt(20, e[2]);
                    out.write(b.array());
                }
            }
            ByteBuffer b = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
            b.putShort(16, (short) EV_ABS).putShort(18, (short) ABS_SLOT).putInt(20, 0);
            out.write(b.array());
        }
    }

    static void touchLoop() throws Exception {
        zeroAxes();
        FileInputStream in = new FileInputStream("/dev/input/event1");
        grab(in.getFD());
        int[] id = new int[SLOTS];
        float[] x = new float[SLOTS], y = new float[SLOTS];
        java.util.Arrays.fill(id, -1);
        int slot = 0;
        boolean wasDown = false;
        byte[] raw = new byte[24];
        while (true) {
            if (in.read(raw) != 24) continue;
            ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            int type = b.getShort(16), code = b.getShort(18), value = b.getInt(20);
            if (type == EV_ABS) {
                if (code == ABS_SLOT) slot = value;
                else if (slot < SLOTS && code == ABS_ID) id[slot] = value;
                else if (slot < SLOTS && code == ABS_X) x[slot] = value * (float) W / (MAX + 1);
                else if (slot < SLOTS && code == ABS_Y) y[slot] = value * (float) H / (MAX + 1);
            } else if (type == EV_SYN) {
                TreeMap<Integer, float[]> wanted = new TreeMap<>();
                int active = 0, only = 0;
                for (int i = 0; i < SLOTS; i++) if (id[i] != -1) { active++; only = i; }
                if (active > 0 && !wasDown) {
                    mode = armed;
                    startY = y[only];
                    if (mode > 1) System.out.println("FINGERS " + mode);
                }
                if (active == 1 && mode > 1) {
                    float[][] off = layout(mode);
                    // 5 is four fingers that come together when dragged down and part when dragged up.
                    float k = mode == 5 ? Math.max(0.2f, 1 + (startY - y[only]) / 400f) : 1;
                    float cy = mode == 5 ? startY : y[only];
                    for (int i = 0; i < off.length; i++) {
                        wanted.put(i, new float[] {clamp(x[only] + off[i][0] * k, W), clamp(cy + off[i][1], H)});
                    }
                } else if (active > 0) {
                    for (int i = 0; i < SLOTS; i++) if (id[i] != -1) wanted.put(i, new float[] {x[i], y[i]});
                }
                apply(wanted);
                if (active == 0 && wasDown && held == 0) armed = 1;
                wasDown = active > 0;
            }
        }
    }

    /** 2, 3, 4: that many fingers for the next touch, or while held. 5: four-finger pinch. 1: back to one. */
    static void keyLoop() throws Exception {
        FileInputStream in = new FileInputStream("/dev/input/event12");
        byte[] raw = new byte[24];
        while (true) {
            if (in.read(raw) != 24) continue;
            ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            int type = b.getShort(16), code = b.getShort(18), value = b.getInt(20);
            if (type != EV_KEY || code < 2 || code > 6) continue;
            int digit = code - 1;
            if (value != 0) { armed = digit; held = digit; }
            else if (held == digit) held = 0;
        }
    }

    static void daemon(String name, java.util.concurrent.Callable<Void> body) {
        new Thread(() -> {
            try { body.call(); } catch (Exception e) { System.out.println(name + " died: " + e); e.printStackTrace(System.out); System.exit(1); }
        }, name).start();
    }

    public static void main(String[] args) throws Exception {
        Class.forName("android.os.ServiceManager").getMethod("addService", String.class, IBinder.class)
                .invoke(null, "EcarxInputDispatcherService", dispatcher);
        System.out.println("registered");
        daemon("touch", () -> { touchLoop(); return null; });
        daemon("keys", () -> { keyLoop(); return null; });
        File cmd = new File("/data/local/tmp/fd_cmd");
        while (true) {
            Thread.sleep(50);
            if (!cmd.exists() || cmd.length() == 0) continue;
            List<String> lines = new ArrayList<>(Files.readAllLines(cmd.toPath()));
            cmd.delete();
            for (String line : lines) {
                String[] a = line.trim().split(" +");
                String[] fs = a[0].split(";");
                float[][] pts = new float[fs.length][2];
                for (int i = 0; i < fs.length; i++) {
                    String[] xy = fs[i].split(",");
                    pts[i][0] = Float.parseFloat(xy[0]);
                    pts[i][1] = Float.parseFloat(xy[1]);
                }
                System.out.println("GESTURE " + line);
                gesture(pts, Float.parseFloat(a[1]), Float.parseFloat(a[2]), Float.parseFloat(a[3]), Long.parseLong(a[4]), Long.parseLong(a[5]));
            }
        }
    }
}
