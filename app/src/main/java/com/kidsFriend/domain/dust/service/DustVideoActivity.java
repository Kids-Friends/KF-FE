package com.kidsFriend.domain.dust.service;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.kidsFriend.R;

/**
 * 미세먼지 놀이.
 *
 * <p>"미세먼지" 버튼을 누르면 안내 영상({@code dust_intro})을 전체 화면으로 1회 재생하고,
 * 끝나면 자동으로 메인으로 돌아간다.</p>
 */
public class DustVideoActivity extends AppCompatActivity {

    private static final String TAG = "DustVideoActivity";

    private VideoView videoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dust_video);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        videoView = findViewById(R.id.video_dust);
        findViewById(R.id.btn_dust_back).setOnClickListener(v -> finish());

        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.dust_intro);
        videoView.setVideoURI(uri);
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            videoView.start();
        });
        videoView.setOnCompletionListener(mp -> finish()); // 끝나면 메인으로 복귀
        videoView.setOnErrorListener((mp, what, extra) -> {
            Log.w(TAG, "영상 재생 오류 what=" + what + " extra=" + extra);
            finish();
            return true;
        });
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
