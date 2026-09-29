package com.mmwtl.atlaslauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import java.util.function.Consumer;

/**
 * One-off diagnostics of the OneOS media center (com.geely.mediacenterservice) before building a media widget on it.
 * Calls are raw Binder transactions; codes and parcel layouts come from the decompiled oneosapi library of
 * com.geely.mediawidget 1.0.20250623G(312) and must be confirmed on the head unit.
 */
final class MediaCenterProbe {
    private static final String TAG = "AtlasMediaProbe";
    static final String PACKAGE = "com.geely.mediacenterservice";
    private static final String ACTION = "android.intent.action.GEELY_MEDIACENTER_SERVICE";
    private static final String MEDIA_CENTER = "com.geely.lib.oneosapi.mediacenter.IMediaCenter";
    private static final String MUSIC_MANAGER = "com.geely.lib.oneosapi.mediacenter.IMusicManager";
    private static final String RADIO_MANAGER = "com.geely.lib.oneosapi.mediacenter.IRadioManager";
    // MediaCenterConstant.AudioSource ordinals.
    private static final String[] AUDIO_SOURCES = {"UNKNOWN", "USB", "BT", "RADIO", "ONLINE", "OTHER", "YUNTING", "CPAA"};
    private static final int[] MUSIC_SOURCES = {1, 2, 7};

    private MediaCenterProbe() {
    }

    /** Binds, queries on a background thread and reports the text on the main thread. */
    static void run(Context context, Consumer<String> onReport) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                ServiceConnection self = this;
                new Thread(() -> {
                    String report = query(service);
                    Log.i(TAG, report);
                    main.post(() -> {
                        app.unbindService(self);
                        onReport.accept(report);
                    });
                }, TAG).start();
            }

            @Override public void onServiceDisconnected(ComponentName name) {
            }

            @Override public void onNullBinding(ComponentName name) {
                app.unbindService(this);
                onReport.accept("Сервис " + PACKAGE + " вернул пустой binder (не инициализирован).");
            }
        };
        Intent intent = new Intent(ACTION).setPackage(PACKAGE);
        boolean bound;
        try { bound = app.bindService(intent, connection, Context.BIND_AUTO_CREATE); }
        catch (SecurityException e) { bound = false; Log.w(TAG, e); }
        if (!bound) onReport.accept("Не удалось подключиться к " + PACKAGE + " (" + ACTION + ").");
    }

    private static String query(IBinder center) {
        StringBuilder out = new StringBuilder();
        try {
            int audio = readInt(center, MEDIA_CENTER, 1);
            out.append("Текущий источник: ").append(source(audio))
                    .append(", приложение ").append(readInt(center, MEDIA_CENTER, 2)).append('\n');
            out.append("Источник с фокусом: ").append(source(readInt(center, MEDIA_CENTER, 16)))
                    .append(", приложение ").append(readInt(center, MEDIA_CENTER, 17)).append('\n');
            IBinder music = readBinder(center, MEDIA_CENTER, 5);
            if (music == null) out.append("IMusicManager: null\n");
            else for (int source : MUSIC_SOURCES) {
                out.append('\n').append(source(source)).append(": состояние ")
                        .append(readInt(music, MUSIC_MANAGER, 15, source))
                        .append(", позиция ").append(readLong(music, MUSIC_MANAGER, 16, source)).append(" мс\n")
                        .append("  ").append(mediaData(music, source)).append('\n');
            }
            IBinder radio = readBinder(center, MEDIA_CENTER, 6);
            if (radio == null) out.append("\nIRadioManager: null\n");
            else {
                int band = readInt(radio, RADIO_MANAGER, 12);
                out.append("\nRADIO: статус ").append(readInt(radio, RADIO_MANAGER, 21))
                        .append(", диапазон ").append(band)
                        .append(", частота ").append(readInt(radio, RADIO_MANAGER, 13)).append('\n')
                        .append("  ").append(frequency(radio, band)).append('\n');
            }
        } catch (RemoteException | RuntimeException e) {
            out.append("\nОшибка: ").append(e);
            Log.w(TAG, e);
        }
        return out.toString();
    }

    private static String source(int ordinal) {
        return ordinal >= 0 && ordinal < AUDIO_SOURCES.length ? AUDIO_SOURCES[ordinal] + " (" + ordinal + ")" : String.valueOf(ordinal);
    }

    private static Parcel call(IBinder binder, String descriptor, int code, int... args) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(descriptor);
            for (int arg : args) data.writeInt(arg);
            binder.transact(code, data, reply, 0);
            reply.readException();
            return reply;
        } catch (RemoteException | RuntimeException e) {
            reply.recycle();
            throw e;
        } finally {
            data.recycle();
        }
    }

    private static int readInt(IBinder binder, String descriptor, int code, int... args) throws RemoteException {
        Parcel reply = call(binder, descriptor, code, args);
        try { return reply.readInt(); } finally { reply.recycle(); }
    }

    private static long readLong(IBinder binder, String descriptor, int code, int... args) throws RemoteException {
        Parcel reply = call(binder, descriptor, code, args);
        try { return reply.readLong(); } finally { reply.recycle(); }
    }

    private static IBinder readBinder(IBinder binder, String descriptor, int code) throws RemoteException {
        Parcel reply = call(binder, descriptor, code);
        try { return reply.readStrongBinder(); } finally { reply.recycle(); }
    }

    /** IMusicManager.getCurrentMediaData, MediaData.readFromParcel layout. */
    private static String mediaData(IBinder music, int source) throws RemoteException {
        Parcel reply = call(music, MUSIC_MANAGER, 17, source);
        try {
            if (reply.readInt() == 0) return "трек: null";
            String id = reply.readString();
            String artist = reply.readString();
            String name = reply.readString();
            long duration = reply.readLong();
            reply.readString(); // uri
            Bitmap cover = reply.readParcelable(Bitmap.class.getClassLoader());
            String coverUri = reply.readString();
            String album = reply.readString();
            int mediaSource = reply.readInt();
            boolean favored = reply.readInt() != 0;
            int mediaType = reply.readInt();
            return "трек: «" + name + "» — " + artist + ", альбом «" + album + "», " + duration + " мс, id " + id
                    + ", обложка " + (cover == null ? "нет" : cover.getWidth() + "×" + cover.getHeight())
                    + (coverUri == null || coverUri.isEmpty() ? "" : " / " + coverUri)
                    + ", source " + mediaSource + ", type " + mediaType + (favored ? ", в избранном" : "");
        } finally {
            reply.recycle();
        }
    }

    /** IRadioManager.getCurrentFrequency, Frequency.readFromParcel layout. */
    private static String frequency(IBinder radio, int band) throws RemoteException {
        Parcel reply = call(radio, RADIO_MANAGER, 30, band);
        try {
            if (reply.readInt() == 0) return "станция: null";
            int frequency = reply.readInt();
            int stationBand = reply.readInt();
            String ensemble = reply.readString();
            String service = reply.readString();
            return "станция: " + frequency + ", диапазон " + stationBand + ", «" + service + "» / «" + ensemble + "»";
        } finally {
            reply.recycle();
        }
    }
}
