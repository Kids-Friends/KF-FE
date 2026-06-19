package com.kidsFriend.domain.greeting.service;

import com.kidsFriend.global.voice.KoreanPhonetics;

/**
 * 아이의 발화에서 키워드를 추출해 어떤 시나리오로 분기할지 결정한다.
 *
 * <p><b>3단계 파이프라인</b>:
 * <ol>
 *   <li><b>전처리</b>: STT 불용어(음/어/그냥/좀 등) 제거 + 연속 공백 정규화.</li>
 *   <li><b>구(Phrase) 매칭 (+3)</b>: 의도를 표현하는 대표 구를 통째로 비교.
 *       단어 단독 키워드보다 높은 가중치를 줘 더 신뢰할 수 있는 신호로 취급한다.</li>
 *   <li><b>키워드 스코어링</b>: 정확 포함(+2) + 키워드 길이 적응형 자모 퍼지(+1).
 *       3~5자 키워드는 임계값 0.82, 6자 이상은 0.75로 STT 오인식 허용폭을 동적 조정한다.</li>
 * </ol>
 * 가장 높은 점수의 시나리오를 고르고, 동점이면 {@link Intent} 선언 순서(= 우선순위)로 깬다.
 * 어떤 신호도 없으면 {@link Intent#CHAT}(AI 자유질문)으로 폴백한다.</p>
 */
public final class IntentRouter {

    /** 정확 포함(exact) 점수. */
    private static final int SCORE_EXACT = 2;
    /** 자모 퍼지 매칭 점수. */
    private static final int SCORE_FUZZY = 1;
    /** 구(Phrase) 매칭 점수 — 단어 단독보다 높게 설정해 더 신뢰. */
    private static final int SCORE_PHRASE = 3;

    /** 퍼지 적용 최소 키워드 길이(이하는 오매칭 위험이 커서 건너뜀). */
    private static final int MIN_FUZZY_LENGTH = 3;

    /**
     * 키워드 길이별 퍼지 임계값.
     * 짧은 키워드(3~5자)는 조금만 달라도 다른 단어가 돼버리므로 엄격하게,
     * 긴 키워드(6자+)는 STT가 중간 음절을 더 많이 틀리므로 관대하게 설정한다.
     */
    private static final double FUZZY_THRESHOLD_SHORT = 0.82; // 3~5자 키워드
    private static final double FUZZY_THRESHOLD_LONG  = 0.75; // 6자 이상 키워드

    /**
     * STT가 불필요하게 섞어넣는 불용어.
     * 의도 분기 전에 단어 경계 기준으로 제거해 핵심 표현만 남긴다.
     */
    private static final String[] FILLER_WORDS = {
            "음", "어", "아", "이", "그", "근데", "그냥", "좀", "야", "이야",
            "있잖아", "저기", "있잖아요", "그러니까", "뭐", "말이야",
    };

    /** 선언 순서가 곧 동점 시 우선순위다(위가 높음). */
    public enum Intent {
        ENDING,         // 고마워/그만/안녕 → 대화 마무리 (가장 먼저 가로챔)
        AIR_QUALITY,    // 미세먼지/공기질
        PHOTO,          // 사진찍기 (기존 회원등록 대체)
        IDENTITY,       // 정체/누구야 → 자기소개
        LOCATION,       // 미끄럼틀/볼풀/화장실 등 위치 안내
        QUIZ,           // 퀴즈/문제
        FREE_QUESTION,  // 궁금/물어봐 → "뭐든 물어봐" 진입
        CHAT            // 폴백: AI 자유질문
    }

    // =========================================================================
    // 키워드 목록 (단어 단위)
    // =========================================================================

