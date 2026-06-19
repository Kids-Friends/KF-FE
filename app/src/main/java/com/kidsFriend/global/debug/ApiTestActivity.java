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
import com.kidsFriend.MainActivity;
import com.kidsFriend.domain.chat.response.QuestionResponse;
import com.kidsFriend.global.repository.RepositoryCallback;
import com.kidsFriend.global.repository.TemiRepository;
import com.kidsFriend.domain.sensor.service.RobotActionManager;

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
    private TextView testResultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_test);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        repository = new TemiRepository(this);

        Button backButton = findViewById(R.id.button_back);
        testResultText = findViewById(R.id.text_test_result);

        backButton.setOnClickListener(v -> finish());

        // 시나리오 1: 인사
        findViewById(R.id.btn_scen_greeting).setOnClickListener(v -> runScenario("넌 정체가 뭐야 !"));

        // 시나리오 2: 회원등록
        findViewById(R.id.btn_scen_membership).setOnClickListener(v -> runScenario("회원등록을 하고 싶어"));

        // 시나리오 3: 위치 안내
        findViewById(R.id.btn_scen_location).setOnClickListener(v -> runScenario("미끄럼틀은 어디 있어?"));

        // 시나리오 4: 퀴즈
        findViewById(R.id.btn_scen_quiz).setOnClickListener(v -> runScenario("퀴즈를 하고 싶어"));

        // 시나리오 5: AI 대화
        findViewById(R.id.btn_scen_chat).setOnClickListener(v -> showTestAiChatDialog());

        // 시나리오 6: 공기질
        findViewById(R.id.btn_scen_air).setOnClickListener(v -> runScenario("지금 미세먼지가 어때?"));

        // 시나리오 7: 엔딩
        findViewById(R.id.btn_scen_ending).setOnClickListener(v -> runScenario("테미야, 고마워!"));

        // 시나리오 8: 화재경보
        findViewById(R.id.btn_scen_fire).setOnClickListener(v -> {
            setTestResult("화재 경보 발령 !!!");
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("injected_sensor", "FIRE_DETECTED");
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        });

        ensureAudioPermission();
    }

    private void runScenario(String injectedText) {
        setTestResult("시나리오 수행: [" + injectedText + "]");
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("injected_stt", injectedText);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
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
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
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
    }

    private void ensureAudioPermission() {
        if (hasAudioPermission()) {
            return;
        }
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
}
