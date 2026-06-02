package com.kidsFriend;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.kidsFriend.data.config.AppConfig;
import com.kidsFriend.data.config.BackendConnectionChecker;
import com.kidsFriend.data.repository.RepositoryCallback;
import com.kidsFriend.data.repository.TemiRepository;
import com.kidsFriend.ui.MembershipCardActivity;
import com.kidsFriend.ui.QuestionActivity;
import com.kidsFriend.ui.QuizActivity;
import com.kidsFriend.voice.IntentRouter;
import com.kidsFriend.voice.QuestionReconstructor;
import com.kidsFriend.voice.TemiSpeechSpeaker;
import com.kidsFriend.voice.VoiceInputManager;
import com.kidsFriend.voice.WakeWordMatcher;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.listeners.OnRobotReadyListener;

/**
 * 통합 홈 화면. 평소에는 얼굴을 띄우고 "친구야" 호출을 대기하다가,
 * 아이의 말을 듣고 알맞은 기능(질문/퀴즈/회원카드)으로 자동 이동합니다.
 */
public class MainActivity extends AppCompatActivity implements OnRobotReadyListener {
    private static final String TAG = "MainActivity";
    private static final int LOW_BATTERY_PERCENT = 20;
    private static final int REQUEST_RECORD_AUDIO = 2001;
    private static boolean errorReporterInstalled;

    private final TemiSpeechSpeaker speaker = new TemiSpeechSpeaker();

    private TemiRepository repository;
    private VoiceInputManager voiceInputManager;
    private BroadcastReceiver batteryReceiver;
    private Robot robot;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AppConfig.init(this);
        BackendConnectionChecker.check();
        repository = new TemiRepository(this);
        voiceInputManager = new VoiceInputManager(this);
        installUnexpectedErrorReporter();

        robot = Robot.getInstance();
        robot.addOnRobotReadyListener(this);

        statusText = findViewById(R.id.text_home_status);
        Button backButton = findViewById(R.id.button_back);
        Button operatorMenuButton = findViewById(R.id.button_operator_menu);

        backButton.setOnClickListener(v -> {
            robot.setKioskModeOn(false);
            finish();
        });
        operatorMenuButton.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ApiTestActivity.class)));

        updateRobotStatus("ACTIVE");
        registerBatteryReceiver();
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
        robot.hideTopBar();
        robot.setKioskModeOn(true);
        updateRobotStatus("ACTIVE");
        startWakeWordStandby();
    }

    @Override
    protected void onPause() {
        super.onPause();
        voiceInputManager.stopListening();
    }

    @Override
    public void onRobotReady(boolean isReady) {
        if (isReady) {
            robot.hideTopBar();
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

    private void startWakeWordStandby() {
        if (!ensureAudioPermission()) {
            return;
        }
        statusText.setText(R.string.home_idle_hint);
        voiceInputManager.startContinuousListening(new VoiceInputManager.Callback() {
            @Override
            public void onReady() {
                statusText.setText(R.string.home_idle_hint);
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
                statusText.setText(R.string.home_wake_detected);
                String textAfterWakeWord = WakeWordMatcher.textAfterWakeWord(text);
                if (!TextUtils.isEmpty(textAfterWakeWord)) {
                    routeIntent(textAfterWakeWord);
                } else {
                    startCommandListening();
                }
            }

            @Override
            public void onError(String message) {
                statusText.setText(message);
            }
        });
    }

    private void startCommandListening() {
        statusText.setText(R.string.voice_listening);
        voiceInputManager.startSingleListening(new VoiceInputManager.Callback() {
            @Override
            public void onReady() {
                statusText.setText(R.string.voice_listening);
            }

            @Override
            public void onPartialResult(String text) {
                if (!TextUtils.isEmpty(text)) {
                    statusText.setText(getString(R.string.voice_heard_format, text));
                }
            }

            @Override
            public void onResult(String text) {
                routeIntent(text);
            }

            @Override
            public void onError(String message) {
                statusText.setText(message);
            }
        });
    }

    private void routeIntent(String command) {
        IntentRouter.Intent intent = IntentRouter.route(command);
        switch (intent) {
            case QUIZ:
                statusText.setText(R.string.home_routing_quiz);
                speaker.speak(getString(R.string.home_routing_quiz));
                startActivity(new Intent(this, QuizActivity.class));
                break;
            case MEMBERSHIP:
                statusText.setText(R.string.home_routing_membership);
                speaker.speak(getString(R.string.home_routing_membership));
                startActivity(new Intent(this, MembershipCardActivity.class));
                break;
            case CHAT:
            default:
                routeToQuestion(command);
                break;
        }
    }

    private void routeToQuestion(String rawText) {
        statusText.setText(R.string.home_routing_question);
        speaker.speak(getString(R.string.home_routing_question));
        String reconstructed = QuestionReconstructor.reconstruct(rawText);
        Intent intent = new Intent(this, QuestionActivity.class);
        intent.putExtra(QuestionActivity.EXTRA_INITIAL_VOICE_TEXT,
                TextUtils.isEmpty(reconstructed) ? rawText : reconstructed);
        startActivity(intent);
    }

    private boolean ensureAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        statusText.setText(R.string.voice_permission_required);
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
        if (requestCode != REQUEST_RECORD_AUDIO) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startWakeWordStandby();
        } else {
            statusText.setText(R.string.voice_permission_denied);
        }
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
    protected void onDestroy() {
        try {
            robot.toggleWakeup(false);
            robot.setKioskModeOn(false);
            robot.removeOnRobotReadyListener(this);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Temi listener removal failed.", exception);
        }
        if (voiceInputManager != null) {
            voiceInputManager.destroy();
        }
        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver);
        }
        super.onDestroy();
    }
}