    private static final String[] ENDING_KEYWORDS = {
            "고마워", "고맙", "감사", "그만", "안녕", "됐어", "끝", "잘가", "바이",
            "그만할게", "이제됐어", "충분해", "알겠어", "수고했어", "잘했어"
    };
    private static final String[] AIR_QUALITY_KEYWORDS = {
            "미세먼지", "먼지", "공기", "미세", "날씨", "공기질", "오염", "대기",
            "깨끗", "나쁨", "좋음", "보통"
    };
    private static final String[] PHOTO_KEYWORDS = {
            "사진", "카메라", "찰칵", "찍어", "포즈", "김치", "치즈",
            "사진촬영", "촬영", "사진찍", "추억"
    };
    private static final String[] IDENTITY_KEYWORDS = {
            "정체", "누구야", "누구", "넌뭐", "정체가뭐", "넌누구", "이름이뭐",
            "뭐하는", "어디서왔", "자기소개", "소개해줘", "소개해봐", "만든사람"
    };
    private static final String[] LOCATION_KEYWORDS = {
            "미끄럼틀", "볼풀", "화장실", "트램폴린", "어디있", "어디야", "위치안내",
            "데려다", "가고싶", "찾아줘", "가르쳐줘", "알려줘", "어딨어",
            "어느쪽", "어떻게가", "길알려", "안내해줘", "같이가줘"
    };
    private static final String[] QUIZ_KEYWORDS = {
            "퀴즈", "문제", "맞히", "맞춰", "오엑스", "ox", "퀴즈풀",
            "문제낼", "시험", "맞춰봐", "알아맞춰", "맞혀봐"
    };
    private static final String[] FREE_QUESTION_KEYWORDS = {
            "궁금", "물어봐", "물어봐도", "질문있", "질문할", "알려줄래", "알고싶어",
            "알아", "혹시", "왜", "어떻게", "뭐야"
    };

    // =========================================================================
    // 구(Phrase) 목록 — 자연 발화에서 자주 나오는 의도 표현 (공백 제거 후 매칭)
    // =========================================================================

    private static final String[] ENDING_PHRASES = {
            "고마워", "감사해", "잘있어", "나중에봐", "다음에봐",
            "이제그만", "괜찮아이제", "됐어고마워", "수고했어", "잘있어"
    };
    private static final String[] AIR_QUALITY_PHRASES = {
            "미세먼지어때", "공기어때", "미세먼지알려줘", "공기알려줘",
            "먼지어때", "오늘공기", "오늘미세먼지", "지금미세먼지", "대기어때",
            "공기좋아", "미세먼지나빠", "공기괜찮아"
    };
    private static final String[] PHOTO_PHRASES = {
            "사진찍어줘", "사진찍고싶어", "사진촬영", "카메라켜줘",
            "나찍어줘", "사진한번찍자", "포즈잡아봐", "사진찍자",
            "치즈해봐", "찰칵해줘"
    };
    private static final String[] IDENTITY_PHRASES = {
            "넌정체가뭐야", "너는누구야", "이름이뭐야", "자기소개해봐",
            "넌뭐하는애야", "어디서왔어", "누가만들었어", "넌누구야",
            "뭐하는로봇이야", "이름이뭐니", "넌어떤로봇이야", "소개해줘"
    };
    private static final String[] LOCATION_PHRASES = {
            "미끄럼틀어디있어", "볼풀어디있어", "화장실어디있어", "트램폴린어디있어",
            "어디있는지알려줘", "어디로가면돼", "어디에있어", "같이가줘",
            "데려다줘", "안내해줘", "길알려줘", "어떻게가야해",
            "어디있는지알아", "찾아줘"
    };
    private static final String[] QUIZ_PHRASES = {
            "퀴즈하고싶어", "퀴즈풀고싶어", "퀴즈해줘", "문제내줘",
            "오엑스퀴즈", "퀴즈맞춰볼게", "문제풀고싶어", "퀴즈알려줘",
            "퀴즈맞혀볼게", "문제내볼게", "맞춰봐"
    };
    private static final String[] FREE_QUESTION_PHRASES = {
            "궁금한게있어", "물어봐도돼", "질문있어", "알려줄수있어",
            "알고싶어", "뭐든물어봐", "잘알아", "궁금해"
    };

