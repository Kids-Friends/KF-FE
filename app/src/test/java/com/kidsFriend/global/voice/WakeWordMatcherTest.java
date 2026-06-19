package com.kidsFriend.global.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 호출어("친구야") 인식 QA — 모든 시나리오의 공통 진입점.
 *
 * <p>유사도 기반 판정(similarity ≥ 0.78)이 "친구야" 계열은 깨우고 일상어는 차단하는지 검증한다.
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
    }

    @Test
    public void 일상어는_깨우지_않는다() {
        assertFalse("'친구가'는 false wake 차단", WakeWordMatcher.containsWakeWord("친구가 놀러왔어"));
        assertFalse("'친구들'은 false wake 차단", WakeWordMatcher.containsWakeWord("친구들이랑 놀자"));
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
