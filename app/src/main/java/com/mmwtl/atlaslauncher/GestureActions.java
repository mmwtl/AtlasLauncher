package com.mmwtl.atlaslauncher;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.widget.Toast;

/**
 * One-off gesture actions that do not touch the car: opening things, media, radios and keys
 * for the system. Runs on the gesture service's action thread. Opening an activity from the
 * background takes the "display over other apps" permission on Android 11.
 */
final class GestureActions {
    /** Sends a key to the system through the input filter; see {@code GestureFilterService}. */
    interface Keys {
        void send(int keyCode);
    }

    /** Shows a confirmation card: a named state, or a large icon for an action without one. */
    interface Feedback {
        void show(int icon, String title, String state);

        void showAction(int icon, String title, int glyph, String text);
    }

    private static final String TAG = "AtlasGestures";
    private static final Uri CLIMATE = Uri.parse("content://com.geely.hvac/hvac/query");
    private static final ComponentName RECENTS = new ComponentName("com.geely.recents",
            "com.geely.recents.RecentsCsdActivity");
    private static final String MEDIA_WIDGET = "com.mmwtl.atlasmediawidget";
    private static final String MEDIA_COMMAND = MEDIA_WIDGET + ".action.MEDIA_COMMAND";

    private final Context context;
    private final Keys keys;
    private final Feedback feedback;
    private final AudioManager audio;
    private final Handler main = new Handler(Looper.getMainLooper());

    GestureActions(Context context, Keys keys, Feedback feedback) {
        this.context = context;
        this.keys = keys;
        this.feedback = feedback;
        audio = context.getSystemService(AudioManager.class);
    }

    /** Returns false for an action that is not one of these. */
    boolean run(String action) {
        if (action.startsWith(GestureMap.APP_PREFIX)) {
            open(action.substring(GestureMap.APP_PREFIX.length()), true);
            return true;
        }
        if (action.startsWith(GestureMap.ACTIVITY_PREFIX)) {
            open(action.substring(GestureMap.ACTIVITY_PREFIX.length()), false);
            return true;
        }
        switch (action) {
            case GestureMap.HOME:
                main.post(this::openHome);
                return true;
            case GestureMap.ALL_APPS:
                main.post(() -> AllAppsActivity.open(context));
                return true;
            case GestureMap.BACK:
                keys.send(KeyEvent.KEYCODE_BACK);
                return true;
            case GestureMap.RECENTS:
                openRecents();
                return true;
            case GestureMap.NOTIFICATIONS:
                keys.send(KeyEvent.KEYCODE_NOTIFICATION);
                return true;
            case GestureMap.SCREENSHOT:
                keys.send(KeyEvent.KEYCODE_SYSRQ);
                return true;
            case GestureMap.PLAY_PAUSE:
                // Whether it pauses or plays, guessed from whether any music sounds now.
                boolean playing = audio.isMusicActive();
                mediaCommand("PLAY_PAUSE", playing ? R.drawable.ic_pause : R.drawable.ic_play,
                        playing ? "Пауза" : "Пуск");
                return true;
            case GestureMap.NEXT_TRACK:
                mediaCommand("NEXT", R.drawable.ic_skip_next, "Далее");
                return true;
            case GestureMap.PREVIOUS_TRACK:
                mediaCommand("PREVIOUS", R.drawable.ic_skip_previous, "Назад");
                return true;
            case GestureMap.MUTE:
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE,
                        AudioManager.FLAG_SHOW_UI);
                // The state read back, not the one asked for.
                boolean muted = audio.isStreamMute(AudioManager.STREAM_MUSIC);
                feedback.show(muted ? R.drawable.ic_volume_off : R.drawable.ic_volume_up, "Звук",
                        muted ? "Выкл" : "Вкл");
                return true;
            case GestureMap.CLIMATE_SCREEN:
                toggleClimateScreen();
                return true;
            case GestureMap.CLIMATE_PANEL:
                // In the main process, where HOME hears the setting change.
                context.sendBroadcast(new Intent(ClimatePanelReceiver.ACTION_TOGGLE).setPackage(context.getPackageName()));
                return true;
            case GestureMap.WIFI:
                toggleWifi();
                return true;
            case GestureMap.BLUETOOTH:
                toggleBluetooth();
                return true;
            default:
                return false;
        }
    }

    /** Opens AtlasLauncher straight away when it is HOME, so the system does not pick Launcher3 first. */
    private void openHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = context.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolved != null && context.getPackageName().equals(resolved.activityInfo.packageName)) {
            home = new Intent(context, HomeActivity.class);
        }
        try {
            context.startActivity(home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException error) {
            Log.w(TAG, "Cannot open HOME", error);
        }
    }

    /** An app from the catalog, or with {@code launcher} false any activity, as the "All apps" button opens one. */
    private void open(String flattened, boolean launcher) {
        ComponentName component = ComponentName.unflattenFromString(flattened);
        Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (launcher) intent.addCategory(Intent.CATEGORY_LAUNCHER);
        main.post(() -> {
            try {
                // As for the catalog: focus may be on the display the climate panel moved Launcher3 to.
                context.startActivity(intent,
                        ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle());
            } catch (ActivityNotFoundException | SecurityException | NullPointerException error) {
                Log.w(TAG, "Cannot open " + flattened, error);
                Toast.makeText(context, launcher ? "Приложение недоступно" : "Activity недоступна", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** The OEM recents screen, as the stock gesture service opens it; elsewhere the standard key. */
    private void openRecents() {
        Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(RECENTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        main.post(() -> {
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException | SecurityException error) {
                keys.send(KeyEvent.KEYCODE_APP_SWITCH);
            }
        });
    }

    /**
     * Media keys do not reach the OneOS sources, so playback goes through the Atlas Media Widget's
     * bridge. The widget acts only while its service is up; otherwise the command is dropped, so
     * the card confirms the gesture, not the result.
     */
    private void mediaCommand(String command, int glyph, String text) {
        context.sendBroadcast(new Intent(MEDIA_COMMAND).setPackage(MEDIA_WIDGET)
                .putExtra("command", command));
        feedback.showAction(R.drawable.ic_music, "Музыка", glyph, text);
    }

    /** The provider answers 0 while the screen is open and 1 otherwise. */
    private void toggleClimateScreen() {
        boolean open = false;
        try (Cursor cursor = context.getContentResolver().query(CLIMATE, new String[] {"getVisibility"}, null, null,
                null)) {
            open = cursor != null && cursor.moveToFirst() && cursor.getLong(0) == 0;
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot read the climate screen state", error);
        }
        try (Cursor ignored = context.getContentResolver().query(CLIMATE, new String[] {open ? "close" : "open"}, null,
                null, null)) {
            // The query itself is the command.
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot switch the climate screen", error);
        }
    }

    @SuppressWarnings("deprecation")
    private void toggleWifi() {
        WifiManager wifi = context.getApplicationContext().getSystemService(WifiManager.class);
        boolean on = !wifi.isWifiEnabled();
        if (wifi.setWifiEnabled(on)) {
            feedback.show(R.drawable.ic_wifi, "Wi-Fi", on ? "Вкл" : "Выкл");
        }
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("MissingPermission") // BLUETOOTH_ADMIN is in the manifest; BLUETOOTH_CONNECT is for targetSdk 31+.
    private void toggleBluetooth() {
        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
        if (bluetooth == null) {
            return;
        }
        boolean on = !bluetooth.isEnabled();
        if (on ? bluetooth.enable() : bluetooth.disable()) {
            feedback.show(R.drawable.ic_bluetooth, "Bluetooth", on ? "Вкл" : "Выкл");
        }
    }
}
