package com.mmwtl.atlaslauncher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

/** A silent, looping video background. The decoder exists only while HOME is visible. */
@SuppressLint("ViewConstructor") // Created only by HOME, never inflated from XML.
final class VideoWallpaperView extends TextureView implements TextureView.SurfaceTextureListener {
    private final ExecutorService loader;
    private final Runnable rendered;
    private final Runnable failed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private String uri;
    private MediaPlayer player;
    private Surface surface;
    private boolean active;
    private boolean opening;
    private boolean error;
    private boolean openFailed;
    private int generation;
    private int videoWidth;
    private int videoHeight;

    VideoWallpaperView(Context context, ExecutorService loader, Runnable rendered, Runnable failed) {
        super(context);
        this.loader = loader;
        this.rendered = rendered;
        this.failed = failed;
        setSurfaceTextureListener(this);
        setVisibility(GONE);
    }

    void setVideo(String uri) {
        releasePlayer();
        this.uri = uri;
        error = false;
        openFailed = false;
        setVisibility(uri == null ? GONE : VISIBLE);
        openVideo();
    }

    void setActive(boolean active) {
        this.active = active;
        // A file that could not be opened, say before its provider started after boot, gets another try.
        if (active && openFailed) {
            error = false;
            openFailed = false;
            setVisibility(VISIBLE);
        }
        if (active) openVideo();
        else releasePlayer();
    }

    private void openVideo() {
        if (!active || !isAvailable() || uri == null || player != null || opening || error) return;
        opening = true;
        int request = generation;
        Uri selected = Uri.parse(uri);
        // Opening a document provider can block after boot. Do that before creating the player on the UI thread.
        loader.execute(() -> {
            AssetFileDescriptor descriptor = null;
            try {
                descriptor = getContext().getContentResolver().openAssetFileDescriptor(selected, "r");
            } catch (IOException | SecurityException e) {
                Log.w("AtlasLauncher", "Cannot open video wallpaper", e);
            }
            AssetFileDescriptor opened = descriptor;
            main.post(() -> {
                try {
                    if (request != generation) return;
                    opening = false;
                    if (opened == null) {
                        openFailed = true;
                        videoFailed();
                        return;
                    }
                    player = new MediaPlayer();
                    surface = new Surface(getSurfaceTexture());
                    player.setSurface(surface);
                    player.setOnPreparedListener(mp -> {
                        if (mp != player) return;
                        mp.setVolume(0, 0);
                        mp.setLooping(true);
                        fitVideo(mp.getVideoWidth(), mp.getVideoHeight());
                        mp.start();
                    });
                    player.setOnVideoSizeChangedListener((mp, width, height) -> {
                        if (mp == player) fitVideo(width, height);
                    });
                    player.setOnInfoListener((mp, what, extra) -> {
                        if (mp == player && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) rendered.run();
                        return false;
                    });
                    player.setOnErrorListener((mp, what, extra) -> {
                        if (mp == player) {
                            Log.w("AtlasLauncher", "Video wallpaper playback failed: " + what + "/" + extra);
                            videoFailed();
                        }
                        return true;
                    });
                    if (opened.getDeclaredLength() < 0) player.setDataSource(opened.getFileDescriptor());
                    else player.setDataSource(opened.getFileDescriptor(), opened.getStartOffset(), opened.getDeclaredLength());
                    player.prepareAsync();
                } catch (IOException | RuntimeException e) {
                    Log.w("AtlasLauncher", "Cannot prepare video wallpaper", e);
                    videoFailed();
                } finally {
                    if (opened != null) try { opened.close(); }
                    catch (IOException e) { Log.w("AtlasLauncher", "Cannot close video wallpaper descriptor", e); }
                }
            });
        });
    }

    private void videoFailed() {
        error = true;
        releasePlayer();
        setVisibility(GONE);
        failed.run();
    }

    private void releasePlayer() {
        generation++;
        opening = false;
        if (player != null) {
            player.release();
            player = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }

    private void fitVideo(int width, int height) {
        videoWidth = width;
        videoHeight = height;
        if (width == 0 || height == 0 || getWidth() == 0 || getHeight() == 0) return;
        float scale = Math.max((float) getWidth() / width, (float) getHeight() / height);
        Matrix transform = new Matrix();
        transform.setScale(width * scale / getWidth(), height * scale / getHeight(), getWidth() / 2f, getHeight() / 2f);
        setTransform(transform);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) { openVideo(); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        fitVideo(videoWidth, videoHeight);
    }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        releasePlayer();
        return true;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) { }

    static Bitmap preview(Context context, Uri uri, int maxSize) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            if (Build.VERSION.SDK_INT >= 27)
                return retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSize, maxSize);
            return retriever.getFrameAtTime();
        } catch (RuntimeException e) {
            return null;
        } finally {
            try { retriever.release(); }
            catch (IOException e) { Log.w("AtlasLauncher", "Cannot release video preview", e); }
        }
    }
}
