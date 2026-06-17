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
import com.kidsFriend.robot.RobotPositionReporter;
import com.kidsFriend.robot.RobotResilienceManager;
import com.kidsFriend.robot.SensorEventPoller;
import com.kidsFriend.ui.MembershipCardActivity;
import com.kidsFriend.ui.QuizActivity;
import com.kidsFriend.voice.IntentRouter;
import com.kidsFriend.voice.ScriptedResponder;
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

    // 대화 발화는 반드시 들려야 하므로 디바운스를 끈다(웨이크 응답 직후 AI 답변이 3초 쿨다운에 먹혀
    // 화면만 바뀌고 말은 안 나오는 "조용한 답변" 문제 방지).
    private final TemiSpeechSpeaker speaker = new TemiSpeechSpeaker(false);
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // 시스템 워치독 주기: 볼륨/키오스크/상단바 상태를 주기적으로 강제 원복한다.
    private static final long SYSTEM_WATCHDOG_INTERVAL_MS = 10_000L;

    // 시스템 워치독: 10초마다 (1) 볼륨이 크게 낮아졌으면 80%로 원복,
    // (2) 키오스크 모드가 풀렸으면 재활성, (3) 상단바가 다시 떴으면 숨김.
    // 어느 한 단계가 실패해도 전체가 멈추지 않도록 각각 try/catch로 격리하고 항상 다음 주기를 예약한다.
    private final Runnable systemWatchdog = new Runnable() {
        @Override
        public void run() {
            try {
                android.media.AudioManager am =
                        (android.media.AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);
                if (am != null) {
                    int target = 6;
                    int cur = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                    // 볼륨이 6이 아니면 강제로 6으로 맞춤 (사용자 요청: 테미 목소리가 너무 크므로 6으로 고정)
                    if (cur != target) {
                        am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "systemWatchdog: 볼륨 원복 실패(무시): " + e.getMessage());
            }
            try {
                if (robot != null) {
                    robot.setKioskModeOn(true);
                    robot.hideTopBar();
                }
            } catch (Exception e) {
                Log.w(TAG, "systemWatchdog: 키오스크/상단바 재적용 실패(무시): " + e.getMessage());
            }
            uiHandler.postDelayed(this, SYSTEM_WATCHDOG_INTERVAL_MS);
        }
    };

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
    private RobotPositionReporter positionReporter;
    private Robot robot;
    private android.os.PowerManager.WakeLock wakeLock;
    private ImageView faceImage;
    private TextView statusText;
    private TextView answerText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // [방어 코드] 하드웨어 수준 화면 꺼짐 절대 방지 (Wakelock 강제 획득)
        try {
            android.os.PowerManager powerManager = (android.os.PowerManager) getSystemService(android.content.Context.POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(android.os.PowerManager.FULL_WAKE_LOCK | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG + ":DemoLock");
                wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);
            }
        } catch (Exception e) {
            Log.e(TAG, "WakeLock acquisition failed", e);
        }

        AppConfig.init(this);
        BackendConnectionChecker.check();
        repository = new TemiRepository(this);
        voiceInputManager = new VoiceInputManager(this);
        // 라즈베리파이 센서 이벤트(KF_BE 경유)를 폴링해 로봇 동작으로 변환한다.
        RobotActionManager actionManager = new RobotActionManager(repository);
        actionManager.setOnFaceChangeListener(faceType -> 
            uiHandler.post(() -> {
                int resId = R.drawable.face_peaceful;
                if ("EXCITED".equals(faceType)) resId = R.drawable.face_excited;
                else if ("SADNESS".equals(faceType)) resId = R.drawable.face_sadness;
                else if ("ANGER".equals(faceType)) resId = R.drawable.face_anger;
                else if ("JOY".equals(faceType)) resId = R.drawable.face_joy;
                setFace(resId);
            })
        );
        sensorEventPoller = new SensorEventPoller(actionManager);
        // 시연 중 돌발상황을 Temi 내장 기능으로 자동 복구(내장 사람감지 백업, 들림/끌림 정지, 배터리 안내).
        resilienceManager = new RobotResilienceManager(actionManager);
        // 테미 매핑(자기 위치 인식) 정보를 KF_BE로 보고 → 대시보드(KF_WEB)가 실시간 위치 표시.
        positionReporter = new RobotPositionReporter();

        robot = Robot.getInstance();
        robot.addOnRobotReadyListener(this);
        robot.addOnRequestPermissionResultListener(this);

        faceImage = findViewById(R.id.image_face);
        faceImage.setOnClickListener(v -> {
            if (state == State.IDLE) {
                voiceInputManager.stopListening();
                startConversation(null);
            }
        });
        
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

        // 데모용 비밀 트리거 6: 뒤로가기 버튼 길게 누르기 -> 화재경보 모의 발생
        backButton.setOnLongClickListener(v -> {
            Log.d(TAG, "Secret Trigger: Mock FIRE event");
            actionManager.onSensorEvent("FIRE_DETECTED", null);
            return true;
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
        // 시스템 워치독 시작 (볼륨/키오스크/상단바 주기 원복). onPause에서 해제된다.
        uiHandler.removeCallbacks(systemWatchdog);
        uiHandler.postDelayed(systemWatchdog, SYSTEM_WATCHDOG_INTERVAL_MS);
        enterIdle();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorEventPoller.stop();
        voiceInputManager.stopListening();
        uiHandler.removeCallbacks(systemWatchdog);
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
            // 테미 위치 보고 시작(매핑된 상태에서 위치가 바뀔 때마다 BE로 전송).
            positionReporter.register();
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
                // 대기화면에서는 오인식/주변 소음 텍스트로 안내문구를 덮지 않는다(화면 깔끔 유지).
            }

            @Override
            public void onResult(String text) {
                // 호출어("친구야")가 들렸을 때만 화면을 전환하고,
                // 그 외 인식 결과는 무시해 대기화면("친구야 하고 부르거나...")을 안정적으로 유지한다.
                if (!WakeWordMatcher.containsWakeWord(text)) {
                    return;
                }
                voiceInputManager.stopListening();
                startConversation(WakeWordMatcher.textAfterWakeWord(text));
            }

            @Override
            public void onError(String message) {
                // STT가 반복 실패하면(마이크/네트워크 문제) 터치를 안내한다. (VoiceInputManager가 잠시 후 자동 복구)
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

        // 데모 스크립트: "고마워/감사" → 작별 인사 후 대기 복귀
        if (isThanksPhrase(text)) {
            setFace(R.drawable.face_joy);
            speaker.speak("내가 더 고맙지! 늘 사랑해");
            enterIdle();
            return;
        }

        if (isEndPhrase(text)) {
            speaker.speak(getString(R.string.home_bye));
            enterIdle();
            return;
        }

        // 데모 스크립트: 정체/이름/엄마/미세먼지는 AI 없이 고정 답변으로 대화를 이어간다.
        String scripted = ScriptedResponder.answer(text);
        if (scripted != null) {
            lastRouteTime = 0; // 연속 대화 허용
            setFace(R.drawable.face_joy);
            showAnswer(scripted);
            speakThenListen(scripted);
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
            case REGISTER:
                setFace(R.drawable.face_joy);
                speaker.speak("회원으로 등록하면 퀴즈를 풀고 포인트를 모아 선물을 받을 수 있어! 사진도 찍고 나만의 프로필도 만들 수 있어. 지금 바로 등록해줄게!");
                statusText.setText("회원 등록을 시작할게요!");
                Intent registerIntent = new Intent(this, MembershipCardActivity.class);
                registerIntent.putExtra(MembershipCardActivity.EXTRA_REGISTER_MODE, true);
                startActivity(registerIntent);
                break;
            case MEMBERSHIP:
                setFace(R.drawable.face_joy);
                speaker.speak(getString(R.string.home_routing_membership));
                statusText.setText(R.string.home_routing_membership);
                startActivity(new Intent(this, MembershipCardActivity.class));
                break;
            case LOCATION:
                setFace(R.drawable.face_excited);
                String guide = locationGuide(text);
                showAnswer(guide);
                speakThenListen(guide);
                break;
            case PHOTO:
                setFace(R.drawable.face_excited);
                speaker.speak("좋아요! 멋진 포즈를 취해봐요. 사진 찍을게요!");
                statusText.setText("사진 촬영 준비 중...");
                // TODO: 실제 카메라 구동 및 uploadAndSavePhoto 호출 로직 구현
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

    /** "고마워/감사" 류는 작별 인사("늘 사랑해")로 마무리한다. */
    private boolean isThanksPhrase(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", "");
        return normalized.contains("고마") || normalized.contains("감사");
    }

    /** 놀이존 위치 안내(자율주행 OFF, 음성 안내만). */
    private String locationGuide(String text) {
        String t = text == null ? "" : text.replaceAll("\\s+", "");
        if (t.contains("미끄럼틀")) {
            return "미끄럼틀은 D존에 있어! 같이 가볼까? 나를 따라와! "
                    + "그리고 미끄럼틀은 한 번에 한 명씩 타야 하고, 친구를 밀면 안 돼!";
        }
        if (t.contains("볼풀")) {
            return "볼풀은 B존에 있어! 뛰지 말고 천천히 가자!";
        }
        if (t.contains("화장실")) {
            return "화장실은 입구 옆에 있어! 보호자랑 같이 가면 돼!";
        }
        if (t.contains("트램폴린")) {
            return "트램폴린은 C존에 있어! 같이 가볼까? 나를 따라와!";
        }
        return "그건 어디 있는지 같이 찾아볼까? 직원 선생님께 여쭤보면 정확히 알려줄 거야!";
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
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception ignored) {}
        
        try {
            if (resilienceManager != null) {
                resilienceManager.unregister();
            }
            if (positionReporter != null) {
                positionReporter.unregister();
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
        if (sensorEventPoller != null) {
            sensorEventPoller.stop();
        }
        super.onDestroy();
    }
}
