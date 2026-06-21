package com.kidsFriend.domain.quiz.service;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.kidsFriend.R;
import com.kidsFriend.global.ui.FullscreenHelper;

/**
 * 퀴즈 인트로.
 *
 * <p>"퀴즈 놀이" 버튼을 누르면 인트로 영상({@code quiz_intro})을 1회 재생하고, 끝나거나
 * 실제 퀴즈 화면({@link QuizActivity})으로 전환한다.</p>
 */
public class QuizIntroActivity extends AppCompatActivity {

    private static final String TAG = "QuizIntroActivity";

    private PlayerView playerView;
    private ExoPlayer player;
    private boolean moved = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 몰입형 전체화면 적용
        FullscreenHelper.setFullscreen(this);
        
        setContentView(R.layout.activity_quiz_intro);

        playerView = findViewById(R.id.player_view_quiz);
        findViewById(R.id.btn_quiz_intro_back).setOnClickListener(v -> finish());
        initializePlayer();
    }

    private void initializePlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.quiz_intro);
        MediaItem mediaItem = MediaItem.fromUri(uri);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.setPlayWhenReady(true);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    goToQuiz();
                }
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                Log.w(TAG, "인트로 영상 오류: " + error.getMessage());
                goToQuiz();
            }
        });
    }

    private void goToQuiz() {
        if (moved) return;
        moved = true;
        releasePlayer();
        startActivity(new Intent(this, QuizActivity.class));
        finish();
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) {
            player.play();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }
}
