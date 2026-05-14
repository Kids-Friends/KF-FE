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
            new MockQuiz("quiz-001", "키즈카페에서는 뛰기보다 천천히 걸어야 해요.", ANSWER_O, "안전하게 이동하면 친구와 부딪힐 위험이 줄어요."),
            new MockQuiz("quiz-002", "미끄럼틀은 거꾸로 올라가도 괜찮아요.", ANSWER_X, "미끄럼틀은 정해진 방향으로만 이용해야 안전해요."),
            new MockQuiz("quiz-003", "놀고 난 뒤에는 손을 씻는 것이 좋아요.", ANSWER_O, "손 씻기는 가장 기본적인 위생 습관이에요."),
            new MockQuiz("quiz-004", "장난감은 친구에게 던져도 괜찮아요.", ANSWER_X, "장난감은 던지지 않고 손으로 안전하게 사용해야 해요."),
            new MockQuiz("quiz-005", "목이 마르면 직원이나 보호자에게 물을 요청해도 돼요.", ANSWER_O, "필요한 것이 있으면 주변 어른에게 말하면 돼요."),
            new MockQuiz("quiz-006", "모르는 사람을 따라가도 괜찮아요.", ANSWER_X, "모르는 사람을 따라가지 말고 보호자나 직원에게 알려야 해요."),
            new MockQuiz("quiz-007", "다친 친구를 보면 직원에게 알려야 해요.", ANSWER_O, "다친 사람이 있으면 빨리 어른에게 알리는 것이 좋아요."),
            new MockQuiz("quiz-008", "음식은 놀이기구 안에서 먹어도 돼요.", ANSWER_X, "음식은 정해진 자리에서 먹어야 깨끗하고 안전해요."),
            new MockQuiz("quiz-009", "신발을 벗는 곳에서는 신발을 정리해 두는 것이 좋아요.", ANSWER_O, "신발을 정리하면 다른 사람이 넘어지는 일을 줄일 수 있어요."),
            new MockQuiz("quiz-010", "친구가 쓰는 장난감을 말없이 가져가도 돼요.", ANSWER_X, "친구에게 먼저 물어보고 함께 쓰는 것이 좋아요."),
            new MockQuiz("quiz-011", "화장실에 가고 싶으면 보호자나 직원에게 말해요.", ANSWER_O, "혼자 움직이기보다 주변 어른에게 말하면 더 안전해요."),
            new MockQuiz("quiz-012", "블록을 밟고 뛰어도 안전해요.", ANSWER_X, "블록을 밟고 뛰면 넘어질 수 있어요.")
    );
    private static final List<ZoneInfo> ZONES = Arrays.asList(
            new ZoneInfo("ENTRANCE", "입구", "입장과 퇴장 안내를 시작하는 구역입니다.", "신발장, 직원 데스크", "보통", "처음 방문한 보호자에게 전체 구조를 짧게 안내합니다."),
            new ZoneInfo("MAIN_ZONE", "메인 홀", "가장 넓은 중앙 이동 구역입니다.", "볼풀, 미끄럼틀, 카페존", "혼잡", "아이와 보호자가 흩어졌을 때 기준 위치로 안내합니다."),
            new ZoneInfo("BALL_POOL", "볼풀존", "공놀이를 할 수 있는 인기 놀이 구역입니다.", "메인 홀, 미끄럼틀존", "혼잡", "뛰지 말고 천천히 이동하도록 안내합니다."),
            new ZoneInfo("SLIDE_ZONE", "미끄럼틀존", "미끄럼틀과 오르기 놀이가 있는 구역입니다.", "볼풀존, 트램펄린존", "혼잡", "거꾸로 올라가지 않도록 안전 문구를 함께 말합니다."),
            new ZoneInfo("TRAMPOLINE_ZONE", "트램펄린존", "점프 놀이를 할 수 있는 활동 구역입니다.", "미끄럼틀존, 안전 매트", "혼잡", "한 명씩 이용하도록 안내합니다."),
            new ZoneInfo("BLOCK_ZONE", "블록존", "블록 조립과 앉아서 노는 조용한 구역입니다.", "역할놀이존, 보호자 좌석", "여유", "어린 아이에게 쉬운 놀이 구역으로 추천합니다."),
            new ZoneInfo("ROLE_PLAY_ZONE", "역할놀이존", "주방놀이, 병원놀이 같은 역할놀이 구역입니다.", "블록존, 포토존", "보통", "장난감은 제자리에 두도록 안내합니다."),
            new ZoneInfo("TODDLER_ZONE", "영유아존", "어린 아이가 안전하게 놀 수 있는 낮은 난이도 구역입니다.", "수유실, 보호자 좌석", "보통", "큰 아이가 뛰어들지 않도록 주의 문구를 말합니다."),
            new ZoneInfo("RESTROOM", "화장실", "아이와 보호자가 함께 이용하는 화장실 위치입니다.", "세면대, 입구", "여유", "화장실 질문이 들어오면 최우선으로 안내합니다."),
            new ZoneInfo("WASH_ZONE", "세면대", "손 씻기와 정리를 하는 위생 구역입니다.", "화장실, 입구", "보통", "놀이 후 손 씻기 안내와 연결합니다."),
            new ZoneInfo("CAFE_ZONE", "보호자 카페존", "보호자가 대기하고 음료를 마시는 구역입니다.", "메인 홀, 직원 데스크", "보통", "아이 위치를 찾는 보호자를 메인 홀 쪽으로 안내합니다."),
            new ZoneInfo("STAFF_DESK", "직원 데스크", "직원 호출, 분실물, 결제 문의를 처리하는 구역입니다.", "입구, 카페존", "보통", "도움 요청이나 분실물 질문이 들어오면 안내합니다."),
            new ZoneInfo("SHOES_ZONE", "신발장", "신발을 벗고 정리하는 구역입니다.", "입구, 세면대", "여유", "퇴장 전 신발 정리를 안내합니다."),
            new ZoneInfo("PHOTO_ZONE", "포토존", "사진 촬영 이벤트를 진행할 수 있는 구역입니다.", "역할놀이존, 메인 홀", "여유", "사진 촬영 기능과 연결할 수 있는 Mock 구역입니다."),
            new ZoneInfo("RECYCLING_ZONE", "분리수거존", "컵, 종이, 플라스틱을 버리는 정리 구역입니다.", "카페존, 출구", "여유", "분리수거 안내 기능과 연결할 수 있습니다."),
            new ZoneInfo("PARTY_ROOM", "파티룸", "생일파티와 단체 예약을 위한 별도 공간입니다.", "카페존, 직원 데스크", "예약", "예약 손님에게만 안내하도록 표시합니다."),
            new ZoneInfo("NURSING_ROOM", "수유실", "영유아 보호자를 위한 조용한 공간입니다.", "영유아존, 화장실", "여유", "보호자 질문에서 우선 안내합니다."),
            new ZoneInfo("FIRST_AID_ZONE", "응급처치존", "간단한 응급처치와 직원 대응을 위한 구역입니다.", "직원 데스크, 메인 홀", "대기", "다침 관련 호출은 이 구역과 연결합니다."),
            new ZoneInfo("EXIT_ZONE", "출구", "퇴장과 보호자 확인을 하는 구역입니다.", "신발장, 직원 데스크", "보통", "길 안내 종료 지점으로 사용할 수 있습니다.")
    );

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
        MockQuiz quiz = QUIZZES.get(RANDOM.nextInt(QUIZZES.size()));
        return quiz.toQuestion();
    }

    public QuizAnswerResponse submitQuizAnswer(QuizAnswerRequest request) {
        MockQuiz quiz = findQuiz(request.quizId);
        if (quiz == null) {
            return new QuizAnswerResponse(false, "문제를 다시 불러와 주세요.");
        }

        boolean correct = quiz.correctAnswer.equals(request.selectedAnswer);
        String message = correct
                ? "정답이에요! " + quiz.explanation
                : "아쉬워요. 정답은 " + quiz.correctAnswer + "입니다. " + quiz.explanation;
        return new QuizAnswerResponse(correct, message);
    }

    private MockQuiz findQuiz(String quizId) {
        for (MockQuiz quiz : QUIZZES) {
            if (quiz.quizId.equals(quizId)) {
                return quiz;
            }
        }
        return null;
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

    public List<ZoneInfo> getZones() {
        return ZONES;
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
            return new QuizQuestion(
                    quizId,
                    question,
                    Arrays.asList(ANSWER_O, ANSWER_X),
                    correctAnswer
            );
        }
    }
}
