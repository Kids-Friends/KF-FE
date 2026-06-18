package com.kidsFriend.global.debug;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.listeners.OnRobotReadyListener;

import com.kidsFriend.R;
import com.kidsFriend.domain.chat.response.QuestionResponse;
import com.kidsFriend.domain.chat.service.QuestionActivity;
import com.kidsFriend.domain.quiz.service.QuizActivity;
import com.kidsFriend.domain.zone.ZoneActivity;
import com.kidsFriend.global.repository.RepositoryCallback;
import com.kidsFriend.global.repository.TemiRepository;
import com.kidsFriend.global.voice.VoiceInputManager;
import com.kidsFriend.global.voice.WakeWordMatcher;

/**
 * 시연 시나리오 점검용 디버그 화면.
 *
 * <p>행사장에서 마이크/네트워크 상태와 무관하게 각 시나리오 화면(자유질문 AI, 퀴즈, 놀이존 안내)과
 * 호출어 대기를 바로 띄워 확인할 수 있다. 백엔드에서 제거된 회원/사진/포인트/채팅로그 테스트는 더 이상 없다.</p>
 */
public class ApiTestActivity extends AppCompatActivity implements OnRobotReadyListener {
    private static final String TAG = "ApiTestActivity";
    private static final int REQUEST_RECORD_AUDIO = 1001;

    private TemiRepository repository;
    private VoiceInputManager wakeVoiceInputManager;
    private TextView wakeStatusText;
    private TextView testResultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_test);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        repository = new TemiRepository(this);

        Button backButton = findViewById(R.id.button_back);
        Button questionButton = findViewById(R.id.button_question);
        Button quizButton = findViewById(R.id.button_quiz);
        Button locationGuideButton = findViewById(R.id.button_location_guide);
        wakeStatusText = findViewById(R.id.text_wake_status);
        Button wakeButton = findViewById(R.id.button_start_wake_listening);

        testResultText = findViewById(R.id.text_test_result);
        Button testAiChatButton = findViewById(R.id.button_test_ai_chat);

        wakeVoiceInputManager = new VoiceInputManager(this);

        backButton.setOnClickListener(v -> finish());
        testAiChatButton.setOnClickListener(v -> showTestAiChatDialog());

        questionButton.setOnClickListener(v -> openScreen(QuestionActivity.class));
        quizButton.setOnClickListener(v -> openScreen(QuizActivity.class));
        locationGuideButton.setOnClickListener(v -> openScreen(ZoneActivity.class));
        wakeButton.setOnClickListener(v -> startWakeWordStandby());

        ensureAudioPermission();
    }

    private long lastOpenScreenTime = 0;

    private void openScreen(Class<?> activityClass) {
        if (System.currentTimeMillis() - lastOpenScreenTime < 1000) return;
        lastOpenScreenTime = System.currentTimeMillis();
        startActivity(new Intent(this, activityClass));
    }

    private void showTestAiChatDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(getString(R.string.test_ai_chat_hint));

        new AlertDialog.Builder(this)
                .setTitle(R.string.test_btn_ai_chat)
                .setView(input)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_save, (dialog, which) -> {
                    String message = input.getText().toString().trim();
                    if (message.isEmpty()) return;
                    setTestResult("AI 대화 요청 중...");
                    repository.askQuestion(message, new RepositoryCallback<QuestionResponse>() {
                        @Override
                        public void onSuccess(QuestionResponse data) {
                            setTestResult("AI 응답: " + data.answer);
                        }
                        @Override
                        public void onError(String message1) {
                            setTestResult("오류: " + message1);
                        }
                    });
                })
                .show();
    }

    private void setTestResult(String result) {
        runOnUiThread(() -> testResultText.setText(getString(R.string.test_result_prefix) + result));
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
        try {
            Robot.getInstance().removeOnRobotReadyListener(this);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Temi listener removal failed.", exception);
        }
        super.onDestroy();
    }

    @Override
    public void onRobotReady(boolean isReady) {
        if (isReady) {
            try {
                ActivityInfo activityInfo = getPackageManager()
                        .getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
                Robot.getInstance().onStart(activityInfo);
            } catch (PackageManager.NameNotFoundException exception) {
                Log.w(TAG, "Temi activity metadata is not available.", exception);
            }
        }
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
                Intent intent = new Intent(ApiTestActivity.this, QuestionActivity.class);
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
