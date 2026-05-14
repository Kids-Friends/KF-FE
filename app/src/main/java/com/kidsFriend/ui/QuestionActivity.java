package com.kidsFriend.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.kidsFriend.R;
import com.kidsFriend.data.model.QuestionResponse;
import com.kidsFriend.data.repository.RepositoryCallback;
import com.kidsFriend.data.repository.TemiRepository;
import com.kidsFriend.voice.QuestionReconstructor;
import com.kidsFriend.voice.TemiSpeechSpeaker;
import com.kidsFriend.voice.VoiceInputManager;
import com.kidsFriend.voice.WakeWordMatcher;

public class QuestionActivity extends AppCompatActivity {
    public static final String EXTRA_START_VOICE_LISTENING = "start_voice_listening";
    public static final String EXTRA_INITIAL_VOICE_TEXT = "initial_voice_text";
    private static final int REQUEST_RECORD_AUDIO = 2001;

    private final TemiSpeechSpeaker temiSpeechSpeaker = new TemiSpeechSpeaker();

    private TemiRepository repository;
    private EditText questionInput;
    private TextView voiceModeText;
    private TextView rawVoiceText;
    private TextView reconstructedVoiceText;
    private TextView answerText;
    private Button voiceButton;
    private VoiceInputManager voiceInputManager;
    private boolean shouldStartVoiceAfterPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_question);
        repository = new TemiRepository(this);

        questionInput = findViewById(R.id.edit_question);
        voiceModeText = findViewById(R.id.text_voice_mode);
        rawVoiceText = findViewById(R.id.text_raw_voice);
        reconstructedVoiceText = findViewById(R.id.text_reconstructed_voice);
        answerText = findViewById(R.id.text_question_answer);
        Button askButton = findViewById(R.id.button_ask_question);
        voiceButton = findViewById(R.id.button_voice_listen);
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
            voiceModeText.setText(R.string.voice_recognition_unavailable);
            return;
        }

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
            voiceModeText.setText(R.string.voice_recognition_unavailable);
            return;
        }

        voiceInputManager.stopListening();
        voiceButton.setText(R.string.voice_button_retry);
        voiceModeText.setText(R.string.voice_listening);
        rawVoiceText.setText(R.string.voice_raw_waiting);
        reconstructedVoiceText.setText(R.string.voice_reconstructed_waiting);

        voiceInputManager.startSingleListening(new VoiceInputManager.Callback() {
            @Override
            public void onReady() {
                voiceModeText.setText(R.string.voice_listening);
            }

            @Override
            public void onPartialResult(String text) {
                if (!TextUtils.isEmpty(text)) {
                    rawVoiceText.setText(text);
                }
            }

            @Override
            public void onResult(String text) {
                handleVoiceQuestion(text);
            }

            @Override
            public void onError(String message) {
                voiceModeText.setText(message);
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
