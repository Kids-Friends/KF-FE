package com.kidsFriend.voice;

import android.util.Log;

import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;

public class TemiSpeechSpeaker {
    private static final String TAG = "TemiSpeechSpeaker";
    private long lastSpeakTime = 0;

    /**
     * 발화를 요청하고 생성된 {@link TtsRequest}를 반환합니다.
     * 반환된 요청의 id로 TTS 완료 시점을 추적할 수 있습니다. 실패 시 null.
     */
    public TtsRequest speak(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        // [방어 코드] 다중 센서 동시 발동으로 인한 TTS 스팸/겹침 방지 (3초 쿨다운)
        if (System.currentTimeMillis() - lastSpeakTime < 3000) {
            Log.w(TAG, "speak: Ignored to prevent TTS spamming. Text: " + text);
            return null;
        }
        lastSpeakTime = System.currentTimeMillis();

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
