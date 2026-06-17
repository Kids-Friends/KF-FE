package com.kidsFriend.domain.membership;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.kidsFriend.R;
import com.kidsFriend.domain.membership.ClientResponse;
import com.kidsFriend.global.repository.RepositoryCallback;
import com.kidsFriend.global.repository.TemiRepository;
import com.kidsFriend.global.session.SessionManager;
import com.kidsFriend.global.voice.TemiSpeechSpeaker;
import com.robotemi.sdk.Robot;

public class MembershipCardActivity extends AppCompatActivity {
    private static final String TAG = "MembershipCardActivity";

    /** true이면 기존 회원 조회 대신 신규 등록 스크립트를 자동 재생한다(데모용). */
    public static final String EXTRA_REGISTER_MODE = "register_mode";
    private static final String DEMO_MEMBER_NAME = "박도현";

    // 등록 스크립트의 연속 발화가 디바운스(3초)에 먹히지 않도록 디바운스를 끈다.
    private final TemiSpeechSpeaker speaker = new TemiSpeechSpeaker(false);
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TemiRepository repository;
    private TextView nameText;
    private TextView pointText;
    private ImageView photoImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_membership_card);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        repository = new TemiRepository(this);

        nameText = findViewById(R.id.text_member_name);
        pointText = findViewById(R.id.text_member_point);
        photoImage = findViewById(R.id.image_member_photo);
        Button backButton = findViewById(R.id.button_back);
        backButton.setOnClickListener(v -> finish());

        if (getIntent().getBooleanExtra(EXTRA_REGISTER_MODE, false)) {
            runRegistrationScript();
        } else {
            loadMember();
        }
    }

    /**
     * 데모-세이프 회원 등록 스크립트: 사진 촬영 → 이름 → 0점 카드 생성을 시간차로 자동 재생.
     * (가입 안내 멘트는 MainActivity가 먼저 발화하므로, 여기서는 사진 단계부터 시작한다.)
     */
    private void runRegistrationScript() {
        photoImage.setVisibility(View.INVISIBLE);
        nameText.setText("...");
        pointText.setText("-");

        // t=6.5s: MainActivity의 가입 안내가 끝날 무렵 사진 촬영 시작
        handler.postDelayed(() ->
                speaker.speak("먼저 프로필 사진을 찍어볼게! 예쁘게 웃어줘!"), 6500);

        handler.postDelayed(() -> {
            speaker.speak("하나, 둘, 셋! 찰칵!");
            photoImage.setVisibility(View.VISIBLE);
        }, 9500);

        handler.postDelayed(() ->
                speaker.speak("정말 멋진 사진이야! 이제 이름을 알려줘."), 12000);

        handler.postDelayed(() -> {
            nameText.setText(DEMO_MEMBER_NAME);
            pointText.setText("0");
            speaker.speak("반가워, 재석! 이제 회원 등록이 완료되었어. 앞으로 재밌게 놀아보자! 사랑해!");
        }, 15500);
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            ActivityInfo activityInfo = getPackageManager()
                    .getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
            Robot.getInstance().onStart(activityInfo);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Temi activity metadata is not available.", e);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void loadMember() {
        String clientId = SessionManager.getInstance(this).getCurrentClientId();
        repository.getClient(clientId, new RepositoryCallback<ClientResponse>() {
            @Override
            public void onSuccess(ClientResponse data) {
                if (isFinishing() || isDestroyed()) return;
                nameText.setText(data.childName);
                pointText.setText(String.valueOf(data.clientPoint));
                speaker.speak(getString(R.string.membership_speak_format, data.childName, data.clientPoint));
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                nameText.setText(R.string.membership_load_failed);
                pointText.setText("-");
            }
        });
    }
}
