package com.kidsFriend.voice;

import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;

public class TemiSpeechSpeaker {
    private static final String TAG = "TemiSpeechSpeaker";

    // TTS 모드 설정: true면 외부 TTS(URL 기반), false면 테미 기본 TTS
    private static final boolean USE_EXTERNAL_TTS = false;
    // 외부 TTS API URL (예: 뽀로로 목소리를 생성해주는 서버 주소)
    private static final String EXTERNAL_TTS_API_URL = "http://your-tts-api.com/speak?text=";

    /**
     * 발화를 요청하고 생성된 {@link TtsRequest}를 반환합니다.
     * 외부 TTS 사용 시에는 null을 반환하고 오디오를 재생합니다.
     */
    public TtsRequest speak(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        if (USE_EXTERNAL_TTS) {
            speakWithExternalVoice(text);
            return null;
        }

        try {
            TtsRequest request = TtsRequest.create(
                    text,
                    false, // readyToListen을 false로 변경하여 수동 제어와 겹치지 않게 함
                    TtsRequest.Language.KO_KR,
                    false,
                    false
            );
            Robot.getInstance().speak(request);
            return request;
        } catch (RuntimeException exception) {
            Log.w(TAG, "Temi TTS is not available in this environment.", exception);
            return null;
        }
    }

    /**
     * 외부 TTS(예: 뽀로로 목소리)를 사용하여 발화합니다.
     * 텍스트를 오디오 URL로 변환하여 테미에서 재생하도록 구현합니다.
     */
    private void speakWithExternalVoice(String text) {
        String audioUrl = EXTERNAL_TTS_API_URL + Uri.encode(text);
        try {
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(audioUrl);
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnCompletionListener(mp -> {
                mp.reset();
                mp.release();
            });
            mediaPlayer.prepareAsync();
            Log.d(TAG, "Playing external TTS: " + audioUrl);
        } catch (Exception e) {
            Log.e(TAG, "Failed to play external TTS", e);
        }
    }
}
