package com.kidsFriend.domain.chat;

import android.text.TextUtils;

import com.kidsFriend.global.voice.WakeWordMatcher;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class QuestionReconstructor {
    private static final Map<String, String> REPLACEMENTS = new LinkedHashMap<>();

    static {
        REPLACEMENTS.put("화장실 어디", "화장실은 어디에 있어");
        REPLACEMENTS.put("화장실 어딨어", "화장실은 어디에 있어");
        REPLACEMENTS.put("선생님 불러", "직원을 호출해");
        REPLACEMENTS.put("직원 불러", "직원을 호출해");
        REPLACEMENTS.put("도와 줘", "도와줘");
        REPLACEMENTS.put("도와 주세요", "도와주세요");
        REPLACEMENTS.put("물건 잃어버렸", "분실물이 있어");
        REPLACEMENTS.put("엄마 어디", "보호자는 어디에 있어");
    }

    private QuestionReconstructor() {
    }

    public static String reconstruct(String rawText) {
        String normalized = normalize(rawText);
        if (TextUtils.isEmpty(normalized)) {
            return "";
        }

        String withoutWakeWord = WakeWordMatcher.removeWakeWord(normalized);
        String reconstructed = withoutWakeWord;
        for (Map.Entry<String, String> entry : REPLACEMENTS.entrySet()) {
            if (reconstructed.contains(entry.getKey())) {
                reconstructed = entry.getValue();
                break;
            }
        }

        return finishAsQuestion(reconstructed);
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String finishAsQuestion(String text) {
        String trimmed = text.trim();
        if (trimmed.endsWith("?") || trimmed.endsWith(".")) {
            return trimmed;
        }

        String lower = trimmed.toLowerCase(Locale.KOREA);
        if (lower.endsWith("요") || lower.endsWith("까") || lower.endsWith("어")) {
            return trimmed + "?";
        }
        return trimmed + " 알려주세요.";
    }
}
