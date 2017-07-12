package com.ericsson.bebopvr.controller;

import android.content.Context;

import com.ericsson.bebopvr.ControllerClientActivity;
import com.google.vr.sdk.controller.Controller;
import com.google.vr.sdk.controller.ControllerManager;

/**
 * Created by eanduso on 7/12/2017.
 */

public class DroneControllerManager {

    // These two objects are the primary APIs for interacting with the Daydream controller.
    private com.google.vr.sdk.controller.ControllerManager controllerManager;
    private Controller controller;

    public DroneControllerManager (Context androidContext) {
        EventListener listener = new EventListener();
        controllerManager = new ControllerManager(androidContext, listener);
        controller = controllerManager.getController();
        controller.setEventListener(listener);
    }


    private class EventListener extends Controller.EventListener
            implements ControllerManager.EventListener {

        // The status of the overall controller API. This is primarily used for error handling since
        // it rarely changes.
        private String apiStatus;

        // The state of a specific Controller connection.
        private int controllerState = Controller.ConnectionStates.DISCONNECTED;

        @Override
        public void onApiStatusChanged(int state) {
            apiStatus = ControllerManager.ApiStatus.toString(state);
        }

        @Override
        public void onConnectionStateChanged(int state) {
            controllerState = state;
        }

        @Override
        public void onRecentered() {
        }

        @Override
        public void onUpdate() {
            controller.update();

            float[] angles = new float[3];
            controller.orientation.toYawPitchRollDegrees(angles);
            String s = String.format(
                    "%s\n%s\n[%4.0f\u00b0 y %4.0f\u00b0 p %4.0f\u00b0 r]",
                    controller.orientation,
                    controller.orientation.toAxisAngleString(),
                    angles[0], angles[1], angles[2]);

            if (controller.isTouching) {
                String touching  = String.format("[%4.2f, %4.2f]", controller.touch.x, controller.touch.y);
            } else {

            }

            String buttonState = String.format("[%s][%s][%s][%s][%s]",
                    controller.appButtonState ? "A" : " ",
                    controller.homeButtonState ? "H" : " ",
                    controller.clickButtonState ? "T" : " ",
                    controller.volumeUpButtonState ? "+" : " ",
                    controller.volumeDownButtonState ? "-" : " ");
        }

    }

}
