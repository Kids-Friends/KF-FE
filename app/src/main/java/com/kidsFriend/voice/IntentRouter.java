package com.kidsFriend.voice;

/**
 * 아이의 발화를 듣고 어떤 기능 화면으로 이동할지 결정합니다.
 * 매칭되는 키워드가 없으면 AI 질문(CHAT)으로 처리합니다.
 */
public final class IntentRouter {

    public enum Intent {
        QUIZ,
        MEMBERSHIP,
        PHOTO,
        CHAT
    }

    private static final String[] QUIZ_KEYWORDS = {"퀴즈", "문제", "맞히", "맞춰", "오엑스", "ox"};
    private static final String[] MEMBERSHIP_KEYWORDS = {"포인트", "회원", "회원카드", "카드", "몇 점", "몇점", "내 점수", "스탬프", "별"};
    private static final String[] PHOTO_KEYWORDS = {"치즈", "사진", "촬영", "찍어", "김치", "찰칵"};

    private IntentRouter() {
    }

    public static Intent route(String text) {
        String normalized = text == null ? "" : text.toLowerCase().replaceAll("\\s+", "");

        if (containsAny(normalized, QUIZ_KEYWORDS)) {
            return Intent.QUIZ;
        }
        if (containsAny(normalized, MEMBERSHIP_KEYWORDS)) {
            return Intent.MEMBERSHIP;
        }
        if (containsAny(normalized, PHOTO_KEYWORDS)) {
            return Intent.PHOTO;
        }
        return Intent.CHAT;
    }

    private static boolean containsAny(String normalized, String[] keywords) {
        for (String keyword : keywords) {
            if (normalized.contains(keyword.replaceAll("\\s+", ""))) {
                return true;
            }
        }
        return false;
    }
}
