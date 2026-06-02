package com.kidsFriend.data.mock;

import com.kidsFriend.data.model.CallRequest;
import com.kidsFriend.data.model.CallResponse;
import com.kidsFriend.data.model.QuestionRequest;
import com.kidsFriend.data.model.QuestionResponse;
import com.kidsFriend.data.model.QuizAnswerRequest;
import com.kidsFriend.data.model.QuizAnswerResponse;
import com.kidsFriend.data.model.QuizQuestion;
import com.kidsFriend.data.model.StatisticsSummary;
import com.kidsFriend.data.model.VoiceQuestionRequest;
import com.kidsFriend.data.model.ZoneInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class MockDataSource {
    private static final String ANSWER_O = "O";
    private static final String ANSWER_X = "X";
    private static final Random RANDOM = new Random();

    private static final List<MockQuiz> QUIZZES = Arrays.asList(
            new MockQuiz("quiz-001", "키즈카페에서는 뛰기보다 천천히 걸어야 해요.", ANSWER_O, "천천히 움직이면 친구와 부딪칠 위험을 줄일 수 있어요."),
            new MockQuiz("quiz-002", "미끄럼틀은 거꾸로 올라가도 괜찮아요.", ANSWER_X, "미끄럼틀은 정해진 방향으로만 이용해야 안전해요."),
            new MockQuiz("quiz-003", "놀고 난 뒤에는 손을 씻는 것이 좋아요.", ANSWER_O, "손 씻기는 가장 기본적인 위생 습관이에요."),
            new MockQuiz("quiz-004", "장난감은 친구에게 던져도 괜찮아요.", ANSWER_X, "장난감은 던지지 않고 안전하게 사용해야 해요."),
            new MockQuiz("quiz-005", "목이 마르면 직원이나 보호자에게 물을 요청해도 돼요.", ANSWER_O, "필요한 것이 있으면 주변 어른에게 말하면 돼요."),
            new MockQuiz("quiz-006", "모르는 사람을 따라가도 괜찮아요.", ANSWER_X, "모르는 사람을 따라가지 말고 보호자나 직원에게 알려야 해요.")
    );

    private static final List<ZoneInfo> ZONES = Arrays.asList(
            new ZoneInfo("ENTRANCE", "입구", "입장과 퇴장 안내를 시작하는 구역입니다.", "신발장, 직원 데스크", "보통", "처음 방문한 보호자에게 전체 구조를 짧게 안내합니다."),
            new ZoneInfo("MAIN_ZONE", "메인 존", "가장 넓은 중앙 놀이 구역입니다.", "볼풀, 미끄럼틀, 카페 존", "혼잡", "아이와 보호자가 합류하기 쉬운 위치로 안내합니다."),
            new ZoneInfo("BALL_POOL", "볼풀 존", "공놀이를 할 수 있는 인기 구역입니다.", "메인 존, 미끄럼틀 존", "혼잡", "뛰지 말고 천천히 이동하도록 안내합니다."),
            new ZoneInfo("SLIDE_ZONE", "미끄럼틀 존", "미끄럼틀과 오르기 구조물이 있는 구역입니다.", "볼풀 존, 트램폴린 존", "혼잡", "거꾸로 올라가지 않도록 안전 문구를 말합니다."),
            new ZoneInfo("RESTROOM", "화장실", "아이와 보호자가 함께 이용하는 화장실 위치입니다.", "세면대, 입구", "여유", "화장실 질문이 들어오면 최우선으로 안내합니다."),
            new ZoneInfo("PHOTO_ZONE", "포토 존", "사진 촬영 이벤트를 진행할 수 있는 구역입니다.", "역할놀이 존, 메인 존", "여유", "사진 촬영 기능과 연결되는 Mock 구역입니다."),
            new ZoneInfo("STAFF_DESK", "직원 데스크", "직원 호출과 결제 문의를 처리하는 구역입니다.", "입구, 카페 존", "보통", "긴급 요청이나 분실물 질문이 들어오면 안내합니다.")
    );

    public CallResponse createCall(CallRequest request) {
        CallResponse response = new CallResponse();
        response.callsId = "CALL-MOCK-001";
        response.robotId = request.robotId;
        response.clientId = request.clientId;
        response.reason = request.reason;
        response.status = "WAITING";
        return response;
    }

    public QuestionResponse askQuestion(QuestionRequest request) {
        return new QuestionResponse(true, "지금은 mock 답변입니다. 실제 환경에서는 Spring Boot AI API 응답으로 교체됩니다.\n\n질문: " + request.question);
    }

    public QuestionResponse askVoiceQuestion(VoiceQuestionRequest request) {
        return new QuestionResponse(
                true,
                "음성 질문 mock 답변입니다.\n\n원문: " + request.rawText + "\n정리: " + request.reconstructedText
        );
    }

    public QuizQuestion getCurrentQuiz() {
        MockQuiz quiz = QUIZZES.get(RANDOM.nextInt(QUIZZES.size()));
        return quiz.toQuestion();
    }

    public QuizAnswerResponse submitQuizAnswer(QuizAnswerRequest request) {
        MockQuiz quiz = findQuiz(request.quizId);
        if (quiz == null) {
            return new QuizAnswerResponse(false, "음... 문제를 못 찾겠어. 다시 불러와줄래? 🤔");
        }

        boolean correct = quiz.correctAnswer.equals(request.selectedAnswer);
        String message = correct
                ? "와우! 정답이에요! 🎉 " + quiz.explanation
                : "아쉬워요. 정답은 " + quiz.correctAnswer + "였어요. " + quiz.explanation + " 🤔";
        return new QuizAnswerResponse(correct, message);
    }

    public StatisticsSummary getStatisticsSummary() {
        return new StatisticsSummary(12, 27, 18, 14, "현재 통계 API가 없어 앱 내부 테스트 데이터를 표시합니다.");
    }

    public List<ZoneInfo> getZones() {
        return ZONES;
    }

    private MockQuiz findQuiz(String quizId) {
        for (MockQuiz quiz : QUIZZES) {
            if (quiz.quizId.equals(quizId)) {
                return quiz;
            }
        }
        return null;
    }

    private static class MockQuiz {
        private final String quizId;
        private final String question;
        private final String correctAnswer;
        private final String explanation;

        private MockQuiz(String quizId, String question, String correctAnswer, String explanation) {
            this.quizId = quizId;
            this.question = question;
            this.correctAnswer = correctAnswer;
            this.explanation = explanation;
        }

        private QuizQuestion toQuestion() {
            return new QuizQuestion(quizId, question, Arrays.asList(ANSWER_O, ANSWER_X), correctAnswer);
        }
    }
}
