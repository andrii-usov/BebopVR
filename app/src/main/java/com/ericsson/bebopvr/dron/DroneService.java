package com.ericsson.bebopvr.dron;

/**
 * @author valerjanka
 */
public interface DroneService {

    /**
     * Move drone according new coordinates.<br/>
     * All values are from [-100,100]<br/>
     * Roll: Go Right = 50, Go Left = -50. Rotation around the front-to-back axis<br/>
     * Pitch: Forward = 50, Backward = -50. Rotation around the side-to-side axis<br/>
     * Yaw: Rotate Right = 50, Rotate left = -50. Rotation around the vertical axis (horizontal)<br/>
     * Gaz: Go Up = 50, Go Down = -50.
     *
     * @param roll
     * @param pitch
     * @param yaw
     * @param gaz
     */
    void move(byte roll, byte pitch, byte yaw, byte gaz);

    void land();

    void takeOff();
}
