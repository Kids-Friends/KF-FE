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

public class VoiceCallActivity extends AppCompatActivity implements OnRobotReadyListener, Robot.AsrListener, Robot.TtsListener {
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
                statusMessageText.setText("곧 연결돼요!");
                handler.postDelayed(() -> {
                    updateState(CallState.SPEAKING);
                    String intro = "안녕! 나 " + character.getName() + "야! 무슨 일이니?";
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
                statusMessageText.setText("내가 말하는 중");
                stopPulseAnimation();
                startMicAnimation();
                robot.askQuestion(""); // 사용자 입력을 기다림
                break;
            case THINKING:
                statusIconText.setText("🤔");
                statusMessageText.setText(character.getName() + "이 생각하는 중");
                stopPulseAnimation();
                stopMicAnimation();
                break;
            case SPEAKING:
                statusIconText.setText("🗣");
                statusMessageText.setText(character.getName() + " 말하는 중");
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

    // 기존
//    private void speak(String text) {
//        subtitleText.setText(text);
//        robot.speak(TtsRequest.create(text, false));
//
//        // 텍스트 길이에 비례하여 리스닝 상태로 전환 (약간의 여유 시간 포함)
//        handler.removeCallbacksAndMessages(null);
//        handler.postDelayed(() -> {
//            if (currentState == CallState.SPEAKING) {
//                updateState(CallState.LISTENING);
//            }
//        }, text.length() * 250L + 1000);
//    }

    private void speak(String text) {
        subtitleText.setText(text);

        // UI 상태 업데이트 (말하는 중)
        updateState(CallState.SPEAKING);

        // 목소리만 나오게 함 (두 번째 인자 false가 검은 자막을 숨깁니다)
        TtsRequest ttsRequest = TtsRequest.create(text, false);
        robot.speak(ttsRequest);

        // 더 이상 여기서 askQuestion("")을 바로 호출하지 않습니다.
        // 대신 onTtsStatusChanged 콜백에서 말이 끝난 것을 확인한 후 호출합니다.
    }

    @Override
    public void onTtsStatusChanged(@NonNull TtsRequest ttsRequest) {
        if (ttsRequest.getStatus() == TtsRequest.Status.COMPLETED) {
            runOnUiThread(() -> {
                if (currentState == CallState.SPEAKING) {
                    updateState(CallState.LISTENING);
                }
            });
        }
    }

    private void endCall() {
        updateState(CallState.ENDED);
        String bye = "오늘 이야기 즐거웠어! 다음에 또 전화해줘, 안녕!";
        subtitleText.setText(bye);
        robot.speak(TtsRequest.create(bye, false));
        handler.postDelayed(this::finish, 3000);
    }

//    @Override
//    public void onAsrResult(@NonNull String asrResult) {
//        if (asrResult.isEmpty()) return;
//
//        handler.post(() -> {
//            // 인식이 되고 있는 동안 초록색으로 표시
//            if (currentState == CallState.LISTENING) {
//                statusIconText.setTextColor(ContextCompat.getColor(this, R.color.kid_green));
//                statusMessageText.setTextColor(ContextCompat.getColor(this, R.color.kid_green));
//                subtitleText.setText(asrResult);
//            }
//
//            // 약간의 딜레이 후 THINKING 상태로 전환 (사용자가 말을 끝냈을 때)
//            handler.removeCallbacksAndMessages(null);
//            handler.postDelayed(() -> {
//                updateState(CallState.THINKING);
//                subtitleText.setText("...");
//
//                repository.askVoiceQuestion(asrResult, asrResult, new RepositoryCallback<QuestionResponse>() {
//                    @Override
//                    public void onSuccess(QuestionResponse data) {
//                        updateState(CallState.SPEAKING);
//                        speak(data.answer);
//                    }
//
//                    @Override
//                    public void onError(String message) {
//                        updateState(CallState.SPEAKING);
//                        speak("미안해, 다시 한 번 말해줄래?");
//                    }
//                });
//            }, 1000); // 1초 정도 인식이 멈추면 전송
//        });
//    }


    @Override
    public void onAsrResult(@NonNull String asrResult) {
        if (asrResult.isEmpty()) return;

        runOnUiThread(() -> {
            // 사용자가 말한 내용을 자막으로 표시
            subtitleText.setText(asrResult);
            updateState(CallState.THINKING); // "생각 중..." 상태로 변경

            // AI 서버에 질문 전달 (캐릭터 정보를 포함하여 전달하는 것이 좋음)
            repository.askVoiceQuestion(asrResult, character.getId(), new RepositoryCallback<QuestionResponse>() {
                @Override
                public void onSuccess(QuestionResponse data) {
                    // AI의 답변을 다시 캐릭터 목소리로 말하며 대화 이어가기
                    speak(data.answer);
                }

                @Override
                public void onError(String message) {
                    speak("미안해, 잘 못 알아들었어. 다시 말해줄래?");
                }
            });
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        robot.addOnRobotReadyListener(this);
        robot.addAsrListener(this);
        robot.addTtsListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        robot.removeOnRobotReadyListener(this);
        robot.removeAsrListener(this);
        robot.removeTtsListener(this);
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
