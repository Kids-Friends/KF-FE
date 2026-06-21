package com.kidsFriend.domain.dust.service;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.animation.AnimationUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.kidsFriend.R;
import com.kidsFriend.global.repository.RepositoryCallback;
import com.kidsFriend.global.repository.TemiRepository;
import com.kidsFriend.global.ui.FullscreenHelper;
import com.kidsFriend.global.ui.GlassBlur;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;

import eightbitlab.com.blurview.BlurView;

public class AirQualityResultActivity extends AppCompatActivity {
    private TemiRepository repository;
    private ImageView faceImage;
    private TextView gradeText;
    private TextView descText;
    private View statusGlow;
    private BlurView blurView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FullscreenHelper.setFullscreen(this);
        setContentView(R.layout.activity_air_quality_result);

        repository = new TemiRepository(this);
        faceImage = findViewById(R.id.image_air_face);
        gradeText = findViewById(R.id.text_air_grade);
        descText = findViewById(R.id.text_air_desc);
        statusGlow = findViewById(R.id.view_status_glow);
        blurView = findViewById(R.id.blur_air_panel);

        // 글래스 블러 효과 적용
        GlassBlur.apply(this, blurView, findViewById(R.id.root_air_quality), 16f, R.color.glass_tint);

        findViewById(R.id.btn_air_confirm).setOnClickListener(v -> finish());
        findViewById(R.id.btn_air_back).setOnClickListener(v -> finish());

        fetchResult();
    }

    private void fetchResult() {
        repository.getAirQuality(new RepositoryCallback<String>() {
            @Override
            public void onSuccess(String grade) {
                updateUi(grade);
            }

            @Override
            public void onError(String message) {
                updateUi("보통");
            }
        });
    }

    private void updateUi(String grade) {
        String speechText;
        int color;

        switch (grade) {
            case "좋음":
                faceImage.setImageResource(R.drawable.face_joy);
                gradeText.setText("좋음 🍀");
                color = ContextCompat.getColor(this, R.color.kid_green);
                descText.setText("공기가 아주 깨끗해!\n신나게 뛰어놀아도 좋아!");
                speechText = "지금 공기가 아주 깨끗해! 신나게 뛰어놀아도 좋아.";
                break;
            case "나쁨":
                faceImage.setImageResource(R.drawable.face_sadness);
                gradeText.setText("나쁨 ☁️");
                color = ContextCompat.getColor(this, R.color.kid_pink);
                descText.setText("공기가 조금 탁하네.\n야외 활동은 조심하는 게 좋겠어.");
                speechText = "공기가 조금 탁하네. 야외 활동은 조심하는 게 좋겠어.";
                break;
            case "보통":
            default:
                faceImage.setImageResource(R.drawable.face_peaceful);
                gradeText.setText("보통 🙂");
                color = ContextCompat.getColor(this, R.color.kid_blue);
                descText.setText("공기는 평소와 같아.\n즐겁게 보내!");
                speechText = "공기는 평소와 같아. 즐겁게 보내!";
                break;
        }

        gradeText.setTextColor(color);
        statusGlow.setBackgroundTintList(ColorStateList.valueOf(color));
        
        // 부드러운 등장 애니메이션
        faceImage.startAnimation(AnimationUtils.loadAnimation(this, R.anim.combined_enter));

        Robot.getInstance().speak(TtsRequest.create(speechText, false));
    }
}
