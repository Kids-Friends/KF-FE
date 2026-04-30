package com.example.temiapplication.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.temiapplication.R;
import com.example.temiapplication.data.model.QuestionResponse;
import com.example.temiapplication.data.repository.RepositoryCallback;
import com.example.temiapplication.data.repository.TemiRepository;

public class QuestionActivity extends AppCompatActivity {
    private final TemiRepository repository = new TemiRepository();

    private EditText questionInput;
    private TextView answerText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_question);

        questionInput = findViewById(R.id.edit_question);
        answerText = findViewById(R.id.text_question_answer);
        Button askButton = findViewById(R.id.button_ask_question);
        Button backButton = findViewById(R.id.button_back);

        askButton.setOnClickListener(v -> askQuestion());
        backButton.setOnClickListener(v -> finish());
    }

    private void askQuestion() {
        String question = questionInput.getText().toString().trim();
        if (TextUtils.isEmpty(question)) {
            answerText.setText(R.string.question_empty_message);
            return;
        }

        answerText.setText(R.string.common_loading);
        repository.askQuestion(question, new RepositoryCallback<QuestionResponse>() {
            @Override
            public void onSuccess(QuestionResponse data) {
                answerText.setText(data.answer);
            }

            @Override
            public void onError(String message) {
                answerText.setText(message);
            }
        });
    }
}
