package com.kidsFriend;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.kidsFriend.data.config.AppConfig;
import com.kidsFriend.data.config.BackendConnectionChecker;
import com.kidsFriend.data.model.QuestionResponse;
import com.kidsFriend.data.repository.RepositoryCallback;
import com.kidsFriend.data.repository.TemiRepository;
import com.kidsFriend.robot.RobotActionManager;
import com.kidsFriend.robot.RobotResilienceManager;
import com.kidsFriend.robot.SensorEventPoller;
import com.kidsFriend.ui.MembershipCardActivity;
import com.kidsFriend.ui.QuizActivity;
import com.kidsFriend.voice.IntentRouter;
import com.kidsFriend.voice.TemiSpeechSpeaker;
import com.kidsFriend.voice.VoiceInputManager;
import com.kidsFriend.voice.WakeWordMatcher;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.constants.Gender;
import com.robotemi.sdk.listeners.OnRobotReadyListener;
import com.robotemi.sdk.permission.OnRequestPermissionResultListener;
import com.robotemi.sdk.permission.Permission;
import com.robotemi.sdk.voice.model.TtsVoice;

import java.util.Collections;

/**
 * 통합 홈 화면.
 * - 평소: 얼굴을 띄우고 "친구야" 호출 또는 화면 터치를 대기합니다.
 * - 호출/터치 시: 다음 화면으로 넘어가지 않고 대화를 시작합니다(멀티턴 AI 대화).
 * - 대화 중 "퀴즈 풀고 싶어"처럼 말하면 해당 화면으로 전환합니다.
 */
