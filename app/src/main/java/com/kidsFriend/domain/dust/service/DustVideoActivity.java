package com.kidsFriend.domain.dust.service;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.kidsFriend.R;
import com.kidsFriend.global.ui.FullscreenHelper;

/**
 * 미세먼지 놀이.
 *
 * <p>"미세먼지" 버튼을 누르면 안내 영상({@code dust_intro})을 전체 화면으로 1회 재생하고,
 * 끝나면 자동으로 메인으로 돌아간다.</p>
 */
public class DustVideoActivity extends AppCompatActivity {

    private static final String TAG = "DustVideoActivity";

    private PlayerView playerView;
    private ExoPlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        FullscreenHelper.setFullscreen(this);
        
        setContentView(R.layout.activity_dust_video);

        playerView = findViewById(R.id.player_view_dust);
        findViewById(R.id.btn_dust_back).setOnClickListener(v -> finish());

        initializePlayer();
    }

    private void initializePlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.dust_intro);
        MediaItem mediaItem = MediaItem.fromUri(uri);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.setPlayWhenReady(true);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    startActivity(new Intent(DustVideoActivity.this, AirQualityResultActivity.class));
                    finish();
                }
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                Log.w(TAG, "영상 재생 오류: " + error.getMessage());
                startActivity(new Intent(DustVideoActivity.this, AirQualityResultActivity.class));
                finish();
            }
        });
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
