package com.kidsFriend;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import eightbitlab.com.blurview.BlurView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.kidsFriend.domain.call.ui.FriendSelectionActivity;
import com.kidsFriend.domain.objectgame.service.ObjectGameActivity;
import com.kidsFriend.domain.greeting.service.IntentRouter;
import com.kidsFriend.domain.dust.service.DustVideoActivity;
import com.kidsFriend.domain.guide.service.GuidePlayActivity;
import com.kidsFriend.domain.photo.service.PhotoPlayActivity;
import com.kidsFriend.domain.quiz.service.QuizIntroActivity;
import com.kidsFriend.domain.sensor.service.SensorWebSocketClient;
import com.kidsFriend.global.config.ApiConfig;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;
import com.robotemi.sdk.listeners.OnRobotReadyListener;
import com.kidsFriend.domain.chat.response.QuestionResponse;
import com.kidsFriend.global.repository.RepositoryCallback;
import com.kidsFriend.global.repository.TemiRepository;
import com.kidsFriend.global.ui.FullscreenHelper;
import com.kidsFriend.global.ui.GlassBlur;
import com.kidsFriend.global.ui.KidAnimator;
import com.kidsFriend.global.ui.MotionConstants;
import com.kidsFriend.global.ui.RocketMenuManager;
import com.kidsFriend.global.ui.MotionManager;

/**
 * 메인 화면.
 * 
 * - 버튼 기반의 명확한 기능 선택 구조.
 * - AI 대화, 퀴즈, 위치 안내, 공기질 확인 등 제공.
 */
