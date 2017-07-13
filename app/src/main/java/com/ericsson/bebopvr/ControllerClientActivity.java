/*
 * Copyright 2017 Google Inc. All Rights Reserved.

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

package com.ericsson.bebopvr;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import com.ericsson.bebopvr.controller.DroneControllerManager;
import com.ericsson.bebopvr.dron.Drone;
import com.ericsson.bebopvr.dron.DroneService;
import com.google.vr.sdk.base.AndroidCompat;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;

import java.io.File;
import java.io.IOException;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

import static android.widget.Toast.makeText;

/**
 * Minimal example demonstrating how to receive and process Daydream controller input. It connects
 * to a Daydream Controller and displays a simple graphical and textual representation of the
 * controller's sensors. This example only works with Android N and Daydream-ready phones.
 */
public class ControllerClientActivity extends Activity {

    private static final String TAG = "ControllerActivity";

    // These TextViews display controller events.
    private TextView apiStatusView;
    private TextView controllerStateView;
    private TextView controllerOrientationText;
    private TextView controllerTouchpadView;
    private TextView controllerButtonView;
    private TextView controllerBatteryView;

    private DroneControllerManager droneControllerManager;

    // This is a 3D representation of the controller's pose. See its comments for more information.
    private OrientationView controllerOrientationView;

    // The various events we need to handle happen on arbitrary threads. They need to be reposted to
    // the UI thread in order to manipulate the TextViews. This is only required if your app needs to
    // perform actions on the UI thread in response to controller events.
    private Handler uiHandler = new Handler();

    private Drone drone;
    private DroneService droneService;

    private SpeechRecognizer recognizer;

    private static final String KWS_SEARCH = "wakeup";
    private static final String COMMAND_SEARCH = "command";

    /* Keyword we are looking for to activate menu */
    private static final String KEYPHRASE = "ok drone";



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // General view initialization.
        setContentView(R.layout.main_layout);
        apiStatusView = (TextView) findViewById(R.id.api_status_view);
        controllerStateView = (TextView) findViewById(R.id.controller_state_view);
        controllerTouchpadView = (TextView) findViewById(R.id.controller_touchpad_view);
        controllerButtonView = (TextView) findViewById(R.id.controller_button_view);
        controllerOrientationText = (TextView) findViewById(R.id.controller_orientation_text);
        controllerTouchpadView = (TextView) findViewById(R.id.controller_touchpad_view);
        controllerButtonView = (TextView) findViewById(R.id.controller_button_view);
        controllerBatteryView = (TextView) findViewById(R.id.controller_battery_view);

        this.droneControllerManager = new DroneControllerManager(this);

        // Start the ControllerManager and acquire a Controller object which represents a single
        // physical controller. Bind our listener to the ControllerManager and Controller.
        EventListener listener = new EventListener();
        this.droneControllerManager.addDroneService(listener);

        apiStatusView.setText("OK");

        // Bind the OrientationView to our acquired controller.
        controllerOrientationView = (OrientationView) findViewById(R.id.controller_orientation_view);
        controllerOrientationView.setController(droneControllerManager.getController());

        drone = new Drone(this);
        droneService = drone.getDroneService();
        droneControllerManager.addDroneService(droneService);

        // This configuration won't be required for normal GVR apps. However, since this sample doesn't
        // use GvrView, it needs pretend to be a VR app in order to receive controller events. The
        // Activity.setVrModeEnabled is only enabled on in N, so this is an GVR-internal utility method
        // to configure the app via reflection.
        //
        // If this sample is compiled with the N SDK, Activity.setVrModeEnabled can be called directly.
        AndroidCompat.setVrModeEnabled(this, true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        runRecognizerSetup();
    }

    @Override
    protected void onStart() {
        super.onStart();
        droneControllerManager.start();
        controllerOrientationView.startTrackingOrientation();
        if ((drone != null) && !(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(drone.getState()))) {
            // if the connection to the Bebop fails, finish the activity
            if (!drone.connect()) {
                finish();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (drone != null) {
            if (!drone.disconnect()) {
                finish();
            }
        }
    }

    @Override
    protected void onStop() {
        droneControllerManager.stop();
        controllerOrientationView.stopTrackingOrientation();
        super.onStop();
    }


    @Override
    public void onDestroy() {
        drone.dispose();
        super.onDestroy();
    }

    // We receive all events from the Controller through this listener. In this example, our
    // listener handles both ControllerManager.EventListener and Controller.EventListener events.
    // This class is also a Runnable since the events will be reposted to the UI thread.
    private class EventListener implements DroneService, Runnable {
        private byte pitch;
        private byte yaw;
        private byte gaz;
        private byte roll;
        private String status;

        // Update the various TextViews in the UI thread.
        @Override
        public void run() {
            controllerStateView.setText(status);

            controllerOrientationText.setText("g " + gaz + " y " + yaw + " p " + pitch + " r " + roll);

        }

        @Override
        public void move(byte roll, byte pitch, byte yaw, byte gaz) {
            this.yaw = yaw;
            this.gaz = gaz;
            this.pitch = pitch;
            this.roll = roll;
            uiHandler.post(this);
            Log.i(TAG, "move");
        }

        @Override
        public void land() {
            status = "LANDED";
            uiHandler.post(this);
            Log.i(TAG, "Land");
        }

        @Override
        public void takeOff() {
            status = "FLYING";
            uiHandler.post(this);
            Log.i(TAG, "Takeoff");
        }

        @Override
        public void flatTrim() {

        }
    }

    private void runRecognizerSetup() {
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(ControllerClientActivity.this);
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
                        apiStatusView.setText(text);
                    } else {
                        apiStatusView.setText(" no match");
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
}
