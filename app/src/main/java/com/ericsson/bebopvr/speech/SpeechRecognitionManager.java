package com.ericsson.bebopvr.speech;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.ericsson.bebopvr.ControllerClientActivity;
import com.ericsson.bebopvr.dron.DroneService;

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
 * Created by eanduso on 7/16/2017.
 */

public class SpeechRecognitionManager {

    private static final String TAG = "SpeechRecognition";

    private SpeechRecognizer recognizer;

    private List<DroneService> droneListeners = new ArrayList<>();

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

    private Context context;

    public SpeechRecognitionManager(Context context) {
        this.context = context;
    }

    public void start() {
        runRecognizerSetup();
    }

    public void stop() {
        if (recognizer != null) {
            recognizer.stop();
        }
    }

    public void addDroneService(DroneService service) {
        droneListeners.add(service);
    }

    private void runRecognizerSetup() {
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(context);
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
                        }

                    } else {
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
