package com.kidsFriend.domain.greeting;

/**
 * 아이의 발화를 듣고 어떤 기능 화면으로 이동할지 결정합니다.
 * 매칭되는 키워드가 없으면 AI 질문(CHAT)으로 처리합니다.
 */
public final class IntentRouter {

    public enum Intent {
        QUIZ,
        REGISTER,
        MEMBERSHIP,
        LOCATION,
        PHOTO,
        CHAT
    }

    private static final String[] QUIZ_KEYWORDS = {"퀴즈", "문제", "맞히", "맞춰", "오엑스", "ox"};
    // 회원'등록'/'가입'은 신규 등록 흐름(REGISTER)으로, 회원카드 조회는 MEMBERSHIP으로 분기한다.
    private static final String[] REGISTER_KEYWORDS = {"회원등록", "등록", "가입"};
    private static final String[] MEMBERSHIP_KEYWORDS = {"포인트", "회원", "회원카드", "카드", "몇 점", "몇점", "내 점수", "스탬프", "별"};
    private static final String[] LOCATION_KEYWORDS = {"미끄럼틀", "볼풀", "화장실", "트램폴린", "어디있", "어디야", "위치안내", "데려다", "가고싶"};
    private static final String[] PHOTO_KEYWORDS = {"사진", "찍어", "카메라", "촬영", "포즈"};

    private IntentRouter() {
    }

    public static Intent route(String text) {
        String normalized = text == null ? "" : text.toLowerCase().replaceAll("\\s+", "");

        if (containsAny(normalized, QUIZ_KEYWORDS)) {
            return Intent.QUIZ;
        }
        if (containsAny(normalized, REGISTER_KEYWORDS)) {
            return Intent.REGISTER;
        }
        if (containsAny(normalized, MEMBERSHIP_KEYWORDS)) {
            return Intent.MEMBERSHIP;
        }
        if (containsAny(normalized, LOCATION_KEYWORDS)) {
            return Intent.LOCATION;
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
