package com.kidsFriend.domain.objectgame.service;

import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.kidsFriend.R;
import com.kidsFriend.global.ui.FullscreenHelper;
import com.kidsFriend.global.ui.GlassBlur;
import com.kidsFriend.global.ui.KidAnimator;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;

import java.util.Random;

import eightbitlab.com.blurview.BlurView;

/**
 * 물건 맞추기 놀이 - 🌎 분리수거 영웅 시나리오 포함
 */
public class ObjectGameActivity extends AppCompatActivity {

    private static final String TAG = "ObjectGameActivity";
    private static final double CONFIDENCE_THRESHOLD = 0.5;
    /** 같은 물체가 이 시간(ms) 이내에 연속으로 다시 감지되면 중복으로 보고 무시한다. */
    private static final long DEDUP_WINDOW_MS = 2500L;

    /** 기본 물체 목록 (전체 유지) */
    private static final String[][] OBJECTS = {
            {"oral_bottle",    "가그린",       "🧴"},
            {"color_pen",      "색펜",       "🖍️"},
            {"battery",        "건전지",     "🔋"},
            {"plastic_bottle", "페트병",     "🧴"},
            {"banana_peel",    "바나나 껍질", "🍌"},
            {"chopstick",      "젓가락",     "🥢"},
            {"plate",          "접시",       "🍽️"},
            {"cigarette_end",  "담배꽁초",   "🚬"},
    };

    /** 시나리오 단계: {label, ko_name, emoji, intro_text, success_text, points} */
    private static final String[][] SCENARIO = {
            {"plastic_bottle", "플라스틱 병", "🧴", "플라스틱 쓰레기가 길바닥에 버려져 있어!\n플라스틱 병을 찾아서 보여줘!", "와! 플라스틱 병을 찾았어!\n재활용 통으로 보내자!", "10"},
            {"battery",        "건전지",     "🔋", "이번엔 더 위험해!\n건전지는 일반 쓰레기통에 버리면 안 돼!\n건전지를 찾아줘!", "훌륭해!\n건전지는 전용 수거함으로!", "20"},
            {"banana_peel",    "바나나 껍질", "🍌", "음식물 쓰레기가 썩고 있어!\n바나나 껍질을 찾아줘!", "좋아!\n음식물 쓰레기통으로 이동 완료!", "20"},
            {"cigarette_end",  "담배꽁초",   "🚬", "공원을 깨끗하게 만들어야 해!\n담배꽁초를 찾아줘!", "대단해!\n공원이 깨끗해졌어!", "30"},
            {"oral_bottle",    "가그린",     "🧴", "입안을 헹구고 남은 가그린 병이 보여!\n깨끗하게 씻어서 재활용해야 해.\n가그린 병을 찾아줘!", "잘했어!\n플라스틱으로 분리배출 완료!", "10"},
            {"color_pen",      "색펜",       "🖍️", "다 쓴 색펜이 책상 위에 굴러다니고 있어!\n색펜을 찾아서 정리해줘!", "완벽해!\n필기구 수거함으로 쏙!", "15"},
            {"chopstick",      "젓가락",     "🥢", "한 번 쓰고 버려진 나무 젓가락이 있네!\n일반 쓰레기로 처리하게 젓가락을 찾아줘!", "체크 완료!\n일반 쓰레기봉투로 골인!", "10"},
            {"plate",          "접시",       "🍽️", "깨질 위험이 있는 접시가 방치되어 있어!\n안전하게 접시를 찾아줘!", "안전하게 수거 완료!\n조심히 처리하자!", "25"}
    };

    private final Random random = new Random();
    private final VisionSocketClient visionClient = new VisionSocketClient();

    private TextView targetEmoji;
    private TextView targetText;
    private TextView statusText;
    private TextView scoreText;
    private BlurView correctLayout;
    private BlurView wrongLayout;
    private BlurView endingLayout;
    private View introLayout;
    private VideoView introVideo;

    private String targetLabel;
    private String targetKo;
    private String successMsg;
    
    private int currentScenarioStage = -1; // -1: Intro, 0~4: Scenario, 5: Random Mode
    private int totalScore = 0;
    private int lastRandomIndex = -1;
    private boolean judged = false;
    private boolean connected = false;

    // 중복 감지 제거: 마지막으로 처리한 라벨과 그 시각
    private String lastLabel = null;
    private long lastLabelTime = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FullscreenHelper.setFullscreen(this);
        setContentView(R.layout.activity_object_game);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        targetEmoji = findViewById(R.id.text_obj_target_emoji);
        targetText = findViewById(R.id.text_obj_target);
        statusText = findViewById(R.id.text_obj_status);
        scoreText = findViewById(R.id.text_obj_score);
        correctLayout = findViewById(R.id.layout_obj_correct);
        wrongLayout = findViewById(R.id.layout_obj_wrong);
        endingLayout = findViewById(R.id.layout_obj_ending);
        introLayout = findViewById(R.id.layout_obj_intro);
        introVideo = findViewById(R.id.video_obj_intro);

