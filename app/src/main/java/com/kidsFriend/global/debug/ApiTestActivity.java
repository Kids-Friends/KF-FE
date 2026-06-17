package com.kidsFriend.global.debug;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.kidsFriend.R;
import com.kidsFriend.domain.membership.ClientResponse;
import com.kidsFriend.global.repository.RepositoryCallback;
import com.kidsFriend.global.repository.TemiRepository;
import com.kidsFriend.global.session.SessionManager;
import com.kidsFriend.domain.chat.QuestionActivity;
import com.kidsFriend.domain.quiz.QuizActivity;
import com.kidsFriend.domain.zone.ZoneActivity;
import com.kidsFriend.global.voice.VoiceInputManager;
import com.kidsFriend.global.voice.WakeWordMatcher;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.listeners.OnRobotReadyListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class ApiTestActivity extends AppCompatActivity implements OnRobotReadyListener {
    private static final String TAG = "ApiTestActivity";
    private static final int REQUEST_RECORD_AUDIO = 1001;

    private TemiRepository repository;
    private VoiceInputManager wakeVoiceInputManager;
    private TextView wakeStatusText;
    private TextView testResultText;
    private EditText testClientIdInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_test);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        repository = new TemiRepository(this);
        
        Button backButton = findViewById(R.id.button_back);
        Button questionButton = findViewById(R.id.button_question);
        Button quizButton = findViewById(R.id.button_quiz);
        Button approachButton = findViewById(R.id.button_approach);
        Button locationGuideButton = findViewById(R.id.button_location_guide);
        Button rewardButton = findViewById(R.id.button_reward);
        Button photoButton = findViewById(R.id.button_photo);
        Button clientSettingsButton = findViewById(R.id.button_client_settings);
        wakeStatusText = findViewById(R.id.text_wake_status);
        Button wakeButton = findViewById(R.id.button_start_wake_listening);

        testClientIdInput = findViewById(R.id.edit_test_client_id);
        testResultText = findViewById(R.id.text_test_result);
        Button testClientIdSaveButton = findViewById(R.id.button_test_client_id_save);
        Button testAiChatButton = findViewById(R.id.button_test_ai_chat);
        Button testPointButton = findViewById(R.id.button_test_point);
        Button testChatLogButton = findViewById(R.id.button_test_chat_log);

        wakeVoiceInputManager = new VoiceInputManager(this);

        backButton.setOnClickListener(v -> finish());
        testClientIdInput.setText(SessionManager.getInstance(this).getCurrentClientId());
        testClientIdSaveButton.setOnClickListener(v -> saveTestClientId());
        testAiChatButton.setOnClickListener(v -> showTestAiChatDialog());
        testPointButton.setOnClickListener(v -> runTestAddPoint());
        testChatLogButton.setOnClickListener(v -> showTestChatLogDialog());

        questionButton.setOnClickListener(v -> openScreen(QuestionActivity.class));
        quizButton.setOnClickListener(v -> openScreen(QuizActivity.class));
        approachButton.setOnClickListener(v -> showPendingFeature());
        locationGuideButton.setOnClickListener(v -> openScreen(ZoneActivity.class));
        rewardButton.setOnClickListener(v -> showPendingFeature());
        photoButton.setOnClickListener(v -> simulateCaptureAndUpload());
        clientSettingsButton.setOnClickListener(v -> showClientSettingsDialog());
        wakeButton.setOnClickListener(v -> startWakeWordStandby());

        ensureAudioPermission();
    }

    private long lastOpenScreenTime = 0;

    private void openScreen(Class<?> activityClass) {
        if (System.currentTimeMillis() - lastOpenScreenTime < 1000) return;
        lastOpenScreenTime = System.currentTimeMillis();
        startActivity(new Intent(this, activityClass));
    }

    private void showPendingFeature() {
        Toast.makeText(this, R.string.feature_pending_message, Toast.LENGTH_SHORT).show();
    }

    /**
     * 실제 카메라 하드웨어가 없는 환경에서도 업로드 프로세스를 테스트할 수 있도록
     * 더미 파일을 생성하여 Step 1(업로드) -> Step 2(DB 등록) 과정을 시뮬레이션합니다.
     */
    private void simulateCaptureAndUpload() {
        setTestResult("사진 촬영 시뮬레이션 중...");

        File dummyFile = new File(getCacheDir(), "test_capture_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(dummyFile)) {
            // 빈 파일 혹은 작은 데이터를 써서 더미 이미지 파일 생성
            fos.write(new byte[1024]);
        } catch (IOException e) {
            Toast.makeText(this, "더미 파일 생성 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        repository.uploadAndSavePhoto(dummyFile, new RepositoryCallback<com.kidsFriend.domain.membership.PhotoResponse>() {
            @Override
            public void onSuccess(com.kidsFriend.domain.membership.PhotoResponse data) {
                setTestResult("사진 업로드 및 DB 등록 성공: " + data.photoName);
                Toast.makeText(ApiTestActivity.this, "사진이 성공적으로 업로드되었습니다.", Toast.LENGTH_SHORT).show();
                // 사용이 끝난 더미 파일 삭제
                dummyFile.delete();
            }

            @Override
            public void onError(String message) {
                setTestResult("업로드 오류: " + message);
                Toast.makeText(ApiTestActivity.this, "업로드 실패: " + message, Toast.LENGTH_SHORT).show();
                dummyFile.delete();
            }
        });
    }

    private void showClientSettingsDialog() {
        repository.getClients(new RepositoryCallback<List<ClientResponse>>() {
            @Override
            public void onSuccess(List<ClientResponse> data) {
                showClientListDialog(data);
            }

            @Override
            public void onError(String message) {
                showManualClientIdDialog();
            }
        });
    }

    private void showClientListDialog(List<ClientResponse> clients) {
        if (clients == null || clients.isEmpty()) {
            showManualClientIdDialog();
            return;
        }

        String[] labels = new String[clients.size()];
        for (int i = 0; i < clients.size(); i++) {
            ClientResponse client = clients.get(i);
            String childName = client.childName == null || client.childName.trim().isEmpty()
                    ? client.clientId
                    : client.childName;
            labels[i] = childName + " / " + client.clientPoint + "pt";
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_client_title)
                .setItems(labels, (dialog, which) -> {
                    ClientResponse selected = clients.get(which);
                    SessionManager.getInstance(this).setCurrentClientId(selected.clientId);
                    Toast.makeText(this, R.string.settings_client_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.settings_cancel, null)
                .setNeutralButton(R.string.settings_client_manual, (dialog, which) -> showManualClientIdDialog())
                .show();
    }

    private void showManualClientIdDialog() {
        SessionManager sessionManager = SessionManager.getInstance(this);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(sessionManager.getCurrentClientId());
        input.setSelection(input.getText().length());
        input.setHint("client UUID");

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_client_title)
                .setMessage(R.string.settings_client_message)
                .setView(input)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_save, (dialog, which) -> {
                    String clientId = input.getText().toString().trim();
                    if (clientId.isEmpty()) {
                        Toast.makeText(this, R.string.settings_client_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    loadAndSaveCurrentClient(clientId);
                })
                .show();
    }

    private void loadAndSaveCurrentClient(String clientId) {
        repository.getClient(clientId, new RepositoryCallback<ClientResponse>() {
            @Override
            public void onSuccess(ClientResponse data) {
                SessionManager.getInstance(ApiTestActivity.this).setCurrentClientId(data.clientId);
                Toast.makeText(ApiTestActivity.this, R.string.settings_client_saved, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ApiTestActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showPhotoUrlDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("https://storage.example.com/photo.jpg");

        new AlertDialog.Builder(this)
                .setTitle(R.string.photo_url_title)
                .setMessage(R.string.photo_url_message)
                .setView(input)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_save, (dialog, which) -> {
                    String photoUrl = input.getText().toString().trim();
                    if (photoUrl.isEmpty()) {
                        Toast.makeText(this, R.string.photo_url_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    savePhotoUrl(photoUrl);
                })
                .show();
    }

    private void savePhotoUrl(String photoUrl) {
        String photoName = photoUrl.substring(photoUrl.lastIndexOf('/') + 1);
        repository.savePhoto(photoUrl, photoName, new RepositoryCallback<com.kidsFriend.domain.membership.PhotoResponse>() {
            @Override
            public void onSuccess(com.kidsFriend.domain.membership.PhotoResponse data) {
                Toast.makeText(ApiTestActivity.this, R.string.photo_saved, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ApiTestActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveTestClientId() {
        String clientId = testClientIdInput.getText().toString().trim();
        if (clientId.isEmpty()) {
            Toast.makeText(this, R.string.settings_client_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        SessionManager.getInstance(this).setCurrentClientId(clientId);
        Toast.makeText(this, R.string.test_client_id_saved, Toast.LENGTH_SHORT).show();
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
                    repository.askQuestion(message, new RepositoryCallback<com.kidsFriend.domain.chat.QuestionResponse>() {
                        @Override
                        public void onSuccess(com.kidsFriend.domain.chat.QuestionResponse data) {
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

    private void runTestAddPoint() {
        String clientId = testClientIdInput.getText().toString().trim();
        if (clientId.isEmpty()) {
            Toast.makeText(this, R.string.test_client_id_hint, Toast.LENGTH_SHORT).show();
            return;
        }
        setTestResult("포인트 추가 요청 중...");
        repository.addClientPoint(clientId, 1, new RepositoryCallback<com.kidsFriend.domain.membership.ClientResponse>() {
            @Override
            public void onSuccess(com.kidsFriend.domain.membership.ClientResponse data) {
                setTestResult("포인트 추가 성공 — 현재 포인트: " + data.clientPoint);
            }
            @Override
            public void onError(String message) {
                setTestResult("오류: " + message);
            }
        });
    }

    private void showTestChatLogDialog() {
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(48, 16, 48, 0);

        EditText questionInput = new EditText(this);
        questionInput.setSingleLine(true);
        questionInput.setHint(getString(R.string.test_chat_log_q_hint));

        EditText answerInput = new EditText(this);
        answerInput.setSingleLine(true);
        answerInput.setHint(getString(R.string.test_chat_log_a_hint));

        container.addView(questionInput);
        container.addView(answerInput);

        new AlertDialog.Builder(this)
                .setTitle(R.string.test_btn_chat_log)
                .setView(container)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_save, (dialog, which) -> {
                    String q = questionInput.getText().toString().trim();
                    String a = answerInput.getText().toString().trim();
                    if (q.isEmpty() || a.isEmpty()) return;
                    setTestResult("채팅 로그 저장 중...");
                    repository.saveChatLog(q, a, new RepositoryCallback<com.kidsFriend.domain.chat.ChatResponse>() {
                        @Override
                        public void onSuccess(com.kidsFriend.domain.chat.ChatResponse data) {
                            String chatId = data != null && data.chatId != null ? " — chatId: " + data.chatId : "";
                            setTestResult("채팅 로그 저장 성공" + chatId);
                        }
                        @Override
                        public void onError(String message) {
                            setTestResult("오류: " + message);
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
        if (testClientIdInput != null) {
            testClientIdInput.setText(SessionManager.getInstance(this).getCurrentClientId());
        }
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
