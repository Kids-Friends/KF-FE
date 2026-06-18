package com.kidsFriend.global.voice;

/**
 * 한글 음성 유사도 계산 유틸.
 *
 * <p>STT는 짧은 한국어 단어를 비슷한 발음으로 자주 잘못 인식한다(예: 미끄럼틀→미끄럼들, 트램폴린→트람폴린).
 * 음절을 초/중/종성 자모로 분해한 뒤 편집거리(레벤슈타인)를 재면, 단순 문자열 비교보다 발음 오인식에
 * 훨씬 강한 유사도를 얻는다. {@link WakeWordMatcher}(호출어)와 의도 분기/음성 보정이 공용으로 쓴다.
 */
public final class KoreanPhonetics {

    private KoreanPhonetics() {
    }

    // 한글 음절 → 초/중/종성 자모 분해 테이블.
    private static final char[] CHO = {
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ',
            'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };
    private static final char[] JUNG = {
            'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ', 'ㅙ',
            'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ'
    };
    private static final char[] JONG = {
            '\0', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ',
            'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ', 'ㅆ', 'ㅇ',
            'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    /** 한글 음절을 자모 시퀀스로 분해(거리 계산용). 한글이 아니면 원문 유지. */
    public static String decompose(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0xAC00 && c <= 0xD7A3) {
                int index = c - 0xAC00;
                int cho = index / (21 * 28);
                int jung = (index % (21 * 28)) / 28;
                int jong = index % 28;
                sb.append(CHO[cho]).append(JUNG[jung]);
                if (jong != 0) {
                    sb.append(JONG[jong]);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 두 문자열을 자모 분해한 뒤 잰 편집거리. 값이 작을수록 발음이 비슷하다. */
    public static int jamoDistance(String a, String b) {
        return levenshtein(decompose(a), decompose(b));
    }

    /** 자모 기준 유사도 0.0~1.0. 1.0 = 완전 동일 발음. */
    public static double similarity(String a, String b) {
        String da = decompose(a);
        String db = decompose(b);
        int max = Math.max(da.length(), db.length());
        if (max == 0) {
            return 1.0;
        }
        return 1.0 - (double) levenshtein(da, db) / max;
    }

    /**
     * {@code text} 안에 {@code keyword}와 발음이 충분히 비슷한 구간이 있는지 검사한다.
     * 정확 포함을 먼저 본 뒤, 키워드 길이(±1) 음절 윈도를 밀며 유사도를 잰다.
     *
     * @param minSimilarity 통과 임계값(0~1). 높을수록 보수적(오매칭 적음).
     */
    public static boolean containsSimilar(String text, String keyword, double minSimilarity) {
        if (text == null || keyword == null) {
            return false;
        }
        String norm = stripAll(text);
        String key = stripAll(keyword);
        if (key.isEmpty() || norm.isEmpty()) {
            return false;
        }
        if (norm.contains(key)) {
            return true;
        }
        int kl = key.length();
        for (int w = Math.max(1, kl - 1); w <= kl + 1; w++) {
            for (int i = 0; i + w <= norm.length(); i++) {
                if (similarity(norm.substring(i, i + w), key) >= minSimilarity) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 공백/문장부호 제거(비교 정규화용). */
    public static String stripAll(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\s.,!?~]", "").trim();
    }

    /** 표준 레벤슈타인 편집거리(롤링 배열). */
    public static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}
