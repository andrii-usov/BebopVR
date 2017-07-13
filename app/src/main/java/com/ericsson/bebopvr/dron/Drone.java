package com.ericsson.bebopvr.dron;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import com.ericsson.bebopvr.DeviceListActivity;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIASTREAMING_VIDEOSTREAMMODE_MODE_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEOFRAMERATE_FRAMERATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DICTIONARY_KEY_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_ERROR_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARControllerDictionary;
import com.parrot.arsdk.arcontroller.ARControllerException;
import com.parrot.arsdk.arcontroller.ARDeviceController;
import com.parrot.arsdk.arcontroller.ARDeviceControllerListener;
import com.parrot.arsdk.arcontroller.ARDeviceControllerStreamListener;
import com.parrot.arsdk.arcontroller.ARFrame;
import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDevice;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryException;

import java.util.LinkedList;
import java.util.List;

/**
 * @author valerjanka
 */
public class Drone {
    private static final String TAG = "Drone";
    private final Handler handler;
    private ARCONTROLLER_DEVICE_STATE_ENUM state;
    private List<DroneListener> droneListeners = new LinkedList<>();
    private Activity context;
    private ARDiscoveryDeviceService deviceService;
    private ARDeviceController deviceController;
    private int enabled = 0;

    public Drone(Activity context) {
        this.context = context;
        Intent intent = context.getIntent();
        deviceService = intent.getParcelableExtra(DeviceListActivity.EXTRA_DEVICE_SERVICE);

        // needed because some callbacks will be called on the main thread
        handler = new Handler(context.getMainLooper());

        state = ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED;

        ARDiscoveryDevice discoveryDevice = createDiscoveryDevice(deviceService);
        if (discoveryDevice != null) {
            deviceController = createDeviceController(discoveryDevice);
            discoveryDevice.dispose();
        } else {
            Log.e(TAG, "discoveryDevice is null");
        }
    }


    public DroneService getDroneService() {
        return droneService;
    }

    public void addDroneListener(DroneListener droneListener) {
        droneListeners.add(droneListener);
    }

    public void dispose()
    {
        enabled = 0;
        if (deviceController != null)
            deviceController.dispose();
    }

    /**
     * Connect to the drone
     * @return true if operation was successful.
     *              Returning true doesn't mean that device is connected.
     */
    public boolean connect() {
        boolean success = false;
        enabled = 0;
        if ((deviceController != null) && (ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED.equals(state))) {
            ARCONTROLLER_ERROR_ENUM error = deviceController.start();
            if (error == ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                success = true;
            }
        }
        return success;
    }

    /**
     * Disconnect from the drone
     * @return true if operation was successful.
     *              Returning true doesn't mean that device is disconnected.
     */
    public boolean disconnect() {
        enabled = 0;
        boolean success = false;
        if ((deviceController != null) && (ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(state))) {
            ARCONTROLLER_ERROR_ENUM error = deviceController.stop();
            if (error == ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
                success = true;
            }
        }
        return success;
    }

    private ARDiscoveryDevice createDiscoveryDevice(@NonNull ARDiscoveryDeviceService service) {
        ARDiscoveryDevice device = null;
        try {
            device = new ARDiscoveryDevice(context, service);
        } catch (ARDiscoveryException e) {
            Log.e(TAG, "Exception", e);
            Log.e(TAG, "Error: " + e.getError());
        }

        return device;
    }

    private ARDeviceController createDeviceController(@NonNull ARDiscoveryDevice discoveryDevice) {
        ARDeviceController deviceController = null;
        try {
            deviceController = new ARDeviceController(discoveryDevice);

            deviceController.addListener(deviceControllerListener);
            deviceController.addStreamListener(streamListener);
        } catch (ARControllerException e) {
            Log.e(TAG, "Exception", e);
        }

        return deviceController;
    }

