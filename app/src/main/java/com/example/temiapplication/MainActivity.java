package com.example.temiapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.temiapplication.ui.CallActivity;
import com.example.temiapplication.ui.QuestionActivity;
import com.example.temiapplication.ui.QuizActivity;
import com.example.temiapplication.ui.StatisticsActivity;
import com.example.temiapplication.voice.VoiceInputManager;
import com.example.temiapplication.voice.WakeWordMatcher;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_RECORD_AUDIO = 1001;

    private VoiceInputManager wakeVoiceInputManager;
    private TextView wakeStatusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button callButton = findViewById(R.id.button_call);
        Button questionButton = findViewById(R.id.button_question);
        Button quizButton = findViewById(R.id.button_quiz);
        Button statisticsButton = findViewById(R.id.button_statistics);
        Button approachButton = findViewById(R.id.button_approach);
        Button locationGuideButton = findViewById(R.id.button_location_guide);
        Button ttsButton = findViewById(R.id.button_tts);
        Button rewardButton = findViewById(R.id.button_reward);
        Button photoButton = findViewById(R.id.button_photo);
        Button recyclingButton = findViewById(R.id.button_recycling);
        wakeStatusText = findViewById(R.id.text_wake_status);
        Button wakeButton = findViewById(R.id.button_start_wake_listening);

        wakeVoiceInputManager = new VoiceInputManager(this);

        callButton.setOnClickListener(v -> openScreen(CallActivity.class));
        questionButton.setOnClickListener(v -> openScreen(QuestionActivity.class));
        quizButton.setOnClickListener(v -> openScreen(QuizActivity.class));
        statisticsButton.setOnClickListener(v -> openScreen(StatisticsActivity.class));
        approachButton.setOnClickListener(v -> showPendingFeature());
        locationGuideButton.setOnClickListener(v -> showPendingFeature());
        ttsButton.setOnClickListener(v -> showPendingFeature());
        rewardButton.setOnClickListener(v -> showPendingFeature());
        photoButton.setOnClickListener(v -> showPendingFeature());
        recyclingButton.setOnClickListener(v -> showPendingFeature());
        wakeButton.setOnClickListener(v -> startWakeWordStandby());

        ensureAudioPermission();
    }

    private void openScreen(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
    }

    private void showPendingFeature() {
        Toast.makeText(this, R.string.feature_pending_message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasAudioPermission()) {
            startWakeWordStandby();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        wakeVoiceInputManager.stopListening();
    }

    @Override
    protected void onDestroy() {
        wakeVoiceInputManager.destroy();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO) {
            return;
        }

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startWakeWordStandby();
        } else {
            wakeStatusText.setText(R.string.voice_permission_denied);
        }
    }

    private void ensureAudioPermission() {
        if (hasAudioPermission()) {
            return;
        }
        wakeStatusText.setText(R.string.voice_permission_required);
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO
        );
    }

    private boolean hasAudioPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startWakeWordStandby() {
        if (!hasAudioPermission()) {
            ensureAudioPermission();
            return;
        }
        if (!VoiceInputManager.isRecognitionAvailable(this)) {
            wakeStatusText.setText(R.string.voice_recognition_unavailable);
            return;
        }

        wakeStatusText.setText(R.string.voice_wake_standby);
        wakeVoiceInputManager.startContinuousListening(new VoiceInputManager.Callback() {
            @Override
            public void onReady() {
                wakeStatusText.setText(R.string.voice_wake_standby);
            }

            @Override
            public void onPartialResult(String text) {
                if (text == null || text.trim().isEmpty()) {
                    return;
                }
                wakeStatusText.setText(getString(R.string.voice_heard_format, text));
            }

            @Override
            public void onResult(String text) {
                if (!WakeWordMatcher.containsWakeWord(text)) {
                    wakeStatusText.setText(getString(R.string.voice_heard_format, text));
                    return;
                }
                wakeVoiceInputManager.stopListening();
                wakeStatusText.setText(R.string.voice_wake_detected);
                Intent intent = new Intent(MainActivity.this, QuestionActivity.class);
                intent.putExtra(QuestionActivity.EXTRA_START_VOICE_LISTENING, true);
                intent.putExtra(QuestionActivity.EXTRA_INITIAL_VOICE_TEXT, WakeWordMatcher.textAfterWakeWord(text));
                startActivity(intent);
            }

            @Override
            public void onError(String message) {
                wakeStatusText.setText(message);
            }
        });
    }
}
