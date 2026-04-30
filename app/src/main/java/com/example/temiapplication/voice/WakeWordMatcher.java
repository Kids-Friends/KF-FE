package com.example.temiapplication.voice;

public class WakeWordMatcher {
    private static final String WAKE_WORD = "친구야";

    private WakeWordMatcher() {
    }

    public static boolean containsWakeWord(String text) {
        return normalize(text).contains(WAKE_WORD);
    }

    public static String removeWakeWord(String text) {
        return normalizeSentence(text)
                .replace("친구 야", WAKE_WORD)
                .replace(WAKE_WORD, "")
                .trim();
    }

    public static String textAfterWakeWord(String text) {
        String sentence = normalizeSentence(text).replace("친구 야", WAKE_WORD);
        int wakeWordIndex = sentence.indexOf(WAKE_WORD);
        if (wakeWordIndex < 0) {
            return "";
        }
        return sentence.substring(wakeWordIndex + WAKE_WORD.length()).trim();
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
