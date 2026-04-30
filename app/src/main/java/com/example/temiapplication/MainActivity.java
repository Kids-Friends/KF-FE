package com.example.temiapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.temiapplication.ui.CallActivity;
import com.example.temiapplication.ui.QuestionActivity;
import com.example.temiapplication.ui.QuizActivity;
import com.example.temiapplication.ui.StatisticsActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button callButton = findViewById(R.id.button_call);
        Button questionButton = findViewById(R.id.button_question);
        Button quizButton = findViewById(R.id.button_quiz);
        Button statisticsButton = findViewById(R.id.button_statistics);

        callButton.setOnClickListener(v -> openScreen(CallActivity.class));
        questionButton.setOnClickListener(v -> openScreen(QuestionActivity.class));
        quizButton.setOnClickListener(v -> openScreen(QuizActivity.class));
        statisticsButton.setOnClickListener(v -> openScreen(StatisticsActivity.class));
    }

    private void openScreen(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
    }
}
