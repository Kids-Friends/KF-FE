package com.example.temiapplication.voice;

import android.util.Log;

import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;

public class TemiSpeechSpeaker {
    private static final String TAG = "TemiSpeechSpeaker";

    public boolean speak(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        try {
            Robot.getInstance().speak(TtsRequest.create(
                    text,
                    true,
                    TtsRequest.Language.KO_KR,
                    false,
                    false
            ));
            return true;
        } catch (RuntimeException exception) {
            Log.w(TAG, "Temi TTS is not available in this environment.", exception);
            return false;
        }
    }
}