        ViewGroup root = findViewById(R.id.root_object_game);
        GlassBlur.apply(this, correctLayout, root, 20f, R.color.glass_tint_strong);
        GlassBlur.apply(this, wrongLayout, root, 20f, R.color.glass_tint_strong);
        GlassBlur.apply(this, endingLayout, root, 20f, R.color.glass_tint_strong);
        GlassBlur.apply(this, findViewById(R.id.blur_obj_target), root, 18f, R.color.glass_tint_strong);

        findViewById(R.id.button_obj_back).setOnClickListener(v -> finish());
        findViewById(R.id.button_obj_settings).setOnClickListener(v -> showConnectionDialog());
        findViewById(R.id.button_obj_skip).setOnClickListener(v -> skipRound());

        KidAnimator.onClick(findViewById(R.id.button_obj_correct_next), v -> nextRound());
        findViewById(R.id.button_obj_correct_stop).setOnClickListener(v -> finish());
        KidAnimator.onClick(findViewById(R.id.button_obj_wrong_retry), v -> retryRound());
        KidAnimator.onClick(findViewById(R.id.button_obj_wrong_next), v -> nextRound());
        KidAnimator.onClick(findViewById(R.id.button_obj_ending_continue), v -> {
            hidePopups();
            loadRandomStage();
        });
        findViewById(R.id.button_obj_ending_stop).setOnClickListener(v -> finish());

