package com.kidsFriend.domain.chat.service;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.robotemi.sdk.Robot;

import com.kidsFriend.R;
import com.kidsFriend.domain.chat.response.QuestionResponse;
import com.kidsFriend.global.repository.RepositoryCallback;
import com.kidsFriend.global.repository.TemiRepository;
import com.kidsFriend.global.voice.TemiSpeechSpeaker;
import com.kidsFriend.global.voice.VoiceInputManager;
import com.kidsFriend.global.voice.WakeWordMatcher;

public class QuestionActivity extends AppCompatActivity {
    private static final String TAG = "QuestionActivity";
    public static final String EXTRA_START_VOICE_LISTENING = "start_voice_listening";
    public static final String EXTRA_INITIAL_VOICE_TEXT = "initial_voice_text";
    private static final int REQUEST_RECORD_AUDIO = 2001;

    private final TemiSpeechSpeaker temiSpeechSpeaker = new TemiSpeechSpeaker();
    private final Robot robot = Robot.getInstance();

    private TemiRepository repository;
    private EditText questionInput;
    private TextView voiceModeText;
    private TextView rawVoiceText;
    private TextView reconstructedVoiceText;
    private TextView answerText;
    private Button voiceButton;
    private LinearLayout subtitleLayout;
    private TextView subtitleText;
    private VoiceInputManager voiceInputManager;
    private boolean shouldStartVoiceAfterPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_question);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        repository = new TemiRepository(this);

        questionInput = findViewById(R.id.edit_question);
        voiceModeText = findViewById(R.id.text_voice_mode);
        rawVoiceText = findViewById(R.id.text_raw_voice);
        reconstructedVoiceText = findViewById(R.id.text_reconstructed_voice);
        answerText = findViewById(R.id.text_question_answer);
        Button askButton = findViewById(R.id.button_ask_question);
        voiceButton = findViewById(R.id.button_voice_listen);
        subtitleLayout = findViewById(R.id.layout_subtitle);
        subtitleText = findViewById(R.id.text_subtitle);
        Button backButton = findViewById(R.id.button_back);
        voiceInputManager = new VoiceInputManager(this);

        askButton.setOnClickListener(v -> askQuestion());
        voiceButton.setOnClickListener(v -> startWakeWordStandby());
        backButton.setOnClickListener(v -> finish());

        String initialVoiceText = getIntent().getStringExtra(EXTRA_INITIAL_VOICE_TEXT);
        if (!TextUtils.isEmpty(initialVoiceText)) {
            handleVoiceQuestion(initialVoiceText);
        } else if (getIntent().getBooleanExtra(EXTRA_START_VOICE_LISTENING, false)) {
            startQuestionListening();
        } else {
            startWakeWordStandby();
        }
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
                temiSpeechSpeaker.speak(data.answer);
            }

            @Override
            public void onError(String message) {
                answerText.setText(message);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        startWakeWordStandby();
    }

    @Override
    protected void onPause() {
        super.onPause();
        voiceInputManager.stopListening();
    }

    @Override
    protected void onDestroy() {
        voiceInputManager.destroy();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO) {
            return;
        }

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (shouldStartVoiceAfterPermission) {
                startQuestionListening();
            } else {
                startWakeWordStandby();
            }
        } else {
            voiceModeText.setText(R.string.voice_permission_denied);
        }
    }

    private void startWakeWordStandby() {
        shouldStartVoiceAfterPermission = false;
        if (!ensureAudioPermission()) {
            return;
        }
        if (!VoiceInputManager.isRecognitionAvailable(this)) {
            showVoiceRecognitionUnavailable();
            return;
        }

        voiceButton.setEnabled(true);
        voiceButton.setText(R.string.voice_button_waiting);
        voiceModeText.setText(R.string.voice_wake_standby);
        voiceInputManager.startContinuousListening(new VoiceInputManager.Callback() {
            @Override
            public void onReady() {
                voiceModeText.setText(R.string.voice_wake_standby);
            }

            @Override
            public void onPartialResult(String text) {
                if (!TextUtils.isEmpty(text)) {
                    voiceModeText.setText(getString(R.string.voice_heard_format, text));
                }
            }

            @Override
            public void onResult(String text) {
                if (!WakeWordMatcher.containsWakeWord(text)) {
                    voiceModeText.setText(getString(R.string.voice_heard_format, text));
                    return;
                }

                voiceInputManager.stopListening();
                voiceModeText.setText(R.string.voice_wake_detected);
                String textAfterWakeWord = WakeWordMatcher.textAfterWakeWord(text);
                if (!TextUtils.isEmpty(textAfterWakeWord)) {
                    handleVoiceQuestion(textAfterWakeWord);
                    return;
                }
                startQuestionListening();
            }

            @Override
            public void onError(String message) {
                voiceModeText.setText(message);
            }
        });
    }

    private void startQuestionListening() {
        shouldStartVoiceAfterPermission = true;
        if (!ensureAudioPermission()) {
            return;
        }
        if (!VoiceInputManager.isRecognitionAvailable(this)) {
            showVoiceRecognitionUnavailable();
            return;
        }

        voiceInputManager.stopListening();
        voiceButton.setEnabled(true);
        voiceButton.setText(R.string.voice_button_retry);
        voiceModeText.setText(R.string.voice_listening);
        rawVoiceText.setText(R.string.voice_raw_waiting);
        reconstructedVoiceText.setText(R.string.voice_reconstructed_waiting);

        voiceInputManager.startSingleListening(new VoiceInputManager.Callback() {
            @Override
            public void onReady() {
                voiceModeText.setText(R.string.voice_listening);
                subtitleLayout.setVisibility(View.VISIBLE);
                subtitleText.setText("");
            }

            @Override
            public void onPartialResult(String text) {
                if (!TextUtils.isEmpty(text)) {
                    rawVoiceText.setText(text);
                    subtitleText.setText(getString(R.string.voice_caption_format, text));
                }
            }

            @Override
            public void onResult(String text) {
                if (!TextUtils.isEmpty(text)) {
                    subtitleText.setText(getString(R.string.voice_caption_format, text));
                }
                handleVoiceQuestion(text);
            }

            @Override
            public void onError(String message) {
                voiceModeText.setText(message);
                subtitleLayout.setVisibility(View.GONE);
            }
        });
    }

    private boolean ensureAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }

        voiceModeText.setText(R.string.voice_permission_required);
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO
        );
        return false;
    }

    private void showVoiceRecognitionUnavailable() {
        voiceInputManager.stopListening();
        voiceModeText.setText(R.string.voice_recognition_unavailable);
        voiceButton.setEnabled(false);
        questionInput.setVisibility(View.VISIBLE);
        questionInput.requestFocus();
    }

    private void handleVoiceQuestion(String rawText) {
        if (TextUtils.isEmpty(rawText)) {
            voiceModeText.setText(R.string.question_empty_message);
            return;
        }

        String reconstructedText = QuestionReconstructor.reconstruct(rawText);
        rawVoiceText.setText(rawText);
        reconstructedVoiceText.setText(reconstructedText);
        questionInput.setText(reconstructedText);
        askVoiceQuestion(rawText, reconstructedText);
    }

    private void askVoiceQuestion(String rawText, String reconstructedText) {
        voiceModeText.setText(R.string.voice_sending);
        answerText.setText(R.string.common_loading);
        subtitleLayout.setVisibility(View.GONE);
        repository.askVoiceQuestion(rawText, reconstructedText, new RepositoryCallback<QuestionResponse>() {
            @Override
            public void onSuccess(QuestionResponse data) {
                voiceModeText.setText(R.string.voice_answer_received);
                answerText.setText(data.answer);
                temiSpeechSpeaker.speak(data.answer);
            }

            @Override
            public void onError(String message) {
                voiceModeText.setText(message);
                answerText.setText(message);
            }
        });
    }
}