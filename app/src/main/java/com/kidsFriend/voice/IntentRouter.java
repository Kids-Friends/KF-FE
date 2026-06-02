package com.kidsFriend.voice;

/**
 * 아이의 발화를 듣고 어떤 기능 화면으로 이동할지 결정합니다.
 * 매칭되는 키워드가 없으면 AI 질문(CHAT)으로 처리합니다.
 */
public final class IntentRouter {

    public enum Intent {
        QUIZ,
        MEMBERSHIP,
        CALL,
        HOME,
        REWARD,
        FOLLOW,
        CHAT
    }

    private static final String[] QUIZ_KEYWORDS = {"퀴즈", "문제", "맞히", "맞춰", "오엑스", "ox"};
    private static final String[] MEMBERSHIP_KEYWORDS = {"포인트", "회원", "회원카드", "카드", "몇 점", "몇점", "내 점수", "스탬프", "별"};
    private static final String[] CALL_KEYWORDS = {"호출", "불러", "선생님", "도와줘", "도움"};
    private static final String[] HOME_KEYWORDS = {"홈", "메인", "메뉴", "처음", "화면"};
    private static final String[] REWARD_KEYWORDS = {"리워드", "선물", "보상"};
    private static final String[] FOLLOW_KEYWORDS = {"따라와", "졸졸", "같이 가", "같이 가자", "팔로우", "따라가기"};

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
        if (containsAny(normalized, CALL_KEYWORDS)) {
            return Intent.CALL;
        }
        if (containsAny(normalized, HOME_KEYWORDS)) {
            return Intent.HOME;
        }
        if (containsAny(normalized, REWARD_KEYWORDS)) {
            return Intent.REWARD;
        }
        if (containsAny(normalized, FOLLOW_KEYWORDS)) {
            return Intent.FOLLOW;
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
