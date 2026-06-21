package com.kidsFriend.domain.call.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.kidsFriend.R;
import com.kidsFriend.domain.call.model.CallState;
import com.kidsFriend.domain.call.model.CharacterProfile;
import com.kidsFriend.domain.chat.response.QuestionResponse;
import com.kidsFriend.global.repository.RepositoryCallback;
import com.kidsFriend.global.repository.TemiRepository;
import com.kidsFriend.global.ui.FullscreenHelper;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;
import com.robotemi.sdk.listeners.OnRobotReadyListener;

public class VoiceCallActivity extends AppCompatActivity implements OnRobotReadyListener, Robot.AsrListener {
    private Robot robot;
    private TemiRepository repository;
    private CharacterProfile character;
    private CallState currentState = CallState.IDLE;

    private ImageView characterImage;
    private TextView characterNameText;
    private TextView subtitleText;
    private TextView statusIconText;
    private TextView statusMessageText;
    private View pulseView;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FullscreenHelper.setFullscreen(this);
        setContentView(R.layout.activity_voice_call);

        character = (CharacterProfile) getIntent().getSerializableExtra("character");
        if (character == null) {
            finish();
            return;
        }

        robot = Robot.getInstance();
        repository = new TemiRepository(this);

        initUi();
        updateState(CallState.CALLING);

        // 시뮬레이션된 전화 연결 과정
        handler.postDelayed(() -> {
            statusMessageText.setText("벨이 울리는 중...");
            handler.postDelayed(() -> {
                statusMessageText.setText("곧 연결될 거야!");
                handler.postDelayed(() -> {
                    updateState(CallState.SPEAKING);
                    String intro = "안녕! 나 " + character.getName() + "야! 무슨 일이야?";
                    speak(intro);
                }, 1000);
            }, 1000);
        }, 1500);
    }

    private void initUi() {
        characterImage = findViewById(R.id.image_call_character);
        characterNameText = findViewById(R.id.text_call_character_name);
        subtitleText = findViewById(R.id.text_call_subtitle);
        statusIconText = findViewById(R.id.text_call_status_icon);
        statusMessageText = findViewById(R.id.text_call_status_message);
        pulseView = findViewById(R.id.view_pulse);

        characterImage.setImageResource(character.getImageRes());
        characterNameText.setText(character.getName());
        subtitleText.setText("");

        findViewById(R.id.btn_end_call).setOnClickListener(v -> endCall());
    }

    private void updateState(CallState state) {
        currentState = state;
        // 기본 색상으로 초기화
        int defaultTextColor = ContextCompat.getColor(this, R.color.text_primary);
        statusIconText.setTextColor(defaultTextColor);
        statusMessageText.setTextColor(defaultTextColor);

        switch (state) {
            case CALLING:
                statusIconText.setText("📞");
                statusMessageText.setText(character.getName() + "에게 전화 중...");
                startPulseAnimation();
                break;
            case LISTENING:
                statusIconText.setText("🎤");
                statusMessageText.setText("나에게 말해 줘");
                stopPulseAnimation();
                startMicAnimation();
                robot.askQuestion(""); // 사용자 입력을 기다림
                break;
            case THINKING:
                statusIconText.setText("🤔");
                statusMessageText.setText(character.getName() + " 친구가 생각 중");
                stopPulseAnimation();
                stopMicAnimation();
                break;
            case SPEAKING:
                statusIconText.setText("🗣");
                statusMessageText.setText(character.getName() + " 친구가 말하는 중");
                stopMicAnimation();
                startPulseAnimation();
                break;
            case ENDED:
                statusIconText.setText("📞");
                statusMessageText.setText("통화 종료");
                stopPulseAnimation();
                stopMicAnimation();
                break;
        }
    }

    private void startMicAnimation() {
        ScaleAnimation anim = new ScaleAnimation(1.0f, 1.2f, 1.0f, 1.2f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        anim.setDuration(600);
        anim.setRepeatCount(Animation.INFINITE);
        anim.setRepeatMode(Animation.REVERSE);
        statusIconText.startAnimation(anim);
    }

    private void stopMicAnimation() {
        statusIconText.clearAnimation();
    }

    private void startPulseAnimation() {
        pulseView.setVisibility(View.VISIBLE);
        ScaleAnimation anim = new ScaleAnimation(1.0f, 1.25f, 1.0f, 1.25f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        anim.setDuration(1200);
        anim.setRepeatCount(Animation.INFINITE);
        anim.setRepeatMode(Animation.REVERSE);
        pulseView.startAnimation(anim);
    }

    private void stopPulseAnimation() {
        pulseView.clearAnimation();
        pulseView.setVisibility(View.GONE);
    }

    private void speak(String text) {
        subtitleText.setText(text);
        robot.speak(TtsRequest.create(text, false));
        
        // 텍스트 길이에 비례하여 리스닝 상태로 전환 (약간의 여유 시간 포함)
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            if (currentState == CallState.SPEAKING) {
                updateState(CallState.LISTENING);
            }
        }, text.length() * 250L + 1000); 
    }

    private void endCall() {
        updateState(CallState.ENDED);
        String bye = "오늘 이야기 즐거웠어! 다음에 또 전화해줘, 안녕!";
        subtitleText.setText(bye);
        robot.speak(TtsRequest.create(bye, false));

        // 3초 후 초기 화면(MainActivity)으로 전환
        handler.postDelayed(() -> {
            android.content.Intent intent = new android.content.Intent(this, com.kidsFriend.MainActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
            overridePendingTransition(0, R.anim.slide_up); // 자연스럽게 사라지는 애니메이션 (또는 slide_down 추천)
        }, 3000);
    }

    @Override
    public void onAsrResult(@NonNull String asrResult) {
        if (asrResult.isEmpty()) return;
        
        handler.post(() -> {
            // 인식이 되고 있는 동안 초록색으로 표시
            if (currentState == CallState.LISTENING) {
                statusIconText.setTextColor(ContextCompat.getColor(this, R.color.kid_green));
                statusMessageText.setTextColor(ContextCompat.getColor(this, R.color.kid_green));
                subtitleText.setText(asrResult);
            }

            // 약간의 딜레이 후 THINKING 상태로 전환 (사용자가 말을 끝냈을 때)
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(() -> {
                updateState(CallState.THINKING);
                subtitleText.setText("...");
                
                repository.askVoiceQuestion(asrResult, asrResult, new RepositoryCallback<QuestionResponse>() {
                    @Override
                    public void onSuccess(QuestionResponse data) {
                        updateState(CallState.SPEAKING);
                        speak(data.answer);
                    }

                    @Override
                    public void onError(String message) {
                        updateState(CallState.SPEAKING);
                        speak("미안해, 다시 한 번 말해줄래?");
                    }
                });
            }, 1000); // 1초 정도 인식이 멈추면 전송
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
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onRobotReady(boolean isReady) {
        if (isReady) {
            robot.hideTopBar();
            robot.wakeup();
        }
    }
}
