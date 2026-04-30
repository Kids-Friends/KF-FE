package com.example.temiapplication.data.mock;

import com.example.temiapplication.data.model.CallRequest;
import com.example.temiapplication.data.model.CallResponse;
import com.example.temiapplication.data.model.QuestionRequest;
import com.example.temiapplication.data.model.QuestionResponse;
import com.example.temiapplication.data.model.QuizAnswerRequest;
import com.example.temiapplication.data.model.QuizAnswerResponse;
import com.example.temiapplication.data.model.QuizQuestion;
import com.example.temiapplication.data.model.StatisticsSummary;
import com.example.temiapplication.data.model.VoiceQuestionRequest;

import java.util.Arrays;

public class MockDataSource {
    private static final String QUIZ_ID = "quiz-001";
    private static final String CORRECT_ANSWER = "손을 깨끗이 씻어요";

    public CallResponse createCall(CallRequest request) {
        return new CallResponse(
                true,
                "CALL-MOCK-001",
                request.reason + " 사유로 직원 호출이 접수되었습니다."
        );
    }

    public QuestionResponse askQuestion(QuestionRequest request) {
        return new QuestionResponse(
                true,
                "지금은 mock 응답입니다. Spring Boot API 연결 후 실제 AI 답변으로 교체됩니다.\n\n질문: " + request.question
        );
    }

    public QuestionResponse askVoiceQuestion(VoiceQuestionRequest request) {
        return new QuestionResponse(
                true,
                "음성 질문 mock 응답입니다. 재구성된 질문 기준으로 답변합니다.\n\n원문: "
                        + request.rawText
                        + "\n재구성: "
                        + request.reconstructedText
        );
    }

    public QuizQuestion getCurrentQuiz() {
        return new QuizQuestion(
                QUIZ_ID,
                "키즈카페에서 놀고 난 뒤 가장 먼저 해야 할 일은 무엇일까요?",
                Arrays.asList("뛰어다녀요", "손을 깨끗이 씻어요", "장난감을 던져요"),
                CORRECT_ANSWER
        );
    }

    public QuizAnswerResponse submitQuizAnswer(QuizAnswerRequest request) {
        boolean correct = CORRECT_ANSWER.equals(request.selectedAnswer);
        String message = correct
                ? "정답입니다. 손 씻기는 가장 기본적인 안전 습관이에요."
                : "아쉬워요. 정답은 '" + CORRECT_ANSWER + "'입니다.";
        return new QuizAnswerResponse(correct, message);
    }

    public StatisticsSummary getStatisticsSummary() {
        return new StatisticsSummary(
                12,
                27,
                18,
                14,
                "오늘은 질문 사용량이 가장 많습니다."
        );
    }
}
