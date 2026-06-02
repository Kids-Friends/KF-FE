package com.kidsFriend.ui;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
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
import com.robotemi.sdk.Robot;

public class DemoActivity extends AppCompatActivity {
    private static final int REQUEST_RECORD_AUDIO = 3001;
    private static final String TAG = "DemoActivity";

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
                statusText.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startWakeWordStandby();
                    }
                }, 2000);
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
                statusText.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startWakeWordStandby();
                    }
                }, 5000);
            }

            @Override
            public void onError(String message) {
                statusText.setText(message);
                statusText.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startWakeWordStandby();
                    }
                }, 3000);
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
        if (requestCode == REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startWakeWordStandby();
        }
    }
}