    private IntentRouter() {}

    // =========================================================================
    // 공개 API
    // =========================================================================

    public static Intent route(String text) {
        // 1단계: 불용어 제거 + 공백 정규화
        String clean = removeFiller(text);
        String normalized = clean.toLowerCase().replaceAll("\\s+", "");
        if (normalized.isEmpty()) {
            return Intent.CHAT;
        }

        Intent best = Intent.CHAT;
        int bestScore = 0;
        // 선언 순서대로 평가 → 동점이면 먼저 본 쪽(높은 우선순위)을 유지
        for (Intent intent : Intent.values()) {
            int score = scoreIntent(normalized, intent);
            if (score > bestScore) {
                bestScore = score;
                best = intent;
            }
        }
        return best;
    }

    // =========================================================================
    // 내부 구현
    // =========================================================================

    /** 3단계 스코어링: 구 매칭 → 키워드 정확 → 키워드 퍼지. */
    private static int scoreIntent(String normalized, Intent intent) {
        int score = 0;

        // 2단계: 구(Phrase) 매칭 (+3)
        for (String phrase : phrasesFor(intent)) {
            String p = phrase.replaceAll("\\s+", "");
            if (normalized.contains(p)) {
                score += SCORE_PHRASE;
                break; // 구 하나만 매칭돼도 충분히 신뢰
            }
        }

        // 3단계: 키워드 스코어링
        for (String keyword : keywordsFor(intent)) {
            String k = keyword.replaceAll("\\s+", "");
            if (normalized.contains(k)) {
                score += SCORE_EXACT;
            } else if (k.length() >= MIN_FUZZY_LENGTH) {
                // 키워드 길이에 따라 퍼지 임계값 동적 조정
                double threshold = k.length() >= 6 ? FUZZY_THRESHOLD_LONG : FUZZY_THRESHOLD_SHORT;
                if (KoreanPhonetics.containsSimilar(normalized, k, threshold)) {
                    score += SCORE_FUZZY;
                }
            }
        }
        return score;
    }

    /**
     * STT 불용어를 단어 경계(앞뒤로 한글이 없는 지점) 기준으로 제거한다.
     * 의미 있는 음절("음악", "어디") 내부의 "음", "어"는 건드리지 않는다.
     */
    private static String removeFiller(String text) {
        if (text == null) return "";
        String result = text.trim();
        for (String filler : FILLER_WORDS) {
            // (?<![가-힣]) : 앞이 한글이 아닌 위치
            // (?![가-힣])  : 뒤가 한글이 아닌 위치 — 단어 경계 한정
            result = result.replaceAll(
                    "(?<![가-힣])" + filler + "(?![가-힣])", " ");
        }
        return result.replaceAll("\\s{2,}", " ").trim();
    }

    private static String[] keywordsFor(Intent intent) {
        switch (intent) {
            case ENDING:        return ENDING_KEYWORDS;
            case AIR_QUALITY:   return AIR_QUALITY_KEYWORDS;
            case PHOTO:         return PHOTO_KEYWORDS;
            case IDENTITY:      return IDENTITY_KEYWORDS;
            case LOCATION:      return LOCATION_KEYWORDS;
            case QUIZ:          return QUIZ_KEYWORDS;
            case FREE_QUESTION: return FREE_QUESTION_KEYWORDS;
            case CHAT: default: return new String[0];
        }
    }

    private static String[] phrasesFor(Intent intent) {
        switch (intent) {
            case ENDING:        return ENDING_PHRASES;
            case AIR_QUALITY:   return AIR_QUALITY_PHRASES;
            case PHOTO:         return PHOTO_PHRASES;
            case IDENTITY:      return IDENTITY_PHRASES;
            case LOCATION:      return LOCATION_PHRASES;
            case QUIZ:          return QUIZ_PHRASES;
            case FREE_QUESTION: return FREE_QUESTION_PHRASES;
            case CHAT: default: return new String[0];
        }
    }
}
