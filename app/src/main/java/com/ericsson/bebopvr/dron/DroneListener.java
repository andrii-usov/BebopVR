package com.ericsson.bebopvr.dron;

import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DICTIONARY_KEY_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARControllerDictionary;
import com.parrot.arsdk.arcontroller.ARFrame;

/**
 * @author valerjanka
 */
public interface DroneListener {
    /**
     * Called when the piloting state changes
     * Called in the main thread
     * @param state the piloting state of the drone
     */
    void onStateChanged(ARCONTROLLER_DEVICE_STATE_ENUM state);

    void onCommandReceived(ARCONTROLLER_DICTIONARY_KEY_ENUM commandKey, ARControllerDictionary elementDictionary);

    /**
     * Called when the video decoder should be configured
     * Called on a separate thread
     * @param codec the codec to configure the decoder with
     */
    void configureDecoder(ARControllerCodec codec);

    /**
     * Called when a video frame has been received
     * Called on a separate thread
     * @param frame the video frame
     */
    void onFrameReceived(ARFrame frame);
}
