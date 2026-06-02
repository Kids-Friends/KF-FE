package com.kidsFriend.ui;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.kidsFriend.R;
import com.kidsFriend.data.model.StatisticsSummary;
import com.kidsFriend.data.repository.RepositoryCallback;
import com.kidsFriend.data.repository.TemiRepository;
import com.robotemi.sdk.Robot;

import java.util.Locale;

public class StatisticsActivity extends AppCompatActivity {
    private static final String TAG = "StatisticsActivity";
    private TemiRepository repository;
    private TextView callCountText;
    private TextView questionCountText;
    private TextView quizPlayCountText;
    private TextView quizCorrectCountText;
    private TextView summaryText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);
        repository = new TemiRepository(this);

        callCountText = findViewById(R.id.text_stat_call_count);
        questionCountText = findViewById(R.id.text_stat_question_count);
        quizPlayCountText = findViewById(R.id.text_stat_quiz_play_count);
        quizCorrectCountText = findViewById(R.id.text_stat_quiz_correct_count);
        summaryText = findViewById(R.id.text_stat_summary);
        Button refreshButton = findViewById(R.id.button_refresh_statistics);
        Button backButton = findViewById(R.id.button_back);

        refreshButton.setOnClickListener(v -> loadStatistics());
        backButton.setOnClickListener(v -> finish());

        loadStatistics();
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            ActivityInfo activityInfo = getPackageManager()
                    .getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
            Robot.getInstance().onStart(activityInfo);
            Log.d(TAG, "Temi onStart: Sovereignty declared.");
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Temi activity metadata is not available.", e);
        }
    }
    private void loadStatistics() {
        summaryText.setText(R.string.common_loading);
        repository.getStatisticsSummary(new RepositoryCallback<StatisticsSummary>() {
            @Override
            public void onSuccess(StatisticsSummary data) {
                callCountText.setText(formatCount(data.callCount));
                questionCountText.setText(formatCount(data.questionCount));
                quizPlayCountText.setText(formatCount(data.quizPlayCount));
                quizCorrectCountText.setText(formatCount(data.quizCorrectCount));
                summaryText.setText(data.summaryMessage);
            }

            @Override
            public void onError(String message) {
                summaryText.setText(message);
            }
        });
    }

    private String formatCount(int count) {
        return String.format(Locale.KOREA, "%d건", count);
    }
}