    private final ARDeviceControllerListener deviceControllerListener = new ARDeviceControllerListener() {

        @Override
        public void onStateChanged(ARDeviceController deviceController, ARCONTROLLER_DEVICE_STATE_ENUM newState, ARCONTROLLER_ERROR_ENUM error) {
            Log.d(TAG, "onStateChanged: " + newState + ", error: " + error);
            state = newState;
            if (ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(state) && enabled < 5) {
                ++enabled;
                deviceController.getFeatureARDrone3().sendMediaStreamingVideoStreamMode(ARCOMMANDS_ARDRONE3_MEDIASTREAMING_VIDEOSTREAMMODE_MODE_ENUM.ARCOMMANDS_ARDRONE3_MEDIASTREAMING_VIDEOSTREAMMODE_MODE_HIGH_RELIABILITY);
                deviceController.getFeatureARDrone3().sendPictureSettingsVideoFramerate((ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEOFRAMERATE_FRAMERATE_ENUM.ARCOMMANDS_ARDRONE3_PICTURESETTINGS_VIDEOFRAMERATE_FRAMERATE_24_FPS));
                ARCONTROLLER_ERROR_ENUM res = deviceController.getFeatureARDrone3().sendMediaStreamingVideoEnable((byte) 1);
                Log.d(TAG, "Enabling video streaming " + res);
                deviceController.getFeatureARDrone3().sendSpeedSettingsHullProtection((byte)1);
            }
            for(DroneListener droneListener : droneListeners) {
                droneListener.onStateChanged(newState);
            }
        }

        @Override
        public void onExtensionStateChanged(ARDeviceController deviceController, ARCONTROLLER_DEVICE_STATE_ENUM newState, ARDISCOVERY_PRODUCT_ENUM product, String name, ARCONTROLLER_ERROR_ENUM error) {
            Log.d(TAG, "onExtensionStateChanged: " + newState);
        }

        @Override
        public void onCommandReceived(ARDeviceController deviceController, ARCONTROLLER_DICTIONARY_KEY_ENUM commandKey, ARControllerDictionary elementDictionary) {
            Log.d(TAG, "onCommandReceived: " + commandKey);
            for(DroneListener droneListener : droneListeners) {
                droneListener.onCommandReceived(commandKey, elementDictionary);
            }
        }
    };

    private final ARDeviceControllerStreamListener streamListener = new ARDeviceControllerStreamListener() {
        @Override
        public ARCONTROLLER_ERROR_ENUM configureDecoder(ARDeviceController deviceController, final ARControllerCodec codec) {
            for(DroneListener droneListener : droneListeners) {
                droneListener.configureDecoder(codec);
            }
            return ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
        }

        @Override
        public ARCONTROLLER_ERROR_ENUM onFrameReceived(ARDeviceController deviceController, final ARFrame frame) {
            for(DroneListener droneListener : droneListeners) {
                droneListener.onFrameReceived(frame);
            }
            return ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
        }

        @Override
        public void onFrameTimeout(ARDeviceController deviceController) {}
    };

    private final DroneService droneService = new DroneService() {
        @Override
        public void move(byte roll, byte pitch, byte yaw, byte gaz) {
            if(canDo()) {
                if( (roll == 0 && pitch == 0 ) || yaw != 0 || gaz != 0) {
                    deviceController.getFeatureARDrone3().setPilotingPCMD((byte) 0, roll, pitch, yaw, gaz, 0);
                } else {
                    deviceController.getFeatureARDrone3().setPilotingPCMD((byte) 1, roll, pitch, yaw, gaz, 0);
                }
            }
        }

        @Override
        public void land() {
            if(canDo()) {
                deviceController.getFeatureARDrone3().sendPilotingLanding();
            }
        }

        @Override
        public void takeOff() {
            if(canDo()) {
                deviceController.getFeatureARDrone3().sendPilotingTakeOff();
            }
        }

        @Override
        public void flatTrim() {
            if (canDo()) {
                deviceController.getFeatureARDrone3().sendPilotingFlatTrim();
            }
        }

        @Override
        public void takeAPicture() {
            if (canDo()) {
                deviceController.getFeatureARDrone3().sendMediaRecordPictureV2();
            }
        }

        @Override
        public void startRecording() {
            if (canDo()) {
                deviceController.getFeatureARDrone3().sendMediaRecordVideoV2(ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_START);
            }
        }

        @Override
        public void stopRecording() {
            if (canDo()) {
                deviceController.getFeatureARDrone3().sendMediaRecordVideoV2(ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_ENUM.ARCOMMANDS_ARDRONE3_MEDIARECORD_VIDEOV2_RECORD_STOP);
            }
        }

        @Override
        public void doAFlip() {
            if (canDo()) {
                deviceController.getFeatureARDrone3().sendAnimationsFlip(ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_ENUM.ARCOMMANDS_ARDRONE3_ANIMATIONS_FLIP_DIRECTION_BACK);
            }
        }

        private boolean canDo() {
            return (deviceController != null) && (state.equals(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING));
        }
    };

    public ARCONTROLLER_DEVICE_STATE_ENUM getState() {
        return state;
    }
}
