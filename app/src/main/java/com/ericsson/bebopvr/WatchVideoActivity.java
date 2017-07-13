package com.ericsson.bebopvr;

/**
 * Created by obatiuk on 7/12/17.
 */

/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.app.Activity;
import android.media.MediaCodec;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;

import com.ericsson.bebopvr.dron.Drone;
import com.ericsson.bebopvr.dron.DroneListener;
import com.ericsson.bebopvr.dron.video.BebopVideoView;
import com.ericsson.bebopvr.dron.video.VideoSceneRenderer;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.vr.ndk.base.AndroidCompat;
import com.google.vr.ndk.base.BufferViewport;
import com.google.vr.ndk.base.GvrLayout;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DICTIONARY_KEY_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARControllerDictionary;
import com.parrot.arsdk.arcontroller.ARFrame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Lock;

/**
 * Simple activity for video playback using the Asynchronous Reprojection Video Surface API. For a
 * detailed description of the API
 * <p>The Surface for video output is acquired from the {@link GvrLayout.ExternalSurfaceListener} and set on
 * the video player. For each frame, this sample draws a window (alpha 0) in the scene where video
 * should be visible. To trigger the GvrApi to render the video frame, the {@link
 * VideoSceneRenderer} adds a {@link BufferViewport} per eye to describe where video should be
 * drawn.
 */
public class WatchVideoActivity extends Activity implements DroneListener {
    private static final String TAG = WatchVideoActivity.class.getSimpleName();

    private GvrLayout gvrLayout;
    private BebopVideoView surfaceView;
    private VideoSceneRenderer renderer;
    private boolean hasFirstFrame;
    private Drone drone;

    // Transform a quad that fills the clip box at Z=0 to a 16:9 screen at Z=-4. Note that the matrix
    // is column-major, so the translation is on the last line in this representation.
    private final float[] videoTransform = {1.6f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.9f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, -4.f, 1.0f};

    // Runnable to refresh the viewer profile when gvrLayout is resumed.
    // This is done on the GL thread because refreshViewerProfile isn't thread-safe.
    private final Runnable refreshViewerProfileRunnable =
            new Runnable() {
                @Override
                public void run() {
                    gvrLayout.getGvrApi().refreshViewerProfile();
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setImmersiveSticky();
        getWindow()
                .getDecorView()
                .setOnSystemUiVisibilityChangeListener(
                        new View.OnSystemUiVisibilityChangeListener() {
                            @Override
                            public void onSystemUiVisibilityChange(int visibility) {
                                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                                    setImmersiveSticky();
                                }
                            }
                        });

        AndroidCompat.setSustainedPerformanceMode(this, true);
        AndroidCompat.setVrModeEnabled(this, true);

        drone = new Drone(this);
        drone.addDroneListener(this);
        gvrLayout = new GvrLayout(this);
        surfaceView = new BebopVideoView(this);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(5, 6, 5, 0, 0, 0);
        gvrLayout.setPresentationView(surfaceView);
        gvrLayout.setKeepScreenOn(true);
        renderer = new VideoSceneRenderer(this, gvrLayout.getGvrApi());

        // Initialize the ExternalSurfaceListener to receive video Surface callbacks.
        hasFirstFrame = false;
        GvrLayout.ExternalSurfaceListener videoSurfaceListener =
                new GvrLayout.ExternalSurfaceListener() {
                    @Override
                    public void onSurfaceAvailable(Surface surface) {
                        // Set the surface for the video player to output video frames to. Video playback
                        // is started when the Surface is set. Note that this callback is *asynchronous* with
                        // respect to the Surface becoming available, in which case videoPlayer may be null due
                        // to the Activity having been stopped.
//                        if (videoPlayer != null) {
//                            videoPlayer.setSurface(surface);
//                        }
                    }

                    @Override
                    public void onFrameAvailable() {
                        // If this is the first frame, and the Activity is still in the foreground, signal to
                        // remove the loading splash screen, and draw alpha 0 in the color buffer where the
                        // video will be drawn by the GvrApi.
                        if (!hasFirstFrame) {
                            surfaceView.queueEvent(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            renderer.setHasVideoPlaybackStarted(true);
                                        }
                                    });

                            hasFirstFrame = true;
                        }
                    }
                };

        // Note that enabling video Surface support will also enable async reprojection.
        boolean isAsyncReprojectionEnabled =
                gvrLayout.enableAsyncReprojectionVideoSurface(
                        videoSurfaceListener,
                        new Handler(Looper.getMainLooper()),
                        /* Whether video playback should use a protected reprojection pipeline. */
                        false);

        if (!isAsyncReprojectionEnabled) {
            // The device does not support this API, video will not play.
            Log.e(TAG, "UnsupportedException: Async Reprojection with Video is unsupported.");
        } else {
            initVideoPlayer();

            // The default value puts the viewport behind the eye, so it's invisible. Set the transform
            // now to ensure the video is visible when rendering starts.
            renderer.setVideoTransform(videoTransform);
            // The ExternalSurface buffer the GvrApi should reference when drawing the video buffer. This
            // must be called after enabling the Async Reprojection video surface.
            renderer.setVideoSurfaceId(gvrLayout.getAsyncReprojectionVideoSurfaceId());
        }

        // Set the renderer and start the app's GL thread.
        surfaceView.setRenderer(renderer);

        setContentView(gvrLayout);
    }

    private void initVideoPlayer() {
    }

    @Override
    protected void onStart() {
        super.onStart();
        hasFirstFrame = false;
        surfaceView.queueEvent(
                new Runnable() {
                    @Override
                    public void run() {
                        renderer.setHasVideoPlaybackStarted(false);
                    }
                });

        // Resume gvrLayout here. This will start the render thread and trigger a new async reprojection
        // video Surface to become available.
        gvrLayout.onResume();
        // Refresh the viewer profile in case the viewer params were changed.
        surfaceView.queueEvent(refreshViewerProfileRunnable);

        if ((drone != null) && !(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(drone.getState()))) {
            // if the connection to the Bebop fails, finish the activity
            if (!drone.connect()) {
                // TODO display message
                Log.d(TAG, "Drone was not initialized");
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        // Pause the gvrLayout here. The video Surface is guaranteed to be detached and not available
        // after gvrLayout.onPause(). We pause from onStop() to avoid needing to wait for an available
        // video Surface following brief onPause()/onResume() events. Wait for the new
        // onSurfaceAvailable() callback with a valid Surface before resuming the video player.
        gvrLayout.onPause();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        gvrLayout.shutdown();
        drone.dispose();
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Avoid accidental volume key presses while the phone is in the VR headset.
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP
                || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setImmersiveSticky();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        gvrLayout.onBackPressed();
    }

    private void setImmersiveSticky() {
        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    /**
     * @return {@code true} if the first video frame has played
     **/
    protected boolean hasFirstFrame() {
        return hasFirstFrame;
    }

    @Override
    public void onStateChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {

    }

    @Override
    public void onCommandReceived(ARCONTROLLER_DICTIONARY_KEY_ENUM commandKey, ARControllerDictionary elementDictionary) {

    }

    @Override
    public void configureDecoder(ARControllerCodec codec) {
        Log.d(TAG, "Configure decoder [this.hasFirstFrame" + this.hasFirstFrame + "]");
        surfaceView.configureDecoder(codec);
    }

    @Override
    public void onFrameReceived(ARFrame frame) {
        Log.d(TAG, "On Frame Received [this.hasFirstFrame" + this.hasFirstFrame + "]");
        surfaceView.displayFrame(frame);
    }
}

