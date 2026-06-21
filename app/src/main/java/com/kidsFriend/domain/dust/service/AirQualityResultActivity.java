package com.kidsFriend.domain.dust.service;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.kidsFriend.R;
import com.kidsFriend.global.repository.RepositoryCallback;
import com.kidsFriend.global.repository.TemiRepository;
import com.kidsFriend.global.ui.FullscreenHelper;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;

public class AirQualityResultActivity extends AppCompatActivity {
    private TemiRepository repository;
    private ImageView faceImage;
    private TextView gradeText;
    private TextView descText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FullscreenHelper.setFullscreen(this);
        setContentView(R.layout.activity_air_quality_result);

        repository = new TemiRepository(this);
        faceImage = findViewById(R.id.image_air_face);
        gradeText = findViewById(R.id.text_air_grade);
        descText = findViewById(R.id.text_air_desc);

        findViewById(R.id.btn_air_confirm).setOnClickListener(v -> finish());

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
        switch (grade) {
            case "좋음":
                faceImage.setImageResource(R.drawable.face_joy);
                gradeText.setText("좋음");
                gradeText.setTextColor(ContextCompat.getColor(this, R.color.kid_green));
                descText.setText("공기가 아주 깨끗해요! 신나게 뛰어놀아도 좋아요.");
                speechText = "지금 공기가 아주 깨끗해요! 신나게 뛰어놀아도 좋아요.";
                break;
            case "나쁨":
                faceImage.setImageResource(R.drawable.face_sadness);
                gradeText.setText("나쁨");
                gradeText.setTextColor(ContextCompat.getColor(this, R.color.kid_pink));
                descText.setText("공기가 조금 탁하네요. 야외 활동은 조심하는 게 좋겠어요.");
                speechText = "공기가 조금 탁하네요. 야외 활동은 조심하는 게 좋겠어요.";
                break;
            case "보통":
            default:
                faceImage.setImageResource(R.drawable.face_peaceful);
                gradeText.setText("보통");
                gradeText.setTextColor(ContextCompat.getColor(this, R.color.kid_blue));
                descText.setText("공기는 평소와 같아요. 즐겁게 보내세요!");
                speechText = "공기는 평소와 같아요. 즐겁게 보내세요!";
                break;
        }
        Robot.getInstance().speak(TtsRequest.create(speechText, false));
    }
}
