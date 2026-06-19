package com.kidsFriend.domain.greeting.service;

import com.kidsFriend.global.voice.KoreanPhonetics;

/**
 * 아이의 발화에서 키워드를 추출해 어떤 시나리오로 분기할지 결정한다.
 *
 * <p>알고리즘(점수제): 시나리오마다 키워드 묶음을 두고, 발화에 키워드가 보이면 점수를 더한다.
 * <ul>
 *   <li>정확 포함  → +2 (확실한 신호)</li>
 *   <li>자모 퍼지  → +1 (STT 근사 오인식 "미끄럼들/회원동록" 흡수)</li>
 * </ul>
 * 가장 높은 점수의 시나리오를 고르고, 점수가 같으면 {@link Intent} 선언 순서(= 우선순위)로 깬다.
 * 어떤 키워드도 못 잡으면 {@link Intent#CHAT}(AI 자유질문)로 폴백한다.</p>
 *
 * <p>대본 키워드 매핑: 정체→IDENTITY, 등록→MEMBERSHIP, 미끄럼틀→LOCATION, 퀴즈→QUIZ,
 * 궁금→FREE_QUESTION, 먼지→AIR_QUALITY, 고마워/그만→ENDING.</p>
 */
public final class IntentRouter {

    /** 퍼지 매칭 통과 임계값. 높을수록 보수적(오라우팅 적음). */
    private static final double FUZZY_THRESHOLD = 0.75;
    /** 이 글자수 미만 키워드는 퍼지를 건너뛴다(짧은 단어는 오매칭 위험이 큼). */
    private static final int MIN_FUZZY_LENGTH = 3;

    private static final int SCORE_EXACT = 2;
    private static final int SCORE_FUZZY = 1;

    /** 선언 순서가 곧 동점 시 우선순위다(위가 높음). */
    public enum Intent {
        ENDING,         // 고마워/그만/안녕 → 대화 마무리 (가장 먼저 가로챔)
        AIR_QUALITY,    // 미세먼지/공기질
        MEMBERSHIP,     // 회원등록/가입
        IDENTITY,       // 정체/누구야 → 자기소개
        LOCATION,       // 미끄럼틀/볼풀/화장실 등 위치 안내
        QUIZ,           // 퀴즈/문제
        FREE_QUESTION,  // 궁금/물어봐 → "뭐든 물어봐" 진입
        CHAT            // 폴백: AI 자유질문
    }

    private static final String[] ENDING_KEYWORDS = {"고마워", "고맙", "감사", "그만", "안녕", "됐어", "끝", "잘가", "바이"};
    private static final String[] AIR_QUALITY_KEYWORDS = {"미세먼지", "먼지", "공기", "미세", "날씨"};
    private static final String[] MEMBERSHIP_KEYWORDS = {"회원등록", "회원가입", "회원", "등록", "가입", "멤버"};
    private static final String[] IDENTITY_KEYWORDS = {"정체", "누구야", "누구", "넌뭐", "정체가뭐", "넌누구", "이름이뭐"};
    private static final String[] LOCATION_KEYWORDS = {"미끄럼틀", "볼풀", "화장실", "트램폴린", "어디있", "어디야", "위치안내", "데려다", "가고싶"};
    private static final String[] QUIZ_KEYWORDS = {"퀴즈", "문제", "맞히", "맞춰", "오엑스", "ox"};
    private static final String[] FREE_QUESTION_KEYWORDS = {"궁금", "물어봐", "물어봐도", "질문있", "질문할", "알려줄래"};

    private IntentRouter() {
    }

    public static Intent route(String text) {
        String normalized = text == null ? "" : text.toLowerCase().replaceAll("\\s+", "");
        if (normalized.isEmpty()) {
            return Intent.CHAT;
        }

        Intent best = Intent.CHAT;
        int bestScore = 0;
        // 선언 순서대로 평가 → 동점이면 먼저 본 쪽(높은 우선순위)을 유지.
        for (Intent intent : Intent.values()) {
            int score = score(normalized, keywordsFor(intent));
            if (score > bestScore) {
                bestScore = score;
                best = intent;
            }
        }
        return best;
    }

    private static String[] keywordsFor(Intent intent) {
        switch (intent) {
            case ENDING: return ENDING_KEYWORDS;
            case AIR_QUALITY: return AIR_QUALITY_KEYWORDS;
            case MEMBERSHIP: return MEMBERSHIP_KEYWORDS;
            case IDENTITY: return IDENTITY_KEYWORDS;
            case LOCATION: return LOCATION_KEYWORDS;
            case QUIZ: return QUIZ_KEYWORDS;
            case FREE_QUESTION: return FREE_QUESTION_KEYWORDS;
            case CHAT:
            default: return new String[0];
        }
    }

    /** 키워드 묶음이 발화에서 얻는 총점(정확 +2, 퍼지 +1). 키워드별로 한 번만 가산. */
    private static int score(String normalized, String[] keywords) {
        int total = 0;
        for (String keyword : keywords) {
            String k = keyword.replaceAll("\\s+", "");
            if (normalized.contains(k)) {
                total += SCORE_EXACT;
            } else if (k.length() >= MIN_FUZZY_LENGTH
                    && KoreanPhonetics.containsSimilar(normalized, k, FUZZY_THRESHOLD)) {
                total += SCORE_FUZZY;
            }
        }
        return total;
    }
}
