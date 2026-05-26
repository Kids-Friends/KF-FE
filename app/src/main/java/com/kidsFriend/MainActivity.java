package com.kidsFriend;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.kidsFriend.data.config.AppConfig;
import com.kidsFriend.data.repository.RepositoryCallback;
import com.kidsFriend.data.repository.TemiRepository;
import com.kidsFriend.ui.DemoActivity;
import com.robotemi.sdk.NlpResult;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.listeners.OnNlpListener;
import com.robotemi.sdk.listeners.OnRobotReadyListener;

public class MainActivity extends AppCompatActivity implements OnRobotReadyListener, OnNlpListener {
    private static final String TAG = "MainActivity";
    private static final int LOW_BATTERY_PERCENT = 20;
    private static boolean errorReporterInstalled;

    private TemiRepository repository;
    private BroadcastReceiver batteryReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AppConfig.init(this);
        repository = new TemiRepository(this);
        installUnexpectedErrorReporter();
        Robot.getInstance().addOnRobotReadyListener(this);
        updateRobotStatus("ACTIVE");
        registerBatteryReceiver();

        Button apiTestButton = findViewById(R.id.button_api_test);
        Button demoTestButton = findViewById(R.id.button_demo_test);

        apiTestButton.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ApiTestActivity.class));
        });

        demoTestButton.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, DemoActivity.class));
        });
    }

    private void updateRobotStatus(String status) {
        if (repository == null) {
            return;
        }
        repository.updateRobotStatus(status, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Log.d(TAG, "Robot status updated: " + status);
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Robot status update failed: " + message);
            }
        });
    }

    private void registerBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level < 0 || scale <= 0) {
                    return;
                }

                int batteryPercent = Math.round(level * 100f / scale);
                if (batteryPercent <= LOW_BATTERY_PERCENT) {
                    updateRobotStatus("INACTIVE");
                }
            }
        };
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void installUnexpectedErrorReporter() {
        if (errorReporterInstalled) {
            return;
        }
        errorReporterInstalled = true;
        Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            updateRobotStatus("ERROR");
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 포그라운드로 돌아올 때마다 NLP 인터셉터 재등록
        // → 테미 기본 음성 응답 UI가 우리 앱 위에 뜨는 것을 막음
        Robot.getInstance().addOnNlpListener(this);
        updateRobotStatus("ACTIVE");
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 백그라운드로 가면 NLP 인터셉터 해제 (리소스 낭비 방지)
        Robot.getInstance().removeOnNlpListener(this);
    }

    @Override
    protected void onDestroy() {
        try {
            Robot.getInstance().removeOnRobotReadyListener(this);
            Robot.getInstance().removeOnNlpListener(this);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Temi listener removal failed.", exception);
        }
        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver);
        }
        super.onDestroy();
    }

    @Override
    public void onRobotReady(boolean isReady) {
        if (isReady) {
            try {
                ActivityInfo activityInfo = getPackageManager()
                        .getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
                // 테미 SDK에 이 앱의 메타데이터(스킬/키오스크 정보) 전달 — 반드시 onStart 먼저
                Robot.getInstance().onStart(activityInfo);
            } catch (PackageManager.NameNotFoundException exception) {
                Log.w(TAG, "Temi activity metadata is not available.", exception);
            }

            // 상단 톱바 숨기기 — 아래로 스와이프해도 시스템 메뉴가 나오지 않음
            Robot.getInstance().hideTopBar();

            // 프로그래밍 방식 키오스크 모드 활성화
            // 주의: 테미 설정 앱에서 먼저 이 앱을 키오스크로 지정해야 동작함
            // 미지정 상태에서 호출하면 SecurityException 또는 무시됨
            try {
                Robot.getInstance().setKioskModeOn(true);
                Log.d(TAG, "Kiosk mode activated.");
            } catch (Exception e) {
                // temi PRO 라이선스 미보유 또는 관리자 미설정 시 발생
                Log.w(TAG, "Kiosk mode could not be activated: " + e.getMessage());
            }
        }
        updateRobotStatus(isReady ? "ACTIVE" : "INACTIVE");
    }

    /**
     * 테미의 기본 NLP 결과를 이 앱이 가로챔.
     * 아무 처리도 하지 않음으로써 테미 기본 대화 UI 표시를 차단.
     * 실제 음성 처리는 VoiceInputManager(SpeechRecognizer)가 독립적으로 담당.
     */
    @Override
    public void onNlpCompleted(NlpResult nlpResult) {
        Log.d(TAG, "Temi NLP intercepted (suppressed): " + nlpResult.getText());
    }
}
