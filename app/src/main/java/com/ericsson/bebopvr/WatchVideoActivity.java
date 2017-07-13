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
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.View;

import com.ericsson.bebopvr.controller.DroneControllerManager;
import com.ericsson.bebopvr.dron.Drone;
import com.ericsson.bebopvr.dron.DroneListener;
import com.ericsson.bebopvr.dron.DroneService;
import com.ericsson.bebopvr.dron.video.BebopVideoView;
import com.ericsson.bebopvr.dron.video.VideoSceneRenderer;
import com.google.vr.ndk.base.AndroidCompat;
import com.google.vr.ndk.base.BufferViewport;
import com.google.vr.ndk.base.GvrLayout;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DICTIONARY_KEY_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARControllerDictionary;
import com.parrot.arsdk.arcontroller.ARFrame;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

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

    private DroneControllerManager droneControllerManager;

    private List<DroneService> droneListeners = new ArrayList<>();

    private SpeechRecognizer recognizer;

    private static final String KWS_SEARCH = "wakeup";
    private static final String COMMAND_SEARCH = "command";

    /* Keyword we are looking for to activate menu */
    private static final String KEYPHRASE = "ok drone";

    private String TAKEOFF_COMMAND = "takeoff";
    private String LAND_COMMAND = "land";
    private String FLIP_COMMAND = "flip";
    private String PICTURE_COMMAND = "picture";
    private String START_RECORDING_COMMAND = "start";
    private String STOP_RECORDING_COMMAND = "stop";


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

        this.droneControllerManager = new DroneControllerManager(this);


        //droneControllerManager.addDroneService(drone.getDroneService());



        droneListeners.add(drone.getDroneService());
        runRecognizerSetup();
    }

    private void runRecognizerSetup() {
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(WatchVideoActivity.this);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
                    Log.i(TAG, "Failed to init recognizer " + result);
                } else {
                    switchSearch(KWS_SEARCH);
                }
            }
        }.execute();
    }

    private void switchSearch(String searchName) {
        recognizer.stop();

        // If we are not spotting, start listening with timeout (10000 ms or 10 seconds).
        if (searchName.equals(KWS_SEARCH))
            recognizer.startListening(searchName);
        else
            recognizer.startListening(searchName, 10000);

    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))

                //.setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)

                .getRecognizer();
        recognizer.addListener(new RecognitionListener() {
            @Override
            public void onBeginningOfSpeech() {

            }

            @Override
            public void onEndOfSpeech() {
                if (!recognizer.getSearchName().equals(KWS_SEARCH))
                    switchSearch(KWS_SEARCH);
            }

            @Override
            public void onPartialResult(Hypothesis hypothesis) {
                if (hypothesis == null)
                    return;

                String text = hypothesis.getHypstr();
                if (text.equals(KEYPHRASE)) {
                    switchSearch(COMMAND_SEARCH);
                }
            }

            @Override
            public void onResult(Hypothesis hypothesis) {
                Log.i(TAG,"on result");
                if (hypothesis != null) {
                    String text = hypothesis.getHypstr();
                    Log.i(TAG,"RESULT: " + text+ " Score " + hypothesis.getBestScore());
                    if ( Integer.valueOf(hypothesis.getBestScore()) > -2000) {
                        if (TAKEOFF_COMMAND.equals(text)) {
                            for (DroneService dService: droneListeners) {
                                dService.takeOff();
                            }
                        } else if (LAND_COMMAND.equals(text)) {
                            for (DroneService dService: droneListeners) {
                                dService.land();
                            }
                        } else if (PICTURE_COMMAND.equals(text)) {
                            for (DroneService dService: droneListeners) {
                                dService.takeAPicture();
                            }
                        } else if (START_RECORDING_COMMAND.equals(text)) {
                            for (DroneService dService: droneListeners) {
                                dService.startRecording();
                            }
                        } else if (STOP_RECORDING_COMMAND.equals(text)) {
                            for (DroneService dService: droneListeners) {
                                dService.stopRecording();
                            }
                        } else if (FLIP_COMMAND.equals(text)) {
                            for (DroneService dService: droneListeners) {
                                dService.doAFlip();
                            }
                        } else {
                            playSound(RingtoneManager.TYPE_NOTIFICATION);
                        }

                    } else {
                        //Play sound
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                Log.i(TAG, "ERROR " + e.getMessage() );
            }

            @Override
            public void onTimeout() {
                switchSearch(KWS_SEARCH);
            }
        });

        /** In your application you might not need to add all those searches.
         * They are added here for demonstration. You can leave just one.
         */

        // Create keyword-activation search.
        recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);

        // Create grammar-based search for selection between demos
        File commandGrammar = new File(assetsDir, "command.gram");
        recognizer.addGrammarSearch(COMMAND_SEARCH, commandGrammar);


    }

    public void playSound(final int soundType) {
        new Thread(new Runnable() {
            public void run() {
                Uri notification = RingtoneManager.getDefaultUri(soundType);
                Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                r.play();
            }
        }).start();
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

