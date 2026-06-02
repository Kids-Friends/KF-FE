package com.kidsFriend.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.kidsFriend.R;
import com.kidsFriend.data.model.QuizAnswerResponse;
import com.kidsFriend.data.model.QuizQuestion;
import com.kidsFriend.data.repository.RepositoryCallback;
import com.kidsFriend.data.repository.TemiRepository;

import java.util.ArrayList;
import java.util.List;

public class QuizActivity extends AppCompatActivity {
    private final List<Button> optionButtons = new ArrayList<>();

    private TemiRepository repository;
    private TextView questionText;
    private TextView resultText;
    private QuizQuestion currentQuiz;
    private String selectedAnswer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);
        repository = new TemiRepository(this);

        questionText = findViewById(R.id.text_quiz_question);
        resultText = findViewById(R.id.text_quiz_result);
        Button optionOneButton = findViewById(R.id.button_quiz_option_one);
        Button optionTwoButton = findViewById(R.id.button_quiz_option_two);
        Button optionThreeButton = findViewById(R.id.button_quiz_option_three);
        Button submitButton = findViewById(R.id.button_submit_quiz);
        Button reloadButton = findViewById(R.id.button_reload_quiz);
        Button backButton = findViewById(R.id.button_back);

        optionButtons.add(optionOneButton);
        optionButtons.add(optionTwoButton);
        optionButtons.add(optionThreeButton);

        submitButton.setOnClickListener(v -> submitAnswer());
        reloadButton.setOnClickListener(v -> loadQuiz());
        backButton.setOnClickListener(v -> finish());

        loadQuiz();
    }

    private void loadQuiz() {
        selectedAnswer = null;
        resultText.setText(R.string.common_loading);
        repository.getCurrentQuiz(new RepositoryCallback<QuizQuestion>() {
            @Override
            public void onSuccess(QuizQuestion data) {
                currentQuiz = data;
                bindQuiz(data);
                resultText.setText(R.string.quiz_select_answer_message);
            }

            @Override
            public void onError(String message) {
                resultText.setText(message);
            }
        });
    }

    private void bindQuiz(QuizQuestion quiz) {
        questionText.setText(quiz.question);
        for (int i = 0; i < optionButtons.size(); i++) {
            Button button = optionButtons.get(i);
            if (i < quiz.options.size()) {
                String option = quiz.options.get(i);
                button.setVisibility(View.VISIBLE);
                button.setText(option);
                button.setEnabled(true);
                button.setSelected(false);
                button.setOnClickListener(v -> selectAnswer((Button) v));
            } else {
                button.setText("");
                button.setEnabled(false);
                button.setSelected(false);
                button.setVisibility(View.GONE);
                button.setOnClickListener(null);
            }
        }
    }

    private void selectAnswer(Button selectedButton) {
        selectedAnswer = selectedButton.getText().toString();
        for (Button button : optionButtons) {
            button.setSelected(false);
        }
        selectedButton.setSelected(true);
        resultText.setText(selectedAnswer + " 선택됨");
    }

    private void submitAnswer() {
        if (currentQuiz == null) {
            resultText.setText(R.string.quiz_load_failed_message);
            return;
        }

        if (selectedAnswer == null) {
            resultText.setText(R.string.quiz_select_answer_message);
            return;
        }

        resultText.setText(R.string.common_loading);
        repository.submitQuizAnswer(currentQuiz.quizId, selectedAnswer, new RepositoryCallback<QuizAnswerResponse>() {
            @Override
            public void onSuccess(QuizAnswerResponse data) {
                resultText.setText(data.message);
            }

            @Override
            public void onError(String message) {
                resultText.setText(message);
            }
        });
    }
}
