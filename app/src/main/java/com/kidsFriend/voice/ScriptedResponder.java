package com.kidsFriend.voice;

/**
 * 데모-세이프 고정 답변기.
 *
 * <p>미세먼지(공기질)는 AI가 알 수 없는 실시간 센서 사실이므로, AI에 맡기면 환각/오답
 * 위험이 있다. 이 항목만 AI 호출 없이 고정 답변을 돌려준다. 그 외 자유 질문(정체/이름/
 * 엄마 위치 등)은 {@code null}을 반환해 기존 AI(CHAT) 경로로 자연스럽게 넘긴다.</p>
 */
public final class ScriptedResponder {

    private ScriptedResponder() {
    }

    /** 매칭되면 고정 답변, 아니면 null(=AI로 넘김). */
    public static String answer(String text) {
        String t = text == null ? "" : text.replaceAll("\\s+", "");

        // 미세먼지/공기질 (실시간 센서 사실 → 고정)
        if (t.contains("미세먼지") || t.contains("먼지") || t.contains("공기")) {
            return "지금 미세먼지 상태는 좋음이야! 신나게 놀아도 괜찮아!";
        }
        return null;
    }
}
