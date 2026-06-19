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
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.kidsFriend.domain.greeting.service.IntentRouter;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.constants.Gender;
import com.robotemi.sdk.listeners.OnRobotReadyListener;
import com.robotemi.sdk.permission.OnRequestPermissionResultListener;
import com.robotemi.sdk.permission.Permission;
import com.robotemi.sdk.voice.model.TtsVoice;
import java.util.Collections;

import com.kidsFriend.domain.chat.response.QuestionResponse;
import com.kidsFriend.domain.quiz.service.QuizActivity;
import com.kidsFriend.domain.sensor.service.RobotActionManager;
import com.kidsFriend.domain.sensor.service.RobotResilienceManager;
import com.kidsFriend.global.config.AppConfig;
import com.kidsFriend.global.config.BackendConnectionChecker;
import com.kidsFriend.global.debug.ApiTestActivity;
import com.kidsFriend.global.repository.RepositoryCallback;
import com.kidsFriend.global.repository.TemiRepository;
import com.kidsFriend.global.voice.SpeechCorrector;
import com.kidsFriend.global.voice.TemiSpeechSpeaker;
import com.kidsFriend.global.voice.VoiceInputManager;
import com.kidsFriend.global.voice.WakeWordMatcher;

/**
 * 통합 홈 화면 / 대화 엔진.
 *
 * <p>평소엔 얼굴을 띄우고 "친구야" 호출 또는 화면 터치를 대기한다. 호출/터치 시 대화를 시작하고,
 * 들은 말의 의도를 {@link #handleUtterance(String)} 디스패처가 판단해 시나리오 핸들러로 위임한다.</p>
 *
 * <p>코드는 데모 대본 순서로 읽히도록 구역을 나눴다:
 * <pre>
 *   1) Android 생명주기        onCreate ~ onDestroy
 *   2) 테미 초기화/권한        onRobotReady, 목소리, 오디오 권한
 *   3) 대화 엔진(공통 진입)    enterIdle, startConversation, listenInConversation, handleUtterance
 *   4) 시나리오 핸들러(대본순)  ① 인사 ② 회원등록 ③ 놀이존 ④ 퀴즈 ⑤ 자유질문 ⑥ 미세먼지 ⑦ 엔딩 ⑧ 화재
 *   5) 공통 helper            화면/발화/워치독
 * </pre>
 * </p>
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

    // 연속 화면 전환(멀티 런치) 방지용 마지막 라우팅 시각.
    private long lastRouteTime = 0;

    private TemiRepository repository;
    private VoiceInputManager voiceInputManager;
    private RobotActionManager actionManager;
    private RobotResilienceManager resilienceManager;
    private Robot robot;
    private android.os.PowerManager.WakeLock wakeLock;
    private ImageView faceImage;
    private TextView statusText;
    private TextView answerText;
    private LinearLayout subtitleLayout;
    private TextView subtitleText;

    // =========================================================================
    // 1) Android 생명주기
    // =========================================================================

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
        // 센서 이벤트를 로봇 동작/표정으로 변환한다(시연에서는 화면 비밀 트리거로 발생시킨다).
        actionManager = new RobotActionManager();
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
        // 시연 중 돌발상황을 Temi 내장 기능으로 자동 복구(내장 사람감지 백업, 들림/끌림 정지, 배터리 안내).
        resilienceManager = new RobotResilienceManager(actionManager);

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
        subtitleLayout = findViewById(R.id.layout_subtitle);
        subtitleText = findViewById(R.id.text_subtitle);
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

    /**
     * ApiTestActivity 등에서 FLAG_ACTIVITY_REORDER_TO_FRONT 로 재진입할 때 호출된다.
     * setIntent()로 교체하지 않으면 getIntent()가 최초 실행 Intent를 반환해
     * injected_stt / injected_sensor extra를 읽지 못하고 enterIdle()로 빠진다.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);  // getIntent()가 새 Intent를 반환하도록 교체
        Log.d(TAG, "onNewIntent: " + intent);
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
        // 시스템 워치독 시작 (볼륨/키오스크/상단바 주기 원복). onPause에서 해제된다.
        uiHandler.removeCallbacks(systemWatchdog);
        uiHandler.postDelayed(systemWatchdog, SYSTEM_WATCHDOG_INTERVAL_MS);

        // 운영자 페이지에서 주입된 STT 텍스트가 있는지 확인
        String injected = getIntent().getStringExtra("injected_stt");
        String sensor = getIntent().getStringExtra("injected_sensor");

        if (!TextUtils.isEmpty(sensor)) {
            getIntent().removeExtra("injected_sensor");
            Log.d(TAG, "Injected Sensor event from operator: " + sensor);
            actionManager.onSensorEvent(sensor, null);
        } else if (!TextUtils.isEmpty(injected)) {
            getIntent().removeExtra("injected_stt");
            Log.d(TAG, "Injected STT received from operator: " + injected);
            startConversation(injected);
        } else {
            enterIdle();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        voiceInputManager.stopListening();
        uiHandler.removeCallbacks(systemWatchdog);
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

    // =========================================================================
    // 2) 테미 초기화 / 권한
    // =========================================================================

    @Override
    public void onRobotReady(boolean isReady) {
        if (isReady) {
            robot.hideTopBar();
            // 사용자의 요청으로 마이크 음소거 및 프라이버시 모드를 해제합니다.
            // 1. 호출어 활성화 (Hey temi 사용 가능)
            robot.toggleWakeup(false);
            // 2. 프라이버시 모드 해제 (마이크 하드웨어 차단 해제)
            robot.setPrivacyMode(false);
            
            Log.d(TAG, "Temi Wakeup Mode Enabled & Privacy Mode Disabled");
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

    private boolean ensureAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }

        // 영구 거부 판단:
        //   - shouldShowRequestPermissionRationale()는 "한 번 이상 거부했을 때만" true를 반환한다.
        //   - 즉 false이면서 권한이 없으면 ①최초 실행 또는 ②영구 거부 둘 중 하나다.
        //   - SharedPreferences에 "이전에 요청한 적 있음"을 기록해 ①②를 구분한다.
        boolean askedBefore = getSharedPreferences("kf_pref", MODE_PRIVATE)
                .getBoolean("audio_permission_asked", false);
        boolean canShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.RECORD_AUDIO);

        if (askedBefore && !canShowRationale) {
            // 영구 거부 상태 → 시스템 다이얼로그가 더 이상 뜨지 않음. 앱 설정으로 안내.
            statusText.setText("마이크 권한이 차단되었습니다. 설정에서 허용해 주세요.");
            new android.app.AlertDialog.Builder(this)
                    .setTitle("마이크 권한 필요")
                    .setMessage("음성 인식을 위해 마이크 권한이 필요합니다.\n"
                            + "설정 → 앱 → Kids_Friend → 권한에서 마이크를 허용해 주세요.")
                    .setCancelable(false)
                    .setPositiveButton("설정으로 이동", (d, w) -> {
                        android.content.Intent si = new android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        si.setData(android.net.Uri.parse("package:" + getPackageName()));
                        startActivity(si);
                    })
                    .setNegativeButton("나중에", null)
                    .show();
            return false;
        }

        // 최초 요청 또는 일반 거부(한 번 더 물어볼 수 있는 상태)
        getSharedPreferences("kf_pref", MODE_PRIVATE).edit()
                .putBoolean("audio_permission_asked", true).apply();
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

    // =========================================================================
    // 3) 대화 엔진 (공통 진입 + 의도 디스패처)
    // =========================================================================

    /** 대기 상태: 얼굴 + "친구야 불러줘" 안내, 호출어 대기. */
    private void enterIdle() {
        disarmConversationWatchdog();
        if (!ensureAudioPermission()) {
            return;
        }
        state = State.IDLE;
        setFace(R.drawable.face_peaceful);
        statusText.setVisibility(View.VISIBLE); // 다시 안내 문구 표시
        statusText.setText(R.string.home_idle_hint);
        answerText.setVisibility(View.GONE);
        subtitleLayout.setVisibility(View.GONE);

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
        subtitleLayout.setVisibility(View.GONE); // 테미가 말을 시작하므로 자막 숨김
        logScenario(1, "입장·인사", "성공");

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
        voiceInputManager.startSingleListening(new VoiceInputManager.Callback() {
            @Override
            public void onReady() {
                runOnUiThread(() -> {
                    statusText.setVisibility(View.GONE);
                    subtitleLayout.setVisibility(View.VISIBLE);
                    subtitleText.setText("");
                });
            }

            @Override
            public void onPartialResult(String text) {
                if (!TextUtils.isEmpty(text)) {
                    runOnUiThread(() -> {
                        subtitleText.setText(getString(R.string.voice_caption_format, text));
                    });
                }
            }

            @Override
            public void onResult(String text) {
                runOnUiThread(() -> {
                    if (!TextUtils.isEmpty(text)) {
                        subtitleText.setText(getString(R.string.voice_caption_format, text));
                    }
                    handleUtterance(text);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    subtitleLayout.setVisibility(View.GONE);
                    statusText.setVisibility(View.VISIBLE);
                    enterIdle();
                });
            }
        });
    }

    /**
     * 들은 말의 의도를 판단해 시나리오 핸들러로 위임한다(스프링의 컨트롤러→서비스 위임과 동일한 구조).
     * 가로채기 우선순위가 중요하다: 작별 → 미세먼지 → 키워드 의도분기 순.
     */
    private void handleUtterance(String text) {
        subtitleLayout.setVisibility(View.GONE);
        statusText.setVisibility(View.VISIBLE);
        if (System.currentTimeMillis() - lastRouteTime < 2000) {
            Log.w(TAG, "handleUtterance: Ignoring rapid utterance to prevent multi-launch.");
            return;
        }
        lastRouteTime = System.currentTimeMillis();

        // 대본 순서대로 분기: 엔딩 → 미세먼지 → 회원등록 → 정체 → 놀이존 → 퀴즈 → 자유질문(AI).
        switch (IntentRouter.route(text)) {
            case ENDING:
                handleEnding();
                break;
            case AIR_QUALITY:
                handleAirQuality();
                break;
            case MEMBERSHIP:
                handleMembership();
                break;
            case IDENTITY:
                handleIdentity();
                break;
            case LOCATION:
                handleLocation(text);
                break;
            case QUIZ:
                handleQuiz();
                break;
            case FREE_QUESTION:
            case CHAT:
            default:
                handleChat(text);
                break;
        }
    }

    // =========================================================================
    // 4) 시나리오 핸들러 (데모 대본 순서)
    // =========================================================================

    // ── ① 입장·인사 ──────────────────────────────────────────────────────────
    // 인사("친구야" 호출 → "어떤 도움이 필요하니?")는 startConversation()의 home_wake_detected
    // 발화로 처리된다.

    private void handleIdentity() {
        setFace(R.drawable.face_joy);
        String msg = "안녕! 나는 키즈카페 친구 테미야. 궁금한것이 있으면 언제든 물어봐!";
        showAnswer(msg);
        speakThenListen(msg);
        logScenario(1, "정체 확인", "성공");
    }

    // ── ② 회원등록 (Mock) ─────────────────────────────────────────────────────

    private void handleMembership() {
        setFace(R.drawable.face_excited);
        // 회원등록 시나리오는 단계가 많으므로 데모용으로 최종 결과를 바로 보여준다.
        String msg = "반가워, 재석! 이제 회원 등록이 완료되었어. 앞으로 재밌게 놀아보자! 사랑해";
        String cardMock = "\n\n[회원 카드 생성]\n👤 이름: 박재석\n⭐ 포인트: 0점\n✨ 프로필 사진 등록 완료";
        showAnswer(msg + cardMock);
        speakThenListen(msg);
        logScenario(2, "회원등록(Mock)", "성공");
    }

    // ── ③ 놀이존 위치 안내 ────────────────────────────────────────────────────

    /** 놀이존 위치 안내(자율주행 OFF, 음성 안내만). */
    private void handleLocation(String text) {
        setFace(R.drawable.face_excited);
        String guide = locationGuide(text);
        showAnswer(guide);
        speakThenListen(guide);
        logScenario(3, "놀이존 안내", "성공");
    }

    private String locationGuide(String text) {
        // 장소명 오인식("미끄럼들"→"미끄럼틀")을 표준어로 스냅한 뒤 매칭한다.
        String t = SpeechCorrector.correct(text).replaceAll("\\s+", "");
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

    // ── ④ 퀴즈 ───────────────────────────────────────────────────────────────

    /** 퀴즈 화면으로 전환. */
    private void handleQuiz() {
        setFace(R.drawable.face_joy);
        speaker.speak(getString(R.string.home_routing_quiz));
        statusText.setText(R.string.home_routing_quiz);
        startActivity(new Intent(this, QuizActivity.class));
        logScenario(4, "퀴즈", "성공");
    }

    // ── ⑤ 자유질문 (AI) ──────────────────────────────────────────────────────

    /** AI에게 묻고 답을 화면+음성으로 보여준 뒤, 다시 듣기로 대화를 이어갑니다. */
    private void handleChat(String question) {
        lastRouteTime = 0; // 채팅은 딜레이 면제 (연속 대화 허용)
        armConversationWatchdog();
        setFace(R.drawable.face_peaceful);
        statusText.setText(R.string.home_thinking);
        repository.askQuestion(question, new RepositoryCallback<QuestionResponse>() {
            @Override
            public void onSuccess(QuestionResponse data) {
                logScenario(5, "자유질문(AI)", "성공");
                setFace(R.drawable.face_joy);
                showAnswer(data.answer);
                speakThenListen(data.answer);
            }

            @Override
            public void onError(String message) {
                // 네트워크/ngrok/AI 실패 시: 원시 에러 대신 친근한 안내로 대화를 이어간다.
                logScenario(5, "자유질문(AI)", "실패: " + message);
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

    // ── ⑥ 미세먼지(공기질) ────────────────────────────────────────────────────

    /** 미세먼지 질문: 센서 API 등급을 받아 안내(실패/값 없음 시 "보통"). */
    private void handleAirQuality() {
        armConversationWatchdog();
        setFace(R.drawable.face_peaceful);
        statusText.setText(R.string.home_thinking);
        repository.getAirQuality(new RepositoryCallback<String>() {
            @Override
            public void onSuccess(String grade) {
                logScenario(6, "미세먼지", "성공(" + grade + ")");
                setFace(R.drawable.face_joy);
                String answer = "지금 미세먼지 상태는 " + grade + "이야!";
                answer += "나쁨".equals(grade) ? " 오늘은 실내에서 노는 게 좋겠어!" : " 신나게 놀아도 괜찮아!";
                showAnswer(answer);
                speakThenListen(answer);
            }

            @Override
            public void onError(String message) {
                logScenario(6, "미세먼지", "실패→보통 폴백: " + message);
                String answer = "지금 미세먼지 상태는 보통이야! 신나게 놀아도 괜찮아!";
                showAnswer(answer);
                speakThenListen(answer);
            }
        });
    }

    // ── ⑦ 엔딩 (작별 인사) ────────────────────────────────────────────────────

    /** 대화를 마무리하고 작별 인사를 합니다. */
    private void handleEnding() {
        setFace(R.drawable.face_joy);
        String msg = "늘 사랑해 !";
        showAnswer(msg);
        speaker.speak(msg);
        logScenario(7, "엔딩", "성공");
        enterIdle();
    }

    // ── ⑧ 화재경보 ───────────────────────────────────────────────────────────
    // 화재는 음성 의도가 아니라 센서 이벤트(FIRE_DETECTED)로 들어와 RobotActionManager.handleFireAlarm()이
    // 처리한다. 이 화면에서는 onCreate의 뒤로가기 버튼 롱클릭(비밀 트리거 6)으로만 모의 발생시킨다.

    // =========================================================================
    // 5) 공통 helper (화면 / 발화 / 워치독)
    // =========================================================================

    private void showAnswer(String text) {
        answerText.setText(text);
        answerText.setVisibility(View.VISIBLE);
    }

    /** 발화를 시작하고, 발화가 끝날 무렵 다시 듣기를 시작합니다(테미가 자기 음성을 인식하는 문제 방지). */
    private void speakThenListen(String text) {
        armConversationWatchdog();
        subtitleLayout.setVisibility(View.GONE); // 발화 시작 시 자막 숨김
        speaker.speak(text);
        uiHandler.removeCallbacks(listenRunnable);
        // 발화 길이를 글자 수로 추정해 그만큼 기다린 뒤 듣기 시작
        long estimatedMs = Math.min(12000L, Math.max(2500L, text.length() * 150L));
        uiHandler.postDelayed(listenRunnable, estimatedMs);
    }

    private void setFace(int drawableRes) {
        faceImage.setImageResource(drawableRes);
    }

    /**
     * 데모 대본 순서대로 n번째 시나리오의 성공 여부를 기록한다(스프링 log.info에 대응하는 Android Log.i).
     * 예: {@code I/MainActivity: [시나리오 4] 퀴즈 → 성공}
     */
    private void logScenario(int no, String name, String result) {
        Log.i(TAG, "[시나리오 " + no + "] " + name + " → " + result);
    }

    /** 대화 한 단계가 시작될 때마다 워치독 타이머를 다시 건다(정상 진행 중엔 발동하지 않음). */
    private void armConversationWatchdog() {
        uiHandler.removeCallbacks(conversationWatchdog);
        uiHandler.postDelayed(conversationWatchdog, CONVERSATION_TIMEOUT_MS);
    }

    private void disarmConversationWatchdog() {
        uiHandler.removeCallbacks(conversationWatchdog);
    }
}