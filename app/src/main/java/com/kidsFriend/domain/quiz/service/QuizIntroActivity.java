package com.kidsFriend.domain.quiz.service;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.kidsFriend.R;

/**
 * 퀴즈 인트로.
 *
 * <p>"퀴즈 놀이" 버튼을 누르면 인트로 영상({@code quiz_intro})을 1회 재생하고, 끝나거나
 * '건너뛰기'를 누르면 실제 퀴즈 화면({@link QuizActivity})으로 전환한다.</p>
 */
public class QuizIntroActivity extends AppCompatActivity {

    private static final String TAG = "QuizIntroActivity";

    private VideoView videoView;
    private boolean moved = false; // 완료/건너뛰기 중복 전환 방지

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_intro);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        videoView = findViewById(R.id.video_quiz_intro);

        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.quiz_intro);
        videoView.setVideoURI(uri);
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            videoView.start();
        });
        videoView.setOnCompletionListener(mp -> goToQuiz());
        videoView.setOnErrorListener((mp, what, extra) -> {
            Log.w(TAG, "인트로 영상 오류 what=" + what + " extra=" + extra);
            goToQuiz();
            return true;
        });
    }

    private void goToQuiz() {
        if (moved) return;
        moved = true;
        if (videoView != null) videoView.stopPlayback();
        startActivity(new Intent(this, QuizActivity.class));
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null) videoView.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoView != null) videoView.stopPlayback();
    }
}
