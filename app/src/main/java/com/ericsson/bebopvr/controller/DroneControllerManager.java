package com.ericsson.bebopvr.controller;

import android.content.Context;
import android.os.SystemClock;

import com.ericsson.bebopvr.dron.DroneService;
import com.google.vr.sdk.controller.Controller;
import com.google.vr.sdk.controller.ControllerManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by eanduso on 7/12/2017.
 */

public class DroneControllerManager {

    // These two objects are the primary APIs for interacting with the Daydream controller.
    private com.google.vr.sdk.controller.ControllerManager controllerManager;
    private Controller controller;
    private List<DroneService> bebopListeners = new ArrayList<>();

    public DroneControllerManager (Context androidContext) {
        EventListener listener = new EventListener();
        controllerManager = new ControllerManager(androidContext, listener);
        controller = controllerManager.getController();
        controller.setEventListener(listener);
    }

    public void addDroneService(DroneService droneService) {
        this.bebopListeners.add(droneService);
    }

    public Controller getController() {
        return this.controller;
    }

    public void start() {
        this.controllerManager.start();
    }

    public void stop(){
        this.controllerManager.stop();
    }

    private class EventListener extends Controller.EventListener
            implements ControllerManager.EventListener {

        // The status of the overall controller API. This is primarily used for error handling since
        // it rarely changes.
        private int apiStatus;

        // The state of a specific Controller connection.
        private int controllerState = Controller.ConnectionStates.DISCONNECTED;

        private boolean isNavigating = false;

        private float[] startYPR;

        private final int SPEED_COEFICIENT = 1;

        @Override
        public void onApiStatusChanged(int state) { apiStatus = state;
        }

        @Override
        public void onConnectionStateChanged(int state) {
            controllerState = state;
        }

        @Override
        public void onRecentered() {
            startYPR = new float[3];
        }

        @Override
        public void onUpdate() {
            controller.update();
            if (controllerState == Controller.ConnectionStates.CONNECTED
                    && apiStatus == ControllerManager.ApiStatus.OK) {

                if (isLandButtonPressed()) {
                    for (DroneService bebopListener: bebopListeners) {
                        bebopListener.move((byte)0,(byte)0,(byte)0,(byte)0);
                        bebopListener.land();
                    }
                } else if (isTakeOffButtonPressed()) {
                    for (DroneService bebopListener: bebopListeners) {
                        bebopListener.flatTrim();
                        bebopListener.takeOff();
                    }
                } else if (isNavigating()) {
                    if (!isNavigating) {
                        isNavigating = true;
                        startYPR = new float[3];
                        controller.orientation.toYawPitchRollDegrees(startYPR);
                    } else {
                        byte gaz = 0;
                        byte yaw = 0;
                        byte pitch = 0;
                        byte roll = 0;

                        float touch = controller.touch.y;

                        if (touch > 0 && touch < 0.3) {
                            gaz = 30;
                        } else if (touch > 0.7 && touch < 1) {
                            gaz = -20;
                        }

                        float[] anglesDiff = new float[3];
                        controller.orientation.toYawPitchRollDegrees(anglesDiff);

                        for (int i = 0; i < 3; i++) {
                            anglesDiff[i] = startYPR[i] - anglesDiff[i];
                        }

                        if (anglesDiff[0] > 0 && Math.abs(anglesDiff[0]) > 40) {
                            yaw = 60;
                        } else if (anglesDiff[0] < 0 && Math.abs(anglesDiff[0]) > 40) {
                            yaw = -60;
                        }

                        if (anglesDiff[1] > 0 && Math.abs(anglesDiff[1]) > 20) {
                            pitch = 30;
                        } else if (anglesDiff[1] < 0 && Math.abs(anglesDiff[1]) > 20) {
                            pitch = -30;
                        }

                        if (anglesDiff[2] > 0 && Math.abs(anglesDiff[2]) > 20) {
                            roll = 30;
                        } else if (anglesDiff[2] < 0 && Math.abs(anglesDiff[2]) > 20) {
                            roll = -30;
                        }

                        for (DroneService bebopListener: bebopListeners) {
                            bebopListener.move(roll, pitch, yaw, gaz);
                        }
                    }

                } else if (isFreezing()) {
                    isNavigating = false;
                    for (DroneService bebopListener: bebopListeners) {
                        bebopListener.move((byte)0,(byte)0,(byte)0,(byte)0);
                    }
                }
                SystemClock.sleep(10);
            }

        }

        public boolean isTakeOffButtonPressed() {
            if (!controller.isTouching
                    && !controller.clickButtonState
                    && !controller.appButtonState
                    && !controller.homeButtonState
                    && !controller.volumeDownButtonState
                    && controller.volumeUpButtonState) {
                return true;
            }
            return false;
        }

        public boolean isLandButtonPressed() {
            return controller.volumeDownButtonState;
        }

        public boolean isNavigating() {
            if (controller.isTouching
                    && controller.clickButtonState
                    && !controller.appButtonState
                    && !controller.homeButtonState
                    && !controller.volumeUpButtonState
                    && !controller.volumeDownButtonState) {
                return true;
            }
            return false;
        }

        public boolean isFreezing() {
            if (!controller.clickButtonState
                    && !controller.appButtonState
                    && !controller.homeButtonState
                    && !controller.volumeUpButtonState
                    && !controller.volumeDownButtonState) {
                return true;
            }
            return false;
        }

    }

}