public class MainActivity extends AppCompatActivity
        implements OnRobotReadyListener, OnRequestPermissionResultListener {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_RECORD_AUDIO = 2001;
    private static final int REQUEST_TTS_SETTINGS = 3001;
    // 대화가 이 시간 동안 한 단계도 진행되지 않으면(STT/TTS 멈춤 등) 대기 상태로 자동 복귀한다.
    private static final long CONVERSATION_TIMEOUT_MS = 45_000L;

    private enum State { IDLE, CONVERSATION }

    private final TemiSpeechSpeaker speaker = new TemiSpeechSpeaker();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private State state = State.IDLE;

    // 발화(TTS)가 끝날 무렵 다시 듣기를 시작합니다.
    private final Runnable listenRunnable = () -> {
        if (state == State.CONVERSATION) {
            listenInConversation();
        }
    };

    // 대화가 멈췄을 때(STT/TTS/AI 응답 없음) 안전하게 대기 상태로 되돌리는 워치독.
    private final Runnable conversationWatchdog = () -> {
        if (state == State.CONVERSATION) {
            Log.w(TAG, "대화 워치독: 일정 시간 진행 없음 → 대기 상태로 자동 복귀");
            enterIdle();
        }
    };

    private TemiRepository repository;
    private VoiceInputManager voiceInputManager;
    private SensorEventPoller sensorEventPoller;
    private RobotResilienceManager resilienceManager;
    private Robot robot;
    private ImageView faceImage;
    private TextView statusText;
    private TextView answerText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AppConfig.init(this);
        BackendConnectionChecker.check();
        repository = new TemiRepository(this);
        voiceInputManager = new VoiceInputManager(this);
        // 라즈베리파이 센서 이벤트(KF_BE 경유)를 폴링해 로봇 동작으로 변환한다.
        RobotActionManager actionManager = new RobotActionManager();
        actionManager.setOnFaceChangeListener(drawableRes -> 
            uiHandler.post(() -> setFace(drawableRes))
        );
        sensorEventPoller = new SensorEventPoller(actionManager);
        // 시연 중 돌발상황을 Temi 내장 기능으로 자동 복구(내장 사람감지 백업, 들림/끌림 정지, 배터리 안내).
        resilienceManager = new RobotResilienceManager(actionManager);

        robot = Robot.getInstance();
        robot.addOnRobotReadyListener(this);
        robot.addOnRequestPermissionResultListener(this);

        faceImage = findViewById(R.id.image_face);
        
        // 데모용 비밀 트리거 (얼굴 길게 누르기 -> 아이 감지 센서 이벤트 모의 발생)
        faceImage.setOnLongClickListener(v -> {
            Log.d(TAG, "Secret Trigger: Mock CHILD_DETECTED event");
            actionManager.onSensorEvent("CHILD_DETECTED", null);
            return true;
        });

        statusText = findViewById(R.id.text_home_status);
        // 데모용 비밀 트리거 2: 상태 텍스트 길게 누르기 -> 위험(TILT) 상황 모의 발생
        statusText.setOnLongClickListener(v -> {
            Log.d(TAG, "Secret Trigger: Mock TILT event");
            actionManager.onSensorEvent("TILT", null);
            return true;
        });

        answerText = findViewById(R.id.text_home_answer);
        // 데모용 비밀 트리거 3: 답변 텍스트 길게 누르기 -> 장애물 접근(20cm) 모의 발생
        answerText.setOnLongClickListener(v -> {
            Log.d(TAG, "Secret Trigger: Mock OBSTACLE_DETECTED event");
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("distance_cm", 20);
            actionManager.onSensorEvent("OBSTACLE_DETECTED", payload);
            return true;
        });

        // 데모용 비밀 트리거 4: 답변 텍스트 더블 클릭(또는 빠른 연속 클릭) -> 배터리 부족 모의 발생
        answerText.setOnClickListener(new View.OnClickListener() {
            private long lastClickTime = 0;
            @Override
            public void onClick(View v) {
                long clickTime = System.currentTimeMillis();
                if (clickTime - lastClickTime < 500) {
                    Log.d(TAG, "Secret Trigger: Mock LOW_BATTERY event");
                    actionManager.onSensorEvent("LOW_BATTERY", null);
                } else if (state == State.IDLE) {
                    voiceInputManager.stopListening();
                    startConversation(null);
                }
                lastClickTime = clickTime;
            }
        });
        
        Button backButton = findViewById(R.id.button_back);
        Button operatorMenuButton = findViewById(R.id.button_operator_menu);

        // 데모용 비밀 트리거 5: 오퍼레이터 메뉴 버튼을 길게 누르면 강제로 "퀴즈 풀고 싶어" STT 결과 주입
        // 행사장 소음으로 마이크가 안 먹힐 때 최후의 수단
        operatorMenuButton.setOnLongClickListener(v -> {
            Log.d(TAG, "Secret Trigger: Mock STT Injection (Quiz)");
            voiceInputManager.stopListening();
            startConversation("퀴즈 풀고 싶어");
            return true;
        });

        // 화면을 터치하면 대화를 시작합니다.
        findViewById(R.id.root_main).setOnClickListener(v -> {
            if (state == State.IDLE) {
                voiceInputManager.stopListening();
                startConversation(null);
            }
        });

        backButton.setOnClickListener(v -> {
            robot.setKioskModeOn(false);
            finish();
        });
        operatorMenuButton.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ApiTestActivity.class)));
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
        sensorEventPoller.start();
        uiHandler.post(systemWatchdog);
        enterIdle();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorEventPoller.stop();
        voiceInputManager.stopListening();
        uiHandler.removeCallbacks(systemWatchdog);
    }
        uiHandler.removeCallbacks(systemWatchdog);
    }
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
            applyCuteVoice();
            // 로봇이 준비된 뒤 내장 복구 기능(사람감지/들림/끌림/배터리) 등록.
            resilienceManager.register();
        }
    }

    /** 어리고 발랄한 캐릭터 톤으로 테미 내장 목소리를 변경합니다. (SETTINGS 권한 필요) */
    private void applyCuteVoice() {
        if (robot.checkSelfPermission(Permission.SETTINGS) == Permission.GRANTED) {
            setCuteVoice();
        } else {
            robot.requestPermissions(Collections.singletonList(Permission.SETTINGS), REQUEST_TTS_SETTINGS);
        }
    }

    private void setCuteVoice() {
        // 여성 음색 + 최대 피치(+10) + 경쾌한 속도(1.2) = 높고 귀엽고 활기찬 톤
        boolean ok = robot.setTtsVoice(new TtsVoice(Gender.FEMALE, 1.2f, 10));
        Log.d(TAG, "setTtsVoice(cute) result = " + ok);
    }

    @Override
    public void onRequestPermissionResult(Permission permission, int grantResult, int requestCode) {
        if (permission == Permission.SETTINGS && grantResult == Permission.GRANTED) {
            setCuteVoice();
        }
    }

    /** 대기 상태: 얼굴 + "친구야 불러줘" 안내, 호출어 대기. */
    private void enterIdle() {
        disarmConversationWatchdog();
        if (!ensureAudioPermission()) {
            return;
        }
        state = State.IDLE;
        setFace(R.drawable.face_peaceful);
        statusText.setText(R.string.home_idle_hint);
        answerText.setVisibility(View.GONE);

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
                startConversation(WakeWordMatcher.textAfterWakeWord(text));
            }

            @Override
            public void onError(String message) {
                statusText.setText(message);
            }
        });
    }

    /** 대화 시작. 호출어 뒤에 바로 말이 붙어 있으면 그 말부터 처리합니다. */
    private void startConversation(String firstUtterance) {
        state = State.CONVERSATION;
        setFace(R.drawable.face_excited);
        answerText.setVisibility(View.GONE);

        if (!TextUtils.isEmpty(firstUtterance)) {
            speaker.speak(getString(R.string.home_wake_detected));
            handleUtterance(firstUtterance);
        } else {
            speakThenListen(getString(R.string.home_wake_detected));
        }
    }

    /** 대화 중 한 마디 듣기: "듣고 있어요" 화면을 띄웁니다. */
    private void listenInConversation() {
        armConversationWatchdog();
        setFace(R.drawable.face_excited);
        statusText.setText(R.string.home_listening);
        voiceInputManager.startSingleListening(new VoiceInputManager.Callback() {
            @Override
            public void onReady() {
                statusText.setText(R.string.home_listening);
            }

            @Override
            public void onPartialResult(String text) {
                if (!TextUtils.isEmpty(text)) {
                    statusText.setText(getString(R.string.voice_heard_format, text));
                }
            }

            @Override
            public void onResult(String text) {
                handleUtterance(text);
            }

            @Override
            public void onError(String message) {
                // 알아듣지 못하면 대화를 마치고 대기 상태로 돌아갑니다.
                enterIdle();
            }
        });
    }

    private long lastRouteTime = 0;

    /** 들은 말의 의도를 판단해 화면 전환 또는 AI 대화를 이어갑니다. */
    private void handleUtterance(String text) {
        if (System.currentTimeMillis() - lastRouteTime < 2000) {
            Log.w(TAG, "handleUtterance: Ignoring rapid utterance to prevent multi-launch.");
            return;
        }
        lastRouteTime = System.currentTimeMillis();

        if (isEndPhrase(text)) {
            speaker.speak(getString(R.string.home_bye));
            enterIdle();
            return;
        }

        IntentRouter.Intent intent = IntentRouter.route(text);
        switch (intent) {
            case QUIZ:
                setFace(R.drawable.face_joy);
                speaker.speak(getString(R.string.home_routing_quiz));
                statusText.setText(R.string.home_routing_quiz);
                startActivity(new Intent(this, QuizActivity.class));
                break;
            case MEMBERSHIP:
                setFace(R.drawable.face_joy);
                speaker.speak(getString(R.string.home_routing_membership));
                statusText.setText(R.string.home_routing_membership);
                startActivity(new Intent(this, MembershipCardActivity.class));
                break;
            case CHAT:
            default:
                lastRouteTime = 0; // 채팅은 딜레이 면제 (연속 대화 허용)
                answerWithAi(text);
                break;
        }
    }

    /** AI에게 묻고 답을 화면+음성으로 보여준 뒤, 다시 듣기로 대화를 이어갑니다. */
    private void answerWithAi(String question) {
        armConversationWatchdog();
        setFace(R.drawable.face_peaceful);
        statusText.setText(R.string.home_thinking);
        repository.askQuestion(question, new RepositoryCallback<QuestionResponse>() {
            @Override
            public void onSuccess(QuestionResponse data) {
                setFace(R.drawable.face_joy);
                showAnswer(data.answer);
                speakThenListen(data.answer);
            }

            @Override
            public void onError(String message) {
                // 네트워크/ngrok/AI 실패 시: 원시 에러 대신 친근한 안내로 대화를 이어간다.
                Log.w(TAG, "AI 응답 실패: " + message);
                setFace(R.drawable.face_sadness);
                String friendly = "미안해, 지금은 대답하기가 조금 어려워. 잠시 뒤에 다시 물어봐 줄래?";
                showAnswer(friendly);
                speaker.speak(friendly);
                if (state == State.CONVERSATION) {
                    listenInConversation();
                }
            }
        });
    }

    private void showAnswer(String text) {
        answerText.setText(text);
        answerText.setVisibility(View.VISIBLE);
    }

    /** 발화를 시작하고, 발화가 끝날 무렵 다시 듣기를 시작합니다(테미가 자기 음성을 인식하는 문제 방지). */
    private void speakThenListen(String text) {
        armConversationWatchdog();
        speaker.speak(text);
        uiHandler.removeCallbacks(listenRunnable);
        // 발화 길이를 글자 수로 추정해 그만큼 기다린 뒤 듣기 시작
        long estimatedMs = Math.min(12000L, Math.max(2500L, text.length() * 150L));
        uiHandler.postDelayed(listenRunnable, estimatedMs);
    }

    private void setFace(int drawableRes) {
        faceImage.setImageResource(drawableRes);
    }

    /** 대화 한 단계가 시작될 때마다 워치독 타이머를 다시 건다(정상 진행 중엔 발동하지 않음). */
    private void armConversationWatchdog() {
        uiHandler.removeCallbacks(conversationWatchdog);
        uiHandler.postDelayed(conversationWatchdog, CONVERSATION_TIMEOUT_MS);
    }

    private void disarmConversationWatchdog() {
        uiHandler.removeCallbacks(conversationWatchdog);
    }

    private boolean isEndPhrase(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", "");
        return normalized.contains("그만")
                || normalized.contains("안녕")
                || normalized.contains("됐어")
                || normalized.contains("끝");
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
            enterIdle();
        } else {
            statusText.setText(R.string.voice_permission_denied);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (resilienceManager != null) {
                resilienceManager.unregister();
            }
            robot.toggleWakeup(false);
            robot.setKioskModeOn(false);
            robot.removeOnRobotReadyListener(this);
            robot.removeOnRequestPermissionResultListener(this);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Temi listener removal failed.", exception);
        }
        uiHandler.removeCallbacksAndMessages(null);
        if (voiceInputManager != null) {
            voiceInputManager.destroy();
        }
        super.onDestroy();
    }
}
();
            }
            robot.toggleWakeup(false);
            robot.setKioskModeOn(false);
            robot.removeOnRobotReadyListener(this);
            robot.removeOnRequestPermissionResultListener(this);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Temi listener removal failed.", exception);
        }
        uiHandler.removeCallbacksAndMessages(null);
        if (voiceInputManager != null) {
            voiceInputManager.destroy();
        }
        super.onDestroy();
    }
}
