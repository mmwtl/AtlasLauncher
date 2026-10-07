package com.mmwtl.gestureprobe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
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
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.InvocationTargetException;

public final class GestureProbeActivity extends Activity {
    private static final String TAG = "GestureProbe";
    // Verified against OneOS framework.jar; see README.md for the complete wire layout.
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
    // Entry.setDisplayState selects display 0 only when bottom > 1080 (right <= 1440).
    private static final Rect REGION = new Rect(400, 1200, 600, 1400);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ProbeFilter filter = new ProbeFilter();
    private final Runnable timeout = () -> requestUnregister("60s timeout");
    private final Runnable failStop = () -> stopProcess("unregister did not finish within 2s");
    private HandlerThread controlThread;
    private Handler control;
    // Service state belongs to the control thread; callback host belongs to ProbeFilter.
    private IBinder service;
    private boolean registrationAttempted;
    private boolean registrationRequested;
    private boolean stopping;
    private boolean destroyed;
    private Button registerButton;
    private Button unregisterButton;
    private TextView logView;
    private ScrollView logScroll;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        controlThread = new HandlerThread("GestureProbeControl");
        controlThread.start();
        control = new Handler(controlThread.getLooper());

        FrameLayout root = new FrameLayout(this);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        column.setPadding(padding, padding, padding, padding);
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        TextView description = new TextView(this);
        description.setText(R.string.probe_description);
        column.addView(description);
        LinearLayout buttons = new LinearLayout(this);
        registerButton = new Button(this);
        registerButton.setText(R.string.register);
        registerButton.setOnClickListener(view -> requestRegister());
        buttons.addView(registerButton, new LinearLayout.LayoutParams(0, -2, 1));
        unregisterButton = new Button(this);
        unregisterButton.setText(R.string.unregister);
        unregisterButton.setEnabled(false);
        unregisterButton.setOnClickListener(view -> requestUnregister("button"));
        buttons.addView(unregisterButton, new LinearLayout.LayoutParams(0, -2, 1));
        column.addView(buttons);
        LinearLayout recents = new LinearLayout(this);
        Button recentsNow = new Button(this);
        recentsNow.setText(R.string.recents_now);
        recentsNow.setOnClickListener(view -> openRecents("now"));
        recents.addView(recentsNow, new LinearLayout.LayoutParams(0, -2, 1));
        Button recentsLater = new Button(this);
        recentsLater.setText(R.string.recents_later);
        // Leave the app within 5 s to check starting Recents without a visible window.
        recentsLater.setOnClickListener(view -> {
            log("RECENTS in 5s: leave this app now");
            ui.postDelayed(() -> openRecents("delayed 5s"), 5_000);
        });
        recents.addView(recentsLater, new LinearLayout.LayoutParams(0, -2, 1));
        column.addView(recents);
        logScroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextIsSelectable(true);
        logScroll.addView(logView);
        column.addView(logScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // A non-clickable view in our own window, not a global overlay or touch listener.
        root.addView(new View(this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final int[] location = new int[2];

            @Override
            protected void onDraw(Canvas canvas) {
                getLocationOnScreen(location);
                paint.setColor(Color.BLUE);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(4);
                canvas.drawRect(REGION.left - location[0], REGION.top - location[1],
                        REGION.right - location[0], REGION.bottom - location[1], paint);
            }
        }, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        log("START uid=" + Process.myUid() + " pid=" + Process.myPid()
                + "; registration is manual unless --ez register true");
        // Do not silently register again after Activity recreation.
        if (state == null && getIntent().getBooleanExtra("register", false)) {
            requestRegister();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("register", false)) {
            requestRegister();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Only observes what reaches our own window; confirms end-to-end delivery of relayed events.
        if (registrationRequested) {
            StringBuilder details = new StringBuilder("APP ")
                    .append(MotionEvent.actionToString(event.getAction()))
                    .append(" pointers=").append(event.getPointerCount());
            for (int i = 0; i < event.getPointerCount(); i++) {
                details.append(" id=").append(event.getPointerId(i))
                        .append(" x=").append(event.getX(i))
                        .append(" y=").append(event.getY(i));
            }
            log(details.toString());
        }
        return super.dispatchTouchEvent(event);
    }

    private void openRecents(String reason) {
        try {
            startActivity(new Intent().setClassName("com.geely.recents",
                    "com.geely.recents.RecentsCsdActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            log("RECENTS " + reason + ": startActivity returned");
        } catch (RuntimeException error) {
            log("RECENTS " + reason + " failed: " + error);
        }
    }

    private void requestRegister() {
        if (registrationRequested || stopping || destroyed) {
            return;
        }
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(size);
        int displayId = getWindowManager().getDefaultDisplay().getDisplayId();
        log("DISPLAY id=" + displayId + " size=" + size.x + "x" + size.y
                + "; region=" + REGION.toShortString());
        if (displayId != 0 || size.x < REGION.right + 50 || size.y < REGION.bottom + 50) {
            log("REGISTER refused: region does not fit display 0 away from edges");
            return;
        }
        registrationRequested = true;
        registerButton.setEnabled(false);
        unregisterButton.setEnabled(true);
        ui.postDelayed(timeout, 60_000);
        log("REGISTER requested; priority=2; timeout armed for 60s");
        control.post(() -> {
            try {
                service = (IBinder) Class.forName("android.os.ServiceManager")
                        .getDeclaredMethod("getService", String.class).invoke(null, SERVICE_NAME);
                log("REFLECTION OK; binder=" + (service == null ? "null" : "non-null"));
                if (service == null) {
                    ui.post(() -> requestUnregister("binder null"));
                    return;
                }
                String descriptor = service.getInterfaceDescriptor();
                log("SERVICE descriptor=" + descriptor);
                if (!DISPATCHER_DESCRIPTOR.equals(descriptor)) {
                    throw new IllegalStateException("unexpected service descriptor");
                }
                // Even a failed reply might follow a successful server-side registration.
                registrationAttempted = true;
                boolean result = dispatcherTransaction(REGISTER);
                log("REGISTER result=" + result);
                if (!result) {
                    ui.post(() -> requestUnregister("register returned false"));
                }
            } catch (ReflectiveOperationException error) {
                Throwable cause = error instanceof InvocationTargetException
                        ? ((InvocationTargetException) error).getTargetException() : error;
                log("REFLECTION blocked/failed: " + cause);
                ui.post(() -> requestUnregister("reflection failed"));
            } catch (RemoteException | RuntimeException error) {
                log("REGISTER exception: " + error);
                ui.post(() -> requestUnregister("register exception"));
            }
        });
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
            boolean accepted = service.transact(code, data, reply, 0);
            log("DISPATCHER code=" + code + " transact=" + accepted);
            if (!accepted) {
                throw new RemoteException("dispatcher transact returned false");
            }
            reply.readException();
            return reply.readInt() != 0;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private void requestUnregister(String reason) {
        if (stopping) {
            return;
        }
        stopping = true;
        registerButton.setEnabled(false);
        unregisterButton.setEnabled(false);
        ui.removeCallbacks(timeout);
        ui.postDelayed(failStop, 2_000);
        log("UNREGISTER requested: " + reason);
        control.post(() -> {
            try {
                if (registrationAttempted) {
                    boolean result = dispatcherTransaction(UNREGISTER);
                    log("UNREGISTER result=" + result);
                    if (!result) {
                        stopProcess("unregister returned false; registration state uncertain");
                        return;
                    }
                } else {
                    log("UNREGISTER skipped: registration was not attempted");
                }
                registrationAttempted = false;
                service = null;
                ui.removeCallbacks(failStop);
                ui.post(() -> {
                    registrationRequested = false;
                    stopping = false;
                    if (!destroyed) {
                        registerButton.setEnabled(true);
                    }
                });
            } catch (RemoteException | RuntimeException error) {
                stopProcess("UNREGISTER exception: " + error);
            }
        });
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        requestUnregister("onDestroy");
        control.post(() -> controlThread.quitSafely());
        super.onDestroy();
    }

    private void log(String message) {
        String line = SystemClock.elapsedRealtime() + " " + message;
        Log.i(TAG, line);
        ui.post(() -> {
            if (destroyed) {
                return;
            }
            logView.append(line + "\n");
            if (logView.length() > 24_000) {
                logView.setText(logView.getText().subSequence(logView.length() - 12_000,
                        logView.length()));
            }
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void stopProcess(String reason) {
        Log.e(TAG, "FAIL_STOP " + reason + "; killing own process to release filter Binder");
        Process.killProcess(Process.myPid());
    }

    private final class ProbeFilter extends Binder {
        private volatile IBinder host;

        ProbeFilter() {
            attachInterface(null, FILTER_DESCRIPTOR);
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
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
                    log("INSTALL host=non-null");
                } else if (code == UNINSTALL) {
                    // OEM keeps a cached Entry until UP/CANCEL, even after unregister.
                    log("UNINSTALL; retaining host for in-flight events");
                } else {
                    event = data.readInt() != 0 ? InputEvent.CREATOR.createFromParcel(data) : null;
                    int policyFlags = data.readInt();
                    if (event == null || host == null) {
                        throw new IllegalStateException("event or host is null");
                    }
                    Parcel outgoing = Parcel.obtain();
                    boolean accepted;
                    try {
                        outgoing.writeInterfaceToken(HOST_DESCRIPTOR);
                        outgoing.writeInt(1);
                        event.writeToParcel(outgoing, 0);
                        outgoing.writeInt(policyFlags);
                        // Relay unchanged on this Binder thread BEFORE any event logging/UI work.
                        accepted = host.transact(SEND_EVENT, outgoing, null, IBinder.FLAG_ONEWAY);
                    } finally {
                        outgoing.recycle();
                    }
                    if (!accepted) {
                        throw new RemoteException("sendInputEvent transact=false");
                    }
                    if (event instanceof MotionEvent) {
                        MotionEvent motion = (MotionEvent) event;
                        StringBuilder details = new StringBuilder("EVENT ")
                                .append(MotionEvent.actionToString(motion.getAction()))
                                .append(" pointers=").append(motion.getPointerCount());
                        for (int i = 0; i < motion.getPointerCount(); i++) {
                            details.append(" id=").append(motion.getPointerId(i))
                                    .append(" x=").append(motion.getX(i))
                                    .append(" y=").append(motion.getY(i));
                        }
                        log(details + " policyFlags=" + policyFlags + " SEND transact=true (oneway)");
                    } else {
                        log("EVENT " + event + " policyFlags=" + policyFlags
                                + " SEND transact=true (oneway)");
                    }
                }
                return true;
            } catch (RemoteException | RuntimeException error) {
                // Callback is oneway: throwing alone would not tell the OEM to pass the event.
                stopProcess("FILTER/SEND exception: " + error);
                return true;
            } finally {
                if (event instanceof MotionEvent) {
                    ((MotionEvent) event).recycle();
                }
            }
        }
    }
}
