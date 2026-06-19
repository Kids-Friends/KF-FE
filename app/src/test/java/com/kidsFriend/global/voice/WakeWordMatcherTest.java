package com.kidsFriend.global.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 호출어("친구야") 인식 QA — 모든 시나리오의 공통 진입점.
 *
 * <p>짧은 호출어는 STT 최종 결과에서 누락되기 쉬워 호출 성공률을 우선한다.
 * 끊겼던 리팩토링(자모거리 → 유사도) 복원의 재현 테스트를 겸한다.</p>
 */
public class WakeWordMatcherTest {

    @Test
    public void 정확한_호출어를_인식한다() {
        assertTrue(WakeWordMatcher.containsWakeWord("친구야"));
    }

    @Test
    public void 띄어쓰기된_호출어도_인식한다() {
        assertTrue(WakeWordMatcher.containsWakeWord("친구 야 안녕"));
    }

    @Test
    public void 유사발음_오인식을_인식한다() {
        assertTrue("칭구야는 호출어여야 한다", WakeWordMatcher.containsWakeWord("칭구야"));
        assertTrue("친구얏은 호출어여야 한다", WakeWordMatcher.containsWakeWord("친구얏"));
        assertTrue("친구만 인식돼도 호출어로 본다", WakeWordMatcher.containsWakeWord("친구"));
        assertTrue("진구야는 호출어여야 한다", WakeWordMatcher.containsWakeWord("진구야"));
    }

    @Test
    public void 가까운_발음은_호출로_본다() {
        assertTrue("'친구가'도 현장 STT 오인식 가능성이 높아 호출로 본다",
                WakeWordMatcher.containsWakeWord("친구가 놀러왔어"));
    }

    @Test
    public void 호출어_뒤_발화를_분리한다() {
        assertEquals("회원등록 하고 싶어",
                WakeWordMatcher.textAfterWakeWord("친구야 회원등록 하고 싶어"));
    }

    @Test
    public void 호출어만_제거하고_나머지는_보존한다() {
        assertEquals("회원등록 하고 싶어",
                WakeWordMatcher.removeWakeWord("친구야 회원등록 하고 싶어"));
    }
}
