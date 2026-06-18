package com.kidsFriend.global.voice;

/**
 * 음성 인식 엔진 추상화. 테미 SDK 기반({@link TemiSttEngine})과 안드로이드 SpeechRecognizer 기반
 * ({@link AndroidSttEngine}) 두 구현을 {@link VoiceInputManager}가 상황에 맞게 골라 쓴다.
 */
public interface SttEngine {

    /**
     * 인식 시작.
     *
     * @param continuous true면 한 발화 처리 후 자동으로 다시 듣는다(웨이크 대기). false면 한 번만 듣는다.
     * @param callback   인식 콜백(부분결과/최종결과/에러).
     */
    void start(boolean continuous, VoiceInputManager.Callback callback);

    /** 현재 인식을 멈춘다. */
    void stop();

    /** 엔진 자원을 해제한다. */
    void destroy();
}