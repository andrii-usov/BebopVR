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
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import com.ericsson.bebopvr.controller.DroneControllerManager;
import com.ericsson.bebopvr.dron.Drone;
import com.ericsson.bebopvr.dron.DroneService;
import com.google.vr.sdk.base.AndroidCompat;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;

/**
 * Minimal example demonstrating how to receive and process Daydream controller input. It connects
 * to a Daydream Controller and displays a simple graphical and textual representation of the
 * controller's sensors. This example only works with Android N and Daydream-ready phones.
 */
public class ControllerClientActivity extends Activity {

    private static final String TAG = "ControllerClientActivity";

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

        // This configuration won't be required for normal GVR apps. However, since this sample doesn't
        // use GvrView, it needs pretend to be a VR app in order to receive controller events. The
        // Activity.setVrModeEnabled is only enabled on in N, so this is an GVR-internal utility method
        // to configure the app via reflection.
        //
        // If this sample is compiled with the N SDK, Activity.setVrModeEnabled can be called directly.
        AndroidCompat.setVrModeEnabled(this, true);
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
        }

        @Override
        public void land() {
            status = "LANDED";
            uiHandler.post(this);
        }

        @Override
        public void takeOff() {
            status = "FLYING";
            uiHandler.post(this);
        }
    }
}
