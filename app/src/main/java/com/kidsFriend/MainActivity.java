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
import com.robotemi.sdk.listeners.OnRobotReadyListener;

public class MainActivity extends AppCompatActivity implements OnRobotReadyListener {
    private static final String TAG = "MainActivity";
    private static final int LOW_BATTERY_PERCENT = 20;
    private static boolean errorReporterInstalled;

    private TemiRepository repository;
    private BroadcastReceiver batteryReceiver;
    private Robot robot;

    // 💡 원본 소스코드 Interface 섹션에 있는 Robot.NlpListener 타입을 정확히 매핑
    private Robot.NlpListener nlpInterceptor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AppConfig.init(this);
        repository = new TemiRepository(this);
        installUnexpectedErrorReporter();

        robot = Robot.getInstance();
        robot.addOnRobotReadyListener(this);

        updateRobotStatus("ACTIVE");
        registerBatteryReceiver();

        // 💡 [수정] nlpResult.getText() 대신 실제 변수인 resolvedQuery를 사용합니다.
        nlpInterceptor = new Robot.NlpListener() {
            @Override
            public void onNlpCompleted(NlpResult nlpResult) {
                if (nlpResult != null) {
                    Log.d(TAG, "Temi NLP intercepted: " + nlpResult.resolvedQuery);
                }
            }
        };

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

    // 💡 Robot 클래스에 존재하는 순정 onStart(ActivityInfo) 메서드만 명확히 호출
    @Override
    protected void onStart() {
        super.onStart();
        try {
            ActivityInfo activityInfo = getPackageManager()
                    .getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
            robot.onStart(activityInfo);
            Log.d(TAG, "Temi onStart: Sovereignty declared.");
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Temi activity metadata is not available.", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nlpInterceptor != null) {
            robot.addNlpListener(nlpInterceptor);
        }

        // 💡 Robot 클래스 본문에 존재하는 확실한 메서드만 호출
        robot.hideTopBar();
        robot.setKioskModeOn(true);

        updateRobotStatus("ACTIVE");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nlpInterceptor != null) {
            robot.removeNlpListener(nlpInterceptor);
        }
    }

    // 💡 [수정] Robot 클래스에 존재하지 않는 robot.onStop()이 에러를 유발했으므로 액티비티 주권 선언용 오버라이드 제거

    @Override
    protected void onDestroy() {
        try {
            // 💡 [수정] 소스코드 스펙 확인 결과, 깨우기 모드 복구는 toggleWakeup(false)가 맞습니다.
            robot.toggleWakeup(false);
            robot.setKioskModeOn(false);
            Log.d(TAG, "Temi default functions restored.");

            robot.removeOnRobotReadyListener(this);
            if (nlpInterceptor != null) {
                robot.removeNlpListener(nlpInterceptor);
            }
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
            robot.hideTopBar();

            // 💡 [수정] 소스코드 스펙 확인 결과 Kiosk Mode 상태에서 기본 음성 인식을 끄는 마스터 키는 toggleWakeup(true) 입니다.
            robot.toggleWakeup(true);
            Log.d(TAG, "Temi Wakeup Mode Disabled (true = 차단)");

            try {
                robot.requestToBeKioskApp();
                robot.setKioskModeOn(true);
                Log.d(TAG, "Kiosk mode activated successfully.");
            } catch (Exception e) {
                Log.w(TAG, "Kiosk mode could not be activated: " + e.getMessage());
            }
        }
        updateRobotStatus(isReady ? "ACTIVE" : "INACTIVE");
    }
}