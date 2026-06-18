package com.kidsFriend.global.voice;

import android.util.Log;

/**
 * 호출어("친구야") 인식 판정기.
 *
 * <p>테미 STT는 짧은 호출어를 종종 비슷한 발음으로 잘못 인식한다(예: 친쿠야/칭구야/친구여/친구얌).
 * 정확 일치만 보면 이런 발화가 전부 버려져 인식률이 떨어진다. 그래서 다음 순서로 판정한다.
 * <ol>
 *   <li>정확 일치: 문장에 "친구야"가 그대로 들어있나.</li>
 *   <li>변형 리스트: 로그에서 모은 흔한 오인식 토큰.</li>
 *   <li>자모 편집거리: 토큰을 초/중/종성으로 분해해 "친구야"와 거리 1 이내면 호출어로 본다.</li>
 * </ol>
 * 거리 1로 제한해 일상 발화(예: "내 친구가")가 로봇을 깨우는 오작동(false wake)을 억제한다.
 */
public class WakeWordMatcher {
    private static final String TAG = "WakeWordMatcher";
    private static final String WAKE_WORD = "친구야";

    /** 자모 편집거리 허용치. 1 = 한 글자(자모) 오인식까지 호출어로 인정. */
    private static final int MAX_JAMO_DISTANCE = 1;

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