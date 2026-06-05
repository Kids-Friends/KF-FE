package com.kidsFriend.voice;

import android.util.Log;

import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;

public class TemiSpeechSpeaker {
    private static final String TAG = "TemiSpeechSpeaker";

    /**
     * 발화를 요청하고 생성된 {@link TtsRequest}를 반환합니다.
     * 반환된 요청의 id로 TTS 완료 시점을 추적할 수 있습니다. 실패 시 null.
     */
    public TtsRequest speak(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        try {
            TtsRequest request = TtsRequest.create(
                    text,
                    true,
                    TtsRequest.Language.KO_KR,
                    false,
                    false
            );
            Robot.getInstance().speak(request);
            return request;
        } catch (Exception exception) {
            Log.w(TAG, "Temi TTS is not available in this environment.", exception);
            return null;
        }
    }
}
