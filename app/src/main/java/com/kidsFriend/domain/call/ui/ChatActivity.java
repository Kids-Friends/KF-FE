package com.kidsFriend.domain.call.ui;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kidsFriend.R;
import com.kidsFriend.domain.call.model.CharacterProfile;
import com.kidsFriend.domain.call.model.ChatMessage;
import com.kidsFriend.domain.chat.response.QuestionResponse;
import com.kidsFriend.global.repository.RepositoryCallback;
import com.kidsFriend.global.repository.TemiRepository;
import com.kidsFriend.global.ui.FullscreenHelper;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;
import com.robotemi.sdk.listeners.OnRobotReadyListener;

import java.util.ArrayList;
import java.util.List;

/**
 * 친구들과 카톡(채팅)하기.
 *
 * <p>음성 통화({@link VoiceCallActivity}) 대신, 미리 준비된 빠른 질문 버튼으로 메시지를 보내고
 * 카톡식 말풍선으로 대화한다. 친구의 답변은 화면 말풍선 + 로봇 TTS로 함께 전달한다.</p>
 */
public class ChatActivity extends AppCompatActivity implements OnRobotReadyListener {

    /** 아이가 타이핑 없이 탭해서 보낼 수 있는 빠른 질문 문구. */
    private static final String[] QUICK_QUESTIONS = {
            "안녕!",
            "뭐 하고 있어?",
            "나랑 놀자!",
            "오늘 뭐 했어?",
            "재밌는 얘기 해줘",
            "좋아하는 게 뭐야?",
            "잘 가!"
    };

    private Robot robot;
    private TemiRepository repository;
    private CharacterProfile character;

    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private final List<ChatMessage> messages = new ArrayList<>();

    private boolean greeted = false;
    private boolean waitingReply = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FullscreenHelper.setFullscreen(this);
        setContentView(R.layout.activity_chat);

        character = (CharacterProfile) getIntent().getSerializableExtra("character");
        if (character == null) {
            finish();
            return;
        }

        robot = Robot.getInstance();
        repository = new TemiRepository(this);

        initUi();
        // 첫 인사는 말풍선으로 먼저 보여주고, 음성은 로봇 준비되면(onRobotReady) 1회 읽어준다.
        addMessage(ChatMessage.TYPE_FRIEND,
                "안녕! 나 " + character.getName() + "야! 오늘 뭐 하고 놀까?");
    }

    private void initUi() {
        ((TextView) findViewById(R.id.text_chat_character_name)).setText(character.getName());
        ((ImageView) findViewById(R.id.image_chat_character)).setImageResource(character.getImageRes());
        findViewById(R.id.btn_chat_back).setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recycler_chat);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatAdapter(messages, character.getImageRes());
        recyclerView.setAdapter(adapter);

        buildQuickQuestionButtons();
    }

    private void buildQuickQuestionButtons() {
        LinearLayout container = findViewById(R.id.layout_quick_questions);
        int marginEnd = dp(12);
        for (String question : QUICK_QUESTIONS) {
            Button button = new Button(this);
            button.setText(question);
            button.setAllCaps(false);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
            button.setTextColor(ContextCompat.getColor(this, R.color.kid_blue));
            button.setBackgroundResource(R.drawable.secondary_button);
            button.setPadding(dp(28), dp(16), dp(28), dp(16));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(marginEnd);
            button.setLayoutParams(params);

            button.setOnClickListener(v -> sendMessage(question));
            container.addView(button);
        }
    }

    /** 빠른 질문을 보낸다: 내 말풍선 추가 → BE에 전송 → 친구 답변 말풍선 + TTS. */
    private void sendMessage(String text) {
        if (waitingReply) return; // 답변 대기 중엔 중복 전송 방지
        waitingReply = true;

        addMessage(ChatMessage.TYPE_MINE, text);

        repository.askVoiceQuestion(text, character.getId(), new RepositoryCallback<QuestionResponse>() {
            @Override
            public void onSuccess(QuestionResponse data) {
                waitingReply = false;
                addMessage(ChatMessage.TYPE_FRIEND, data.answer);
                speak(data.answer);
            }

            @Override
            public void onError(String message) {
                waitingReply = false;
                String fallback = "미안, 지금은 대답하기 힘들어. 조금 있다 다시 얘기하자!";
                addMessage(ChatMessage.TYPE_FRIEND, fallback);
                speak(fallback);
            }
        });
    }

    private void addMessage(int type, String text) {
        messages.add(new ChatMessage(type, text));
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerView.scrollToPosition(messages.size() - 1);
    }

    private void speak(String text) {
        robot.speak(TtsRequest.create(text, false)); // false = 검은 자막창 숨김
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onStart() {
        super.onStart();
        robot.addOnRobotReadyListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        robot.removeOnRobotReadyListener(this);
    }

    @Override
    public void onRobotReady(boolean isReady) {
        if (!isReady) return;
        robot.hideTopBar();
        // 내장 호출어/대화 레이어 차단(통화 화면과 동일 정책). 채팅은 ASR을 쓰지 않는다.
        robot.toggleWakeup(true);
        robot.setDetectionModeOn(false);

        if (!greeted && !messages.isEmpty()) {
            greeted = true;
            speak(messages.get(0).text); // 첫 인사 1회 음성 출력
        }
    }
}
