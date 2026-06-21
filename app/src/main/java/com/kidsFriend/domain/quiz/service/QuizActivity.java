package com.kidsFriend.domain.quiz.service;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.robotemi.sdk.Robot;

import com.robotemi.sdk.TtsRequest;

import com.kidsFriend.R;
import com.kidsFriend.domain.quiz.response.QuizAnswerResponse;
import com.kidsFriend.domain.sensor.service.SensorWebSocketClient;
import com.kidsFriend.global.repository.RepositoryCallback;
import com.kidsFriend.global.repository.TemiRepository;
import com.kidsFriend.global.ui.GlassBlur;
import com.kidsFriend.global.ui.KidAnimator;

import eightbitlab.com.blurview.BlurView;

public class QuizActivity extends AppCompatActivity {
    private static final String TAG = "QuizActivity";

    private TemiRepository repository;
    private TextView questionText;
    private Button answerOButton;
    private Button answerXButton;
    private BlurView correctLayout;
    private BlurView wrongLayout;
    private QuizQuestion currentQuiz;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        repository = new TemiRepository(this);

        questionText = findViewById(R.id.text_quiz_question);
        answerOButton = findViewById(R.id.button_answer_o);
        answerXButton = findViewById(R.id.button_answer_x);
        correctLayout = findViewById(R.id.layout_correct);
        wrongLayout = findViewById(R.id.layout_wrong);

        // 정답/오답 팝업: 뒤의 퀴즈 화면을 프로스트 처리(글래스 오버레이)
        android.view.ViewGroup rootQuiz = findViewById(R.id.root_quiz);
        GlassBlur.apply(this, correctLayout, rootQuiz, 20f, R.color.glass_tint_strong);
        GlassBlur.apply(this, wrongLayout, rootQuiz, 20f, R.color.glass_tint_strong);
        // 질문 카드: 뒤의 컬러 배경을 흐림
        GlassBlur.apply(this, findViewById(R.id.blur_quiz_question), rootQuiz, 18f, R.color.glass_tint_strong);
        Button backButton = findViewById(R.id.button_back);
        Button correctNextButton = findViewById(R.id.button_correct_next);
        Button correctStopButton = findViewById(R.id.button_correct_stop);
        Button wrongRetryButton = findViewById(R.id.button_wrong_retry);
        Button wrongNextButton = findViewById(R.id.button_wrong_next);
        Button wrongStopButton = findViewById(R.id.button_wrong_stop);

        KidAnimator.onClick(answerOButton, v -> submitAnswer("O"));
        KidAnimator.onClick(answerXButton, v -> submitAnswer("X"));
        backButton.setOnClickListener(v -> finish());
        // 정답/오답 모두 "다음 문제"로 계속 풀고, "그만할래"로만 종료한다(최대한 오래 트라이).
        correctNextButton.setOnClickListener(v -> loadQuiz());
        correctStopButton.setOnClickListener(v -> finish());
        wrongRetryButton.setOnClickListener(v -> hidePopups());
        wrongNextButton.setOnClickListener(v -> loadQuiz());
        wrongStopButton.setOnClickListener(v -> finish());

        loadQuiz();
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            ActivityInfo activityInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activityInfo = getPackageManager().getActivityInfo(getComponentName(),
                        PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA));
            } else {
                activityInfo = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
            }
            Robot.getInstance().onStart(activityInfo);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Temi activity metadata is not available.", e);
        }

        // 비전 카메라(KF_AD WonderMV)가 보낸 O/X 정답을 구독한다(터치와 동일 경로로 즉시 제출).
        SensorWebSocketClient.setQuizAnswerListener(this::onVisionAnswer);
    }

    @Override
    protected void onStop() {
        super.onStop();
        SensorWebSocketClient.setQuizAnswerListener(null);
    }

    /**
     * 손/팔 제스처를 비전이 O/X 로 인식해 보낸 정답.
     * 문제 풀이 중(정답/오답 팝업이 떠 있지 않을 때)만 받아 터치와 동일하게 제출한다.
     * (메인 스레드에서 호출됨)
     */
    private void onVisionAnswer(String answer) {
        if (isFinishing() || isDestroyed()) return;
        if (correctLayout.getVisibility() == View.VISIBLE
                || wrongLayout.getVisibility() == View.VISIBLE) {
            return; // 이미 채점 결과 표시 중 → 무시
        }
        submitAnswer(answer);
    }

    private void loadQuiz() {
        hidePopups();
        repository.getCurrentQuiz(new RepositoryCallback<QuizQuestion>() {
            @Override
            public void onSuccess(QuizQuestion data) {
                if (isFinishing() || isDestroyed()) return;
                currentQuiz = data;
                questionText.setText(data.question);
                Robot.getInstance().speak(TtsRequest.create(data.question, false));
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                questionText.setText(message);
            }
        });
    }

    private long lastSubmitTime = 0;

    private void submitAnswer(String selectedAnswer) {
        if (currentQuiz == null) return;
        if (System.currentTimeMillis() - lastSubmitTime < 1000) return;
        lastSubmitTime = System.currentTimeMillis();

        setAnswerEnabled(false);
        repository.submitQuizAnswer(currentQuiz.quizId, selectedAnswer, new RepositoryCallback<QuizAnswerResponse>() {
            @Override
            public void onSuccess(QuizAnswerResponse data) {
                if (isFinishing() || isDestroyed()) return;
                setAnswerEnabled(true);
                if (data.correct) {
                    correctLayout.setVisibility(View.VISIBLE);
                    View t = findViewById(R.id.text_correct_title);
                    t.post(() -> KidAnimator.success(t)); // 팝 + 초록 글로우 + 별 버스트
                } else {
                    wrongLayout.setVisibility(View.VISIBLE);
                    View t = findViewById(R.id.text_wrong_title);
                    t.post(() -> KidAnimator.error(t));   // 쉐이크 + 부드러운 빨강
                }
                if (data.message != null) {
                    String speechText = data.message.replaceAll("[^가-힣a-zA-Z0-9\\s.!?]", "").trim();
                    Robot.getInstance().speak(TtsRequest.create(speechText, false));
                }
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                setAnswerEnabled(true);
                questionText.setText(message);
            }
        });
    }

    private void hidePopups() {
        correctLayout.setVisibility(View.GONE);
        wrongLayout.setVisibility(View.GONE);
        setAnswerEnabled(true);
    }

    private void setAnswerEnabled(boolean enabled) {
        answerOButton.setEnabled(enabled);
        answerXButton.setEnabled(enabled);
    }
}