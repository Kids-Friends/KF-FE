package com.kidsFriend.global.voice;

import android.util.Log;

import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;

public class TemiSpeechSpeaker {
    private static final String TAG = "TemiSpeechSpeaker";
    private static final long DEBOUNCE_MS = 3000;
    private long lastSpeakTime = 0;
    private final boolean debounce;

    /** 기본: 디바운스 ON(센서/돌발상황용). 다중 센서 동시 발동 TTS 스팸 방지. */
    public TemiSpeechSpeaker() {
        this(true);
    }

    /**
     * @param debounce 3초 쿨다운 적용 여부.
     *                 센서/리실리언스 발화처럼 짧은 시간에 몰릴 수 있는 경우는 true,
     *                 대화 흐름(웨이크 응답 직후 AI 답변 등 반드시 들려야 하는 발화)은 false로 둔다.
     *                 false가 아니면 fast-path에서 AI 답변이 쿨다운에 먹혀 로봇이 화면만 띄우고
     *                 말은 안 하는 "조용한 답변" 데모 실패가 발생한다.
     */
    public TemiSpeechSpeaker(boolean debounce) {
        this.debounce = debounce;
    }

    /**
     * 발화를 요청하고 생성된 {@link TtsRequest}를 반환합니다.
     * 반환된 요청의 id로 TTS 완료 시점을 추적할 수 있습니다. 실패 시 null.
     */
    public TtsRequest speak(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        // [방어 코드] 다중 센서 동시 발동으로 인한 TTS 스팸/겹침 방지 (3초 쿨다운)
        if (debounce && System.currentTimeMillis() - lastSpeakTime < DEBOUNCE_MS) {
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
