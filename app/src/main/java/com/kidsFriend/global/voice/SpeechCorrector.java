package com.kidsFriend.global.voice;

/**
 * 온디바이스 음성 보정기.
 *
 * <p>STT가 도메인 고유어를 비슷한 발음으로 잘못 인식한 토큰("미끄럼들"→"미끄럼틀", "트람폴린"→"트램폴린")을
 * 표준어로 스냅한다. 자모 유사도가 임계값을 넘고 길이가 비슷한 후보에만 적용해 과도한 치환을 막는다.
 *
 * <p>자유 대화(AI 질문) 원문을 망치지 않도록, 명령/장소 위주의 보수적 사전만 쓴다. 의도 분기는
 */
public final class SpeechCorrector {

    /** 토큰을 표준어로 스냅할 최소 유사도. */
    private static final double SNAP_THRESHOLD = 0.80;

    /** 도메인 표준어 사전(데모 고유어 위주, 2글자 이상). 현장 로그로 확장. */
    private static final String[] VOCAB = {
            "미끄럼틀", "트램폴린", "화장실", "볼풀", "회원등록", "회원카드",
            "포인트", "스탬프", "직원", "선생님", "사진", "카메라", "분실물", "보호자", "퀴즈"
    };

    private SpeechCorrector() {
    }

    /** 발화 텍스트의 각 토큰을 도메인 표준어로 스냅해 돌려준다. */
    public static String correct(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] tokens = trimmed.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(snap(token));
        }
        return sb.toString();
    }

    private static String snap(String token) {
        String stripped = KoreanPhonetics.stripAll(token);
        if (stripped.length() < 2) {
            return token;
        }
        String best = null;
        double bestSim = SNAP_THRESHOLD;
        for (String canonical : VOCAB) {
            // 길이가 한 글자 넘게 차이 나면 후보 제외(과도한 치환 방지).
            if (Math.abs(canonical.length() - stripped.length()) > 1) {
                continue;
            }
            double sim = KoreanPhonetics.similarity(stripped, canonical);
            if (sim > bestSim) {
                bestSim = sim;
                best = canonical;
            }
        }
        return (best != null && !best.equals(stripped)) ? best : token;
    }
}