package com.kidsFriend.global.repository.mock;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.kidsFriend.domain.quiz.request.QuizAnswerRequest;
import com.kidsFriend.domain.quiz.response.QuizAnswerResponse;
import com.kidsFriend.domain.quiz.service.QuizQuestion;

public class MockDataSource {
    private static final String ANSWER_O = "O";
    private static final String ANSWER_X = "X";
    private static final Random RANDOM = new Random();

    // 시연 대본 고정 순서: demo-001(정답 케이스) → demo-002(오답 케이스) → 이후 기본 문제들(총 30종)
    private static final List<MockQuiz> QUIZZES = Arrays.asList(
            new MockQuiz("demo-001", "미끄럼틀 위에서 친구를 밀어도 될까요?", ANSWER_X, "친구를 밀면 다칠 수 있어요. 카운터에서 선물을 받을 수 있어!"),
            new MockQuiz("demo-002", "펭귄은 하늘을 날 수 있어요?", ANSWER_X, "펭귄은 날개가 있지만 하늘을 날지는 못해요."),
            new MockQuiz("quiz-001", "키즈카페에서는 뛰기보다 천천히 걸어야 해요.", ANSWER_O, "천천히 움직이면 친구와 부딪칠 위험을 줄일 수 있어요."),
            new MockQuiz("quiz-002", "미끄럼틀은 거꾸로 올라가도 괜찮아요.", ANSWER_X, "미끄럼틀은 정해진 방향으로만 이용해야 안전해요."),
            new MockQuiz("quiz-003", "놀고 난 뒤에는 손을 씻는 것이 좋아요.", ANSWER_O, "손 씻기는 가장 기본적인 위생 습관이에요."),
            new MockQuiz("quiz-004", "장난감은 친구에게 던져도 괜찮아요.", ANSWER_X, "장난감은 던지지 않고 안전하게 사용해야 해요."),
            new MockQuiz("quiz-005", "목이 마르면 직원이나 보호자에게 물을 요청해도 돼요.", ANSWER_O, "필요한 것이 있으면 주변 어른에게 말하면 돼요."),
            new MockQuiz("quiz-006", "모르는 사람을 따라가도 괜찮아요.", ANSWER_X, "모르는 사람을 따라가지 말고 보호자나 직원에게 알려야 해요."),
            new MockQuiz("quiz-007", "친구와 장난감을 사이좋게 나눠 쓰면 좋아요.", ANSWER_O, "나눠 쓰면 친구와 더 재밌게 놀 수 있어요."),
            new MockQuiz("quiz-008", "계단에서는 뛰어내려도 안전해요.", ANSWER_X, "계단은 한 칸씩 천천히 내려와야 해요."),
            new MockQuiz("quiz-009", "신발은 신발장에 정리해야 해요.", ANSWER_O, "신발을 정리하면 잃어버리지 않아요."),
            new MockQuiz("quiz-010", "다른 친구가 놀고 있는 장난감을 빼앗아도 돼요.", ANSWER_X, "차례를 기다리거나 같이 놀자고 말해야 해요."),
            new MockQuiz("quiz-011", "기침을 할 때는 입을 가려야 해요.", ANSWER_O, "입을 가리면 친구에게 감기를 옮기지 않아요."),
            new MockQuiz("quiz-012", "볼풀에서 공을 친구 얼굴에 던져도 돼요.", ANSWER_X, "공을 얼굴에 던지면 다칠 수 있어요."),
            new MockQuiz("quiz-013", "화장실에 다녀온 뒤에는 손을 씻어야 해요.", ANSWER_O, "손을 씻어야 깨끗하고 건강해요."),
            new MockQuiz("quiz-014", "음식은 정해진 자리에서만 먹어야 해요.", ANSWER_O, "놀이기구 위에서 먹으면 위험해요."),
            new MockQuiz("quiz-015", "넘어진 친구를 보면 도와줘야 해요.", ANSWER_O, "친구를 도와주면 모두 행복해요."),
            new MockQuiz("quiz-016", "젖은 바닥에서는 뛰어다녀도 괜찮아요.", ANSWER_X, "젖은 바닥은 미끄러져 다칠 수 있어요."),
            new MockQuiz("quiz-017", "트램폴린은 한 번에 여러 명이 뛰어도 안전해요.", ANSWER_X, "한 명씩 타야 서로 부딪치지 않아요."),
            new MockQuiz("quiz-018", "거북이는 등에 딱딱한 등껍질이 있어요.", ANSWER_O, "거북이는 등껍질로 몸을 보호해요."),
            new MockQuiz("quiz-019", "사자는 풀만 먹는 동물이에요.", ANSWER_X, "사자는 고기를 먹는 동물이에요."),
            new MockQuiz("quiz-020", "물을 자주 마시면 우리 몸에 좋아요.", ANSWER_O, "물을 자주 마시면 건강해져요."),
            new MockQuiz("quiz-021", "친구에게 고마울 때는 고맙다고 말해요.", ANSWER_O, "고마운 마음은 말로 표현하면 좋아요."),
            new MockQuiz("quiz-022", "불이 나면 엘리베이터를 타고 내려가야 해요.", ANSWER_X, "불이 나면 계단으로 대피해야 해요."),
            new MockQuiz("quiz-023", "낯선 사람이 따라오라고 하면 따라가야 해요.", ANSWER_X, "따라가지 말고 어른에게 알려야 해요."),
            new MockQuiz("quiz-024", "장난감을 가지고 논 뒤에는 제자리에 정리해요.", ANSWER_O, "정리하면 다음 친구도 쉽게 찾을 수 있어요."),
            new MockQuiz("quiz-025", "친구를 때리거나 꼬집으면 안 돼요.", ANSWER_O, "친구가 아파해요. 사이좋게 놀아요."),
            new MockQuiz("quiz-026", "꿀벌은 꽃에서 꿀을 모아요.", ANSWER_O, "꿀벌은 꽃을 옮겨 다니며 꿀을 모아요."),
            new MockQuiz("quiz-027", "실내에서 큰 소리로 떠들어도 괜찮아요.", ANSWER_X, "실내에서는 작은 목소리로 말해야 해요."),
            new MockQuiz("quiz-028", "양치질은 하루에 한 번만 하면 충분해요.", ANSWER_X, "양치질은 아침저녁으로 자주 하는 게 좋아요.")
    );

    // 시연 재현성을 위해 랜덤 대신 고정 순서로 한 문제씩 내보낸다.
    private static int demoIndex = 0;

    public QuizQuestion getCurrentQuiz() {
        MockQuiz quiz = QUIZZES.get(demoIndex % QUIZZES.size());
        demoIndex++;
        return quiz.toQuestion();
    }

    public QuizAnswerResponse submitQuizAnswer(QuizAnswerRequest request) {
        MockQuiz quiz = findQuiz(request.quizId);
        if (quiz == null) {
            return new QuizAnswerResponse(false, "음... 문제를 못 찾겠어. 다시 불러와줄래? 🤔");
        }

        boolean correct = quiz.correctAnswer.equals(request.selectedAnswer);
        String message = correct
                ? "정답! 정말 잘했어! " + quiz.explanation
                : "정말 아쉽다! 정답은 " + quiz.correctAnswer + "였어. " + quiz.explanation;
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