public class MainActivity extends AppCompatActivity 
        implements OnRobotReadyListener, Robot.AsrListener {
    
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1000;
    
    private Robot robot;
    private TemiRepository repository;
    private TextView statusText;
    private ImageView faceImage;
    private RocketMenuManager rocketMenuManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        FullscreenHelper.setFullscreen(this);

        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        robot = Robot.getInstance();
        repository = new TemiRepository(this);

        // 센서 데이터 수신 시작
        SensorWebSocketClient.start(ApiConfig.DEFAULT_API_BASE_URL);

        // 권한 체크 및 요청
        checkAndRequestPermissions();

        statusText = findViewById(R.id.text_home_status);
        faceImage = findViewById(R.id.image_face);
        rocketMenuManager = new RocketMenuManager(findViewById(R.id.rocket_menu_root));

        // 화면 어디든 터치 시 메뉴 실행/닫기
        findViewById(R.id.root_main).setOnClickListener(v -> toggleRocketMenu());
        faceImage.setOnClickListener(v -> toggleRocketMenu());
        findViewById(R.id.rocket_menu_root).setOnClickListener(v -> toggleRocketMenu());

        // 메뉴 패널에 글래스 블러 적용
        android.view.ViewGroup rootMain = findViewById(R.id.root_main);
        GlassBlur.apply(this, findViewById(R.id.rocket_menu_root), rootMain, 18f, R.color.glass_tint);

        // 버튼 이벤트 설정
        setupMenuButtons();

        // "다 말했어 !" 버튼 설정
        findViewById(R.id.button_complete).setOnClickListener(v -> finishChat());

        statusText.setVisibility(View.VISIBLE);
        resetUi();
    }

    private void toggleRocketMenu() {
        if (rocketMenuManager.isMenuOpen()) {
            resetUi();
        } else {
            startRocketMenu();
        }
    }

    private void startRocketMenu() {
        if (rocketMenuManager.isMenuOpen()) return;
        
        statusText.setVisibility(View.GONE);
        rocketMenuManager.openMenu();
        speakAndShow("어떤 놀이를 해볼까? 하고 싶은 놀이를 골라줘!");
    }

    /** UI를 초기 상태로 되돌림 */
    private void resetUi() {
        if (rocketMenuManager != null) rocketMenuManager.closeMenu(null);
        findViewById(R.id.layout_action_buttons).setVisibility(View.GONE);
        statusText.setText("터치해보세요 !");
        statusText.setVisibility(View.VISIBLE);
        faceImage.setImageResource(R.drawable.face_peaceful);
    }

    private void setupMenuButtons() {
        // 사진 찍기 놀이
        findViewById(R.id.btn_menu_photo).setOnClickListener(v -> {
            rocketMenuManager.closeMenu(v);
            v.postDelayed(() -> {
                speakAndShow("멋진 포즈를 취해봐! 사진을 찍어줄게.");
                startActivity(new Intent(this, PhotoPlayActivity.class));
                overridePendingTransition(R.anim.combined_enter, 0);
            }, MotionConstants.COLLAPSE_DURATION + 100);
        });

        // 게임 하기 (퀴즈 놀이)
        findViewById(R.id.btn_menu_game).setOnClickListener(v -> {
            rocketMenuManager.closeMenu(v);
            v.postDelayed(() -> {
                startActivity(new Intent(this, QuizIntroActivity.class));
                overridePendingTransition(R.anim.combined_enter, 0);
            }, MotionConstants.COLLAPSE_DURATION + 100);
        });

        // 노래 듣기 (미세먼지 놀이/영상)
        findViewById(R.id.btn_menu_music).setOnClickListener(v -> {
            rocketMenuManager.closeMenu(v);
            v.postDelayed(() -> {
                startActivity(new Intent(this, DustVideoActivity.class));
                overridePendingTransition(R.anim.combined_enter, 0);
            }, MotionConstants.COLLAPSE_DURATION + 100);
        });

        // 춤 추기 (안내 놀이/지도)
        findViewById(R.id.btn_menu_dance).setOnClickListener(v -> {
            rocketMenuManager.closeMenu(v);
            v.postDelayed(() -> {
                speakAndShow("어디로 갈까? 지도를 보여줄게!");
                startActivity(new Intent(this, GuidePlayActivity.class));
                overridePendingTransition(R.anim.combined_enter, 0);
            }, MotionConstants.COLLAPSE_DURATION + 100);
        });

        // 친구들과 카톡하기 -> 친구 선택 화면 (음성 통화 대신 텍스트 채팅)
        findViewById(R.id.btn_menu_ai_chat).setOnClickListener(v -> {
            rocketMenuManager.closeMenu(v);
            v.postDelayed(() -> {
                startActivity(new Intent(this, FriendSelectionActivity.class));
                overridePendingTransition(R.anim.combined_enter, 0);
            }, MotionConstants.COLLAPSE_DURATION + 100);
        });

        // 물건 맞추기 놀이 (비전 카메라 — 파이썬 직접 연결)
        findViewById(R.id.btn_menu_object_game).setOnClickListener(v -> {
            rocketMenuManager.closeMenu(v);
            v.postDelayed(() -> {
                startActivity(new Intent(this, ObjectGameActivity.class));
                overridePendingTransition(R.anim.combined_enter, 0);
            }, MotionConstants.COLLAPSE_DURATION + 100);
        });

        // 서버 확인 버튼 (오퍼레이터용)
        findViewById(R.id.button_operator_menu).setOnClickListener(v -> checkServerHealth());
    }

    private void checkServerHealth() {
        repository.checkHealth(new RepositoryCallback<java.util.Map<String, String>>() {
            @Override
            public void onSuccess(java.util.Map<String, String> data) {
                Toast.makeText(MainActivity.this, "서버 연결 성공: " + data.toString(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MainActivity.this, "서버 연결 실패: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        robot.addOnRobotReadyListener(this);
        robot.addAsrListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        robot.removeOnRobotReadyListener(this);
        robot.removeAsrListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SensorWebSocketClient.stop();
    }

    @Override
    public void onRobotReady(boolean isReady) {
        if (isReady) {
            robot.hideTopBar();
            // "헤이 테미" 및 내장 호출어 완전히 비활성화 (인자 true = 비활성화)
            robot.toggleWakeup(true); 
            robot.setKioskModeOn(true);
            
            // 시연 안정성을 위해 내장된 다른 호출 감지 기능들도 꺼둡니다.
            robot.setDetectionModeOn(false);
        }
    }

    private void checkAndRequestPermissions() {
        String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA};
        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false; break;
            }
        }
        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    /** 대화 종료 처리 */
    private void finishChat() {
        findViewById(R.id.layout_action_buttons).setVisibility(View.GONE);
        statusText.setVisibility(View.GONE);
        faceImage.setImageResource(R.drawable.face_peaceful);
        robot.speak(TtsRequest.create("응, 재밌었어! 또 대화하고 싶으면 불러줘.", false));
        resetUi();
    }

    @Override
    public void onAsrResult(@NonNull String asrResult) {
        if (TextUtils.isEmpty(asrResult)) {
            // 아무 말도 없을 경우 "다 말했어 !" 버튼은 유지하되 표정만 복구
            faceImage.setImageResource(R.drawable.face_peaceful);
            statusText.setText("듣고 있어...");
            return;
        }
        Log.d(TAG, "ASR Result: " + asrResult);
        
        // 사용자가 말을 마치면 생각하는 표정으로 바꿉니다
        faceImage.setImageResource(R.drawable.face_peaceful);
        statusText.setText("생각 중이야...");
        
        IntentRouter.Intent intent = IntentRouter.route(asrResult);
        switch (intent) {
            case IDENTITY: speakAndShow("나는 너의 인공지능 친구, 테미야!"); break;
            case AIR_QUALITY: handleAirQualityIntent(); break;
            case QUIZ: 
                faceImage.setImageResource(R.drawable.face_joy);
                speakAndShow("재미있는 퀴즈를 시작해볼까?"); 
                break;
            case ENDING: 
                finishChat();
                break;
            case CHAT:
            default: processAiChat(asrResult); break;
        }
    }

    private void speakAndShow(String text) {
        statusText.setText(text);
        statusText.setVisibility(View.VISIBLE);
        
        // 말할 때 표정을 웃는 표정으로 바꿉니다
        faceImage.setImageResource(R.drawable.face_joy);
        
        // 테미가 말을 하고 다시 사용자 질문을 기다리게 합니다 (연속 대화 느낌)
        // 거대한 검은 자막창을 끄기 위해 TtsRequest.create(text, false) 사용
        robot.speak(TtsRequest.create(text, false));
        robot.askQuestion(""); // 자막 없이 귀만 열기
        
        // 7초 후에 답변 텍스트를 자동으로 숨김
        statusText.postDelayed(() -> {
            statusText.setVisibility(View.GONE);
            faceImage.setImageResource(R.drawable.face_peaceful);
        }, 7000);
    }

    private void handleAirQualityIntent() {
        repository.getAirQuality(new RepositoryCallback<String>() {
            @Override
            public void onSuccess(String grade) {
                String response = "지금 공기질은 '" + grade + "' 상태야. ";
                if ("좋음".equals(grade)) response += "밖에서 뛰어놀기 딱 좋은 날씨네!";
                else if ("나쁨".equals(grade)) response += "마스크를 꼭 쓰는 게 좋겠어.";
                else response += "평범한 날씨야.";
                speakAndShow(response);
            }
            @Override
            public void onError(String message) {
                speakAndShow("미안해, 지금 공기질 정보를 가져올 수 없어.");
            }
        });
    }

    private void processAiChat(String asrResult) {
        statusText.setText("잠시만 기다려줘...");
        statusText.setVisibility(View.VISIBLE);
        faceImage.setImageResource(R.drawable.face_peaceful);

        repository.askVoiceQuestion(asrResult, asrResult, new RepositoryCallback<QuestionResponse>() {
            @Override
            public void onSuccess(QuestionResponse data) {
                speakAndShow(data.answer);
            }
            @Override
            public void onError(String message) {
                // 에러 발생 시 시무룩한 표정
                faceImage.setImageResource(R.drawable.face_sulky);
                robot.speak(TtsRequest.create("미안해, 서버와 연결이 안 됐어. 다시 한 번 말해줄래?", false));
                statusText.setText("서버 연결 실패 (502)");
                
                statusText.postDelayed(() -> {
                    faceImage.setImageResource(R.drawable.face_peaceful);
                    statusText.setVisibility(View.GONE);
                }, 3000);
            }
        });
    }
}