        playIntroVideo();
    }

    private void playIntroVideo() {
        introLayout.setVisibility(View.VISIBLE);
        String path = "android.resource://" + getPackageName() + "/" + R.raw.trash_intro;
        introVideo.setVideoURI(Uri.parse(path));
        introVideo.setOnCompletionListener(mp -> {
            introLayout.setVisibility(View.GONE);
            startScenario();
        });
        introVideo.start();

        speak("안녕 친구들! 지구 마을에 쓰레기 괴물이 나타났어! 분리수거 영웅이 되어 지구를 구해줄래?");
    }

    private void startScenario() {
        currentScenarioStage = 0;
        totalScore = 0;
        updateScoreUI();
        loadScenarioStage(currentScenarioStage);
    }

    @Override
    protected void onStart() {
        super.onStart();
        connectVision();
    }

    @Override
    protected void onStop() {
        super.onStop();
        visionClient.close();
        connected = false;
    }

    private void connectVision() {
        String url = VisionConfig.getWsUrl(this);
        setStatus("카메라 연결 중...");
        visionClient.connect(url, new VisionSocketClient.Listener() {
            @Override
            public void onConnected() {
                connected = true;
                setStatus("카메라에 보여줘!");
                if (targetLabel != null) visionClient.sendRoundStart(targetLabel, targetKo);
            }

            @Override
            public void onDetection(@NonNull String label, double confidence) {
                handleDetection(label, confidence);
            }

            @Override
            public void onClosed(String reason) {
                connected = false;
                setStatus("카메라 연결 끊김 — '연결 설정' 확인");
            }
        });
    }

    private void handleDetection(String label, double confidence) {
        if (isFinishing() || isDestroyed()) return;
        if (confidence < CONFIDENCE_THRESHOLD) return;

        // 중복 제거: 파이썬이 같은 물체를 매 프레임 반복 전송하므로,
        // 같은 라벨이 DEDUP_WINDOW_MS 이내에 연속으로 오면 1건으로 본다.
        // (물체가 화면에 계속 있어도 stream이 윈도우를 갱신해 계속 무시됨 → 다음 라운드 자동 채점 방지)
        long now = System.currentTimeMillis();
        boolean duplicate = label.equalsIgnoreCase(lastLabel) && (now - lastLabelTime) < DEDUP_WINDOW_MS;
        lastLabel = label;
        lastLabelTime = now;
        if (duplicate) return;

        if (judged) return;
        judged = true;
        boolean correct = label.equalsIgnoreCase(targetLabel);
        if (correct) {
            handleCorrect();
        } else {
            handleWrong();
        }
    }

    private void handleCorrect() {
        int points = 10; // 기본 점수
        if (currentScenarioStage >= 0 && currentScenarioStage < SCENARIO.length) {
            points = Integer.parseInt(SCENARIO[currentScenarioStage][5]);
        }
        totalScore += points;
        updateScoreUI();

        correctLayout.setVisibility(View.VISIBLE);
        TextView title = findViewById(R.id.text_obj_correct_title);
        TextView sub = findViewById(R.id.text_obj_correct_sub);
        
        String msg = (currentScenarioStage >= 0 && currentScenarioStage < SCENARIO.length) ? successMsg : "와! 정답이야! 정말 잘했어!";
        if (sub != null) sub.setText(msg);

        title.post(() -> KidAnimator.success(title));
        speak(msg.replace("\n", " ") + " 정말 잘했어!");

        Button nextBtn = findViewById(R.id.button_obj_correct_next);
        if (currentScenarioStage == SCENARIO.length - 1) {
            nextBtn.setText("결과 보기");
        } else {
            nextBtn.setText("다음 물건");
        }
    }

    private void handleWrong() {
        wrongLayout.setVisibility(View.VISIBLE);
        View t = findViewById(R.id.text_obj_wrong_title);
        t.post(() -> KidAnimator.error(t));
        speak("음, 그건 아니야. 다시 한 번 보여줄래?");
    }

    private void nextRound() {
        hidePopups();
        if (currentScenarioStage >= 0 && currentScenarioStage < SCENARIO.length) {
            if (currentScenarioStage == SCENARIO.length - 1) {
                showEnding();
                currentScenarioStage = SCENARIO.length; // 시나리오 끝, 랜덤 모드 진입 가능 상태
                return;
            }
            currentScenarioStage++;
            loadScenarioStage(currentScenarioStage);
        } else {
            loadRandomStage();
        }
    }

    private void loadScenarioStage(int stageIdx) {
        judged = false;
        targetLabel = SCENARIO[stageIdx][0];
        targetKo = SCENARIO[stageIdx][1];
        String emoji = SCENARIO[stageIdx][2];
        String intro = SCENARIO[stageIdx][3];
        successMsg = SCENARIO[stageIdx][4];

        targetEmoji.setText(emoji);
        targetText.setText(intro);
        
        speak(intro.replace("\n", " "));
        if (connected) visionClient.sendRoundStart(targetLabel, targetKo);
    }

    private void loadRandomStage() {
        judged = false;
        int idx;
        do {
            idx = random.nextInt(OBJECTS.length);
        } while (OBJECTS.length > 1 && idx == lastRandomIndex);
        lastRandomIndex = idx;

        targetLabel = OBJECTS[idx][0];
        targetKo = OBJECTS[idx][1];
        String emoji = OBJECTS[idx][2];

        targetEmoji.setText(emoji);
        targetText.setText(targetKo + objParticle(targetKo) + " 보여줘!");

        speak(targetKo + objParticle(targetKo) + " 카메라에 보여줄래?");
        if (connected) visionClient.sendRoundStart(targetLabel, targetKo);
    }

    private void retryRound() {
        hidePopups();
        judged = false;
        if (connected) visionClient.sendRoundStart(targetLabel, targetKo);
    }

    private void skipRound() {
        speak("알겠어! 다음 물건으로 넘어가자.");
        nextRound();
    }

    private void showEnding() {
        String sub = "총 점수 " + totalScore + "점!\n오늘의 분리수거 영웅이야!\n지구를 구해줘서 고마워!";
        TextView endingSub = findViewById(R.id.text_obj_ending_sub);
        endingSub.setText(sub);

        endingLayout.setVisibility(View.VISIBLE);
        TextView endingEmoji = findViewById(R.id.text_obj_ending_emoji);
        endingEmoji.post(() -> KidAnimator.success(endingEmoji));

        speak("축하합니다! " + sub.replace("\n", " "));
    }

    private static String objParticle(String word) {
        if (word == null || word.isEmpty()) return "를";
        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) return "를";
        return ((last - 0xAC00) % 28 != 0) ? "을" : "를";
    }

    private void updateScoreUI() {
        runOnUiThread(() -> scoreText.setText("점수: " + totalScore + "점"));
    }

    private void hidePopups() {
        correctLayout.setVisibility(View.GONE);
        wrongLayout.setVisibility(View.GONE);
        endingLayout.setVisibility(View.GONE);
    }

    private void setStatus(String text) {
        runOnUiThread(() -> statusText.setText(text));
    }

    private void speak(String text) {
        try {
            Robot.getInstance().speak(TtsRequest.create(text, false));
        } catch (Exception e) {
            Log.w(TAG, "TTS 실패: " + e.getMessage());
        }
    }

    private void showConnectionDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("예) 192.168.0.10:8765");
        input.setText(VisionConfig.getWsUrl(this));

        new AlertDialog.Builder(this)
                .setTitle("비전 센서(파이썬) 연결 설정")
                .setMessage("파이썬 서버의 IP와 포트를 입력하세요.")
                .setView(input)
                .setPositiveButton("저장", (d, w) -> {
                    VisionConfig.setWsUrl(this, input.getText().toString());
                    visionClient.close();
                    connected = false;
                    connectVision();
                })
                .setNegativeButton("취소", null)
                .show();
    }
}
