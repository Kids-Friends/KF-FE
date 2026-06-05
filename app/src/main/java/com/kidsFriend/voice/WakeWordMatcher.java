package com.kidsFriend.voice;

import android.util.Log;

public class WakeWordMatcher {
    private static final String TAG = "WakeWordMatcher";
    private static final String WAKE_WORD = "친구야";

    private WakeWordMatcher() {
    }

    public static boolean containsWakeWord(String text) {
        try {
            return normalize(text).contains(WAKE_WORD);
        } catch (Exception e) {
            Log.e(TAG, "containsWakeWord exception", e);
            return false;
        }
    }

    public static String removeWakeWord(String text) {
        try {
            return normalizeSentence(text)
                    .replace("친구 야", WAKE_WORD)
                    .replace(WAKE_WORD, "")
                    .trim();
        } catch (Exception e) {
            Log.e(TAG, "removeWakeWord exception", e);
            return text != null ? text.trim() : "";
        }
    }

    public static String textAfterWakeWord(String text) {
        try {
            String sentence = normalizeSentence(text).replace("친구 야", WAKE_WORD);
            int wakeWordIndex = sentence.indexOf(WAKE_WORD);
            if (wakeWordIndex < 0 || wakeWordIndex + WAKE_WORD.length() > sentence.length()) {
                return "";
            }
            return sentence.substring(wakeWordIndex + WAKE_WORD.length()).trim();
        } catch (Exception e) {
            Log.e(TAG, "textAfterWakeWord exception", e);
            return "";
        }
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
}
