package com.kidsFriend.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.WindowManager;
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
import com.robotemi.sdk.NlpResult;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.listeners.OnNlpListener;

public class DemoActivity extends AppCompatActivity implements OnNlpListener {
    private static final int REQUEST_RECORD_AUDIO = 3001;

    private final TemiSpeechSpeaker temiSpeechSpeaker = new TemiSpeechSpeaker();
    private TemiRepository repository;
    private TextView statusText;
    private VoiceInputManager voiceInputManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_demo);
        repository = new TemiRepository(this);
        statusText = findViewById(R.id.text_demo_status);
        voiceInputManager = new VoiceInputManager(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 테미 NLP 인터셉터 등록: DemoActivity가 포그라운드일 때
        // 테미 기본 대화 UI가 우리 앱 위에 오버레이되는 것을 막음
        Robot.getInstance().addOnNlpListener(this);
        startWakeWordStandby();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Robot.getInstance().removeOnNlpListener(this);
        voiceInputManager.stopListening();
    }

    @Override
    protected void onDestroy() {
        voiceInputManager.destroy();
        super.onDestroy();
    }

    /**
     * 테미 NLP 결과 인터셉터.
     * DemoActivity가 포그라운드일 때 테미의 음성 인식 결과를 가로채
     * 기본 응답 UI를 차단한다. 실제 음성 처리는 VoiceInputManager가 담당.
     */
    @Override
    public void onNlpCompleted(NlpResult nlpResult) {
        // 의도적으로 비워둠 — 테미 기본 응답 차단
    }

    private void startWakeWordStandby() {
        if (!ensureAudioPermission()) {
            return;
        }
        if (!VoiceInputManager.isRecognitionAvailable(this)) {
            statusText.setText(R.string.voice_recognition_unavailable);
            return;
        }

        statusText.setText(R.string.voice_wake_standby);
        voiceInputManager.startContinuousListening(new VoiceInputManager.Callback() {
            @Override
            public void onReady() {
                statusText.setText(R.string.voice_wake_standby);
            }

            @Override
            public void onPartialResult(String text) {
                if (!TextUtils.isEmpty(text)) {
                    statusText.setText(getString(R.string.voice_heard_format, text));
                }
            }

            @Override
            public void onResult(String text) {
                if (!WakeWordMatcher.containsWakeWord(text)) {
                    statusText.setText(getString(R.string.voice_heard_format, text));
                    return;
                }

                voiceInputManager.stopListening();
                statusText.setText(R.string.voice_wake_detected);
                String textAfterWakeWord = WakeWordMatcher.textAfterWakeWord(text);
                if (!TextUtils.isEmpty(textAfterWakeWord)) {
                    handleVoiceQuestion(textAfterWakeWord);
                    return;
                }
                startQuestionListening();
            }

            @Override
            public void onError(String message) {
                statusText.setText(message);
                statusText.postDelayed(() -> startWakeWordStandby(), 2000);
            }
        });
    }

    private void startQuestionListening() {
        if (!ensureAudioPermission()) {
            return;
        }
        
        statusText.setText(R.string.voice_listening);
        voiceInputManager.startSingleListening(new VoiceInputManager.Callback() {
            @Override
            public void onReady() {
                statusText.setText(R.string.voice_listening);
            }

            @Override
            public void onPartialResult(String text) {
                if (!TextUtils.isEmpty(text)) {
                    statusText.setText(text);
                }
            }

            @Override
            public void onResult(String text) {
                handleVoiceQuestion(text);
            }

            @Override
            public void onError(String message) {
                statusText.setText(message);
                startWakeWordStandby();
            }
        });
    }

    private void handleVoiceQuestion(String rawText) {
        if (TextUtils.isEmpty(rawText)) {
            startWakeWordStandby();
            return;
        }

        String reconstructedText = QuestionReconstructor.reconstruct(rawText);
        statusText.setText(reconstructedText);
        
        repository.askVoiceQuestion(rawText, reconstructedText, new RepositoryCallback<QuestionResponse>() {
            @Override
            public void onSuccess(QuestionResponse data) {
                statusText.setText(data.answer);
                temiSpeechSpeaker.speak(data.answer);
                // After speaking, go back to wake word standby
                // Note: We might want to wait for TTS to finish, but for now we just wait a bit or restart
                statusText.postDelayed(() -> startWakeWordStandby(), 5000);
            }

            @Override
            public void onError(String message) {
                statusText.setText(message);
                statusText.postDelayed(() -> startWakeWordStandby(), 3000);
            }
        });
    }

    private boolean ensureAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }

        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO
        );
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startWakeWordStandby();
        }
    }
}
