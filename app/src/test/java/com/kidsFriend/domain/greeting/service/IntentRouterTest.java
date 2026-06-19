package com.kidsFriend.domain.greeting.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.kidsFriend.domain.greeting.service.IntentRouter.Intent;
import org.junit.Test;

/**
 * 의도 분기 QA — 발화 → 시나리오 라우팅(IntentRouter.route).
 *
 * <p>시나리오 2.1~2.7의 진입 분기를 대본 발화로 검증한다. STT 오인식("미끄럼들")과
 * 우선순위(작별 우선) 같은 경계 케이스도 포함한다.</p>
 */
public class IntentRouterTest {

    @Test
    public void 시나리오2_1_자기소개로_분기() {
        assertEquals(Intent.IDENTITY, IntentRouter.route("넌 정체가 뭐야"));
    }

    @Test
    public void 시나리오2_2_회원등록으로_분기() {
        assertEquals(Intent.MEMBERSHIP, IntentRouter.route("회원등록을 하고 싶어"));
    }

    @Test
    public void 시나리오2_3_놀이존_위치로_분기() {
        assertEquals(Intent.LOCATION, IntentRouter.route("미끄럼틀은 어디 있어"));
    }

    @Test
    public void 시나리오2_4_퀴즈로_분기() {
        assertEquals(Intent.QUIZ, IntentRouter.route("퀴즈를 하고 싶어"));
    }

    @Test
    public void 시나리오2_6_공기질로_분기() {
        assertEquals(Intent.AIR_QUALITY, IntentRouter.route("지금 미세먼지가 어때"));
    }

    @Test
    public void 시나리오2_7_엔딩으로_분기() {
        assertEquals(Intent.ENDING, IntentRouter.route("테미야 고마워"));
    }

    @Test
    public void 시나리오2_5_자유질문은_AI로_폴백() {
        // FREE_QUESTION/CHAT 둘 다 MainActivity.handleUtterance 의 switch default(handleChat=AI)로 처리된다.
        // IntentRouter 개선으로 "왜/어떻게" 류는 FREE_QUESTION 으로 라우팅되며, 동작상 자유질문(AI)과 동일하다.
        Intent r = IntentRouter.route("바다는 왜 파란색이야");
        assertTrue("자유질문은 CHAT 또는 FREE_QUESTION 으로 라우팅돼 AI가 답한다",
                r == Intent.CHAT || r == Intent.FREE_QUESTION);
    }

    @Test
    public void STT_오인식_미끄럼들도_위치로_라우팅() {
        assertEquals(Intent.LOCATION, IntentRouter.route("미끄럼들 어디야"));
    }

    @Test
    public void 작별이_다른_의도보다_우선한다() {
        assertEquals(Intent.ENDING, IntentRouter.route("고마워 잘가"));
    }
}
