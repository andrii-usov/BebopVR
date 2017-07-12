package com.ericsson.bebopvr.dron;

import com.parrot.arsdk.arcontroller.ARDeviceController;

/**
 * @author valerjanka
 */
public class DroneServiceImpl implements DroneService {
    private ARDeviceController deviceController;

    public DroneServiceImpl(ARDeviceController deviceController) {
        this.deviceController = deviceController;
    }

    @Override
    public void move(byte roll, byte pitch, byte yaw, byte gaz) {
        deviceController.getFeatureARDrone3().setPilotingPCMD((byte)1, roll, pitch, yaw, gaz, 0);
    }

    @Override
    public void land() {
        deviceController.getFeatureARDrone3().sendPilotingLanding();
    }

    @Override
    public void takeOff() {
        deviceController.getFeatureARDrone3().sendPilotingTakeOff();
    }
}
