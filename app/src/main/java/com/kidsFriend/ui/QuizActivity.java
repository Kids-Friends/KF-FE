package com.kidsFriend.ui;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.kidsFriend.R;
import com.kidsFriend.data.model.QuizAnswerResponse;
import com.kidsFriend.data.model.QuizQuestion;
import com.kidsFriend.data.repository.RepositoryCallback;
import com.kidsFriend.data.repository.TemiRepository;
import com.kidsFriend.voice.TemiSpeechSpeaker;
import com.robotemi.sdk.Robot;

public class QuizActivity extends AppCompatActivity {
    private static final String TAG = "QuizActivity";

    private TemiRepository repository;
    private TemiSpeechSpeaker speaker;
    private TextView questionText;
    private Button answerOButton;
    private Button answerXButton;
    private View correctLayout;
    private View wrongLayout;
    private QuizQuestion currentQuiz;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);
        repository = new TemiRepository(this);
        speaker = new TemiSpeechSpeaker();

        questionText = findViewById(R.id.text_quiz_question);
        answerOButton = findViewById(R.id.button_answer_o);
        answerXButton = findViewById(R.id.button_answer_x);
        correctLayout = findViewById(R.id.layout_correct);
        wrongLayout = findViewById(R.id.layout_wrong);
        Button backButton = findViewById(R.id.button_back);
        Button correctHomeButton = findViewById(R.id.button_correct_home);
        Button wrongRetryButton = findViewById(R.id.button_wrong_retry);
        Button wrongNextButton = findViewById(R.id.button_wrong_next);

        answerOButton.setOnClickListener(v -> submitAnswer("O"));
        answerXButton.setOnClickListener(v -> submitAnswer("X"));
        backButton.setOnClickListener(v -> finish());
        correctHomeButton.setOnClickListener(v -> finish());
        wrongRetryButton.setOnClickListener(v -> hidePopups());
        wrongNextButton.setOnClickListener(v -> loadQuiz());

        loadQuiz();
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            ActivityInfo activityInfo = getPackageManager()
                    .getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
            Robot.getInstance().onStart(activityInfo);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Temi activity metadata is not available.", e);
        }
    }

    private void loadQuiz() {
        hidePopups();
        repository.getCurrentQuiz(new RepositoryCallback<QuizQuestion>() {
            @Override
            public void onSuccess(QuizQuestion data) {
                currentQuiz = data;
                questionText.setText(data.question);
                speaker.speak(data.question);
            }

            @Override
            public void onError(String message) {
                questionText.setText(message);
            }
        });
    }

    private void submitAnswer(String selectedAnswer) {
        if (currentQuiz == null) {
            return;
        }
        setAnswerEnabled(false);
        repository.submitQuizAnswer(currentQuiz.quizId, selectedAnswer, new RepositoryCallback<QuizAnswerResponse>() {
            @Override
            public void onSuccess(QuizAnswerResponse data) {
                setAnswerEnabled(true);
                if (data.correct) {
                    correctLayout.setVisibility(View.VISIBLE);
                } else {
                    wrongLayout.setVisibility(View.VISIBLE);
                }
                if (data.message != null) {
                    String speechText = data.message.replaceAll("[^가-힣a-zA-Z0-9\\s.!?]", "").trim();
                    speaker.speak(speechText);
                }
            }

            @Override
            public void onError(String message) {
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
