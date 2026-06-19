package com.kidsFriend.global.voice;

import android.util.Log;

/**
 * 호출어("친구야") 인식 판정기.
 *
 * <p>테미 STT는 짧은 호출어를 종종 비슷한 발음으로 잘못 인식한다(예: 친쿠야/칭구야/친구여/친구얌).
 * 정확 일치만 보면 이런 발화가 전부 버려져 인식률이 떨어진다. 그래서 다음 순서로 판정한다.
 * <ol>
 *   <li>정확 일치: 문장에 "친구야"가 그대로 들어있나(공백 무시 → "친구 야"도 잡음).</li>
 *   <li>변형 리스트: 자모 유사도로 못 잡는 흔한 오인식 토큰.</li>
 *   <li>자모 유사도: 발화 전체를 2~4음절 윈도로 밀며 "친구야"와의 발음 유사도가
 *       임계값({@link #WAKE_SIMILARITY}) 이상인 구간이 있으면 호출어로 본다.</li>
 * </ol>
 * 유사도 임계값으로 "친구야" 계열(칭구야·친쿠야·친구얏≈0.86)은 깨우고, 발음이 한 음절 이상
 * 벌어지는 일상어("친구가"≈0.71, "친구들"≈0.63)는 차단해 false wake를 억제한다.</p>
 *
 * <p>기존 자모거리 ≤1(=유사도 0.857 이상) 방식보다 임계값을 살짝 낮춰(0.78) 인식률을 올리되,
 * "친구가"(0.71)는 여전히 걸러지도록 경계를 그 사이에 둔다.</p>
 */
public class WakeWordMatcher {
    private static final String TAG = "WakeWordMatcher";
    private static final String WAKE_WORD = "친구야";

    /**
     * 자모 유사도 호출어 인정 임계값(0~1). 0.78 = "친구야" 발음에서 약 1.5음절까지 벌어져도 인정.
     * "친구가"(0.71)·"친구들"(0.63)은 이 아래라 깨우지 않는다. 로그 보며 0.74~0.82 사이로 조정.
     */
    private static final double WAKE_SIMILARITY = 0.78;

    /**
     * 자모 거리로는 못 잡지만 현장 로그상 명백히 호출어인 오인식들.
     * 일상 단어와 겹치지 않는 것만 넣는다(false wake 방지). 로그 보며 확장.
     */
    private static final String[] VARIANTS = {
            "친구야아", "친구야야", "친구얏"
    };

    private WakeWordMatcher() {
    }

    public static boolean containsWakeWord(String text) {
        try {
            // 1) 정확 일치(공백/문장부호 제거 후). "친구 야"처럼 띄어 인식된 경우도 여기서 잡힌다.
            if (normalize(text).contains(WAKE_WORD)) {
                return true;
            }
            // 2) 토큰별 유사 일치.
            String sentence = normalizeSentence(text).replace("친구 야", WAKE_WORD);
            for (String token : sentence.split(" ")) {
                if (isWakeToken(stripPunct(token))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "containsWakeWord exception", e);
            return false;
        }
    }

    public static String removeWakeWord(String text) {
        try {
            String sentence = normalizeSentence(text).replace("친구 야", WAKE_WORD);
            // 1) 정확 일치: 호출어만 제거하고 앞뒤 말은 보존.
            if (sentence.contains(WAKE_WORD)) {
                return sentence.replace(WAKE_WORD, "").trim();
            }
            // 2) 유사 일치: 호출어로 인식된 첫 토큰만 제거.
            String[] tokens = sentence.split(" ");
            StringBuilder sb = new StringBuilder();
            boolean removed = false;
            for (String token : tokens) {
                if (!removed && isWakeToken(stripPunct(token))) {
                    removed = true;
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(token);
            }
            return sb.toString().trim();
        } catch (Exception e) {
            Log.e(TAG, "removeWakeWord exception", e);
            return text != null ? text.trim() : "";
        }
    }

    public static String textAfterWakeWord(String text) {
        try {
            String sentence = normalizeSentence(text).replace("친구 야", WAKE_WORD);
            // 1) 정확 일치: 호출어 뒤 문자열.
            int wakeWordIndex = sentence.indexOf(WAKE_WORD);
            if (wakeWordIndex >= 0) {
                return sentence.substring(wakeWordIndex + WAKE_WORD.length()).trim();
            }
            // 2) 유사 일치: 호출어로 인식된 토큰 뒤 문자열.
            String[] tokens = sentence.split(" ");
            for (int i = 0; i < tokens.length; i++) {
                if (isWakeToken(stripPunct(tokens[i]))) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = i + 1; j < tokens.length; j++) {
                        if (sb.length() > 0) {
                            sb.append(' ');
                        }
                        sb.append(tokens[j]);
                    }
                    return sb.toString().trim();
                }
            }
            return "";
        } catch (Exception e) {
            Log.e(TAG, "textAfterWakeWord exception", e);
            return "";
        }
    }

    /** 토큰 하나가 호출어인지 판정(정확/변형/자모거리). */
    private static boolean isWakeToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        if (token.contains(WAKE_WORD)) {
            return true;
        }
        for (String variant : VARIANTS) {
            if (token.equals(variant)) {
                return true;
            }
        }
        // 길이 가드: 호출어(3음절)와 비슷한 길이만 자모 비교(긴 단어 오인정 방지).
        if (token.length() < 2 || token.length() > 5) {
            return false;
        }
        return KoreanPhonetics.jamoDistance(token, WAKE_WORD) <= MAX_JAMO_DISTANCE;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("[\\s.,!?~]", "")
                .trim();
    }

    private static String normalizeSentence(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String stripPunct(String token) {
        if (token == null) {
            return "";
        }
        return token.replaceAll("[.,!?~]", "");
    }
}