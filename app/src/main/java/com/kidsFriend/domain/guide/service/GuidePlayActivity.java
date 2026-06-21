package com.kidsFriend.domain.guide.service;

import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.kidsFriend.R;
import com.kidsFriend.global.ui.FullscreenHelper;
import com.kidsFriend.global.ui.GlassBlur;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener;

/**
 * 위치 안내 놀이.
 *
 * <p>키즈카페 지도(이미지)를 띄우고, 그 <b>위에 보이지 않는 터치 버튼</b>을 각 존 위치에 얹는다.
 * 아이가 가고 싶은 존을 누르면 테미가 음성 안내와 함께 {@code goTo}로 그곳까지 이동한다.
 * 현재 위치는 지도 위에 마커(📍)로 표시하고, 안내가 끝나면 마커를 목적지로 옮긴다.</p>
 */
public class GuidePlayActivity extends AppCompatActivity
        implements OnGoToLocationStatusChangedListener {

    private static final String TAG = "GuidePlayActivity";

    /** 지도상의 각 존: 표시/안내 이름 + 지도 비율 사각형(left,top,right,bottom in 0~1). */
    private enum Zone {
        BALLPOOL("볼풀장", 0.03f, 0.04f, 0.25f, 0.42f),
        JUNGLE("정글짐", 0.27f, 0.05f, 0.49f, 0.42f),
        TODDLER("유아놀이존", 0.51f, 0.04f, 0.71f, 0.42f),
        ROLEPLAY("역할놀이존", 0.73f, 0.03f, 0.97f, 0.40f),
        READING("독서존", 0.80f, 0.44f, 0.97f, 0.65f),
        TOILET("화장실", 0.80f, 0.67f, 0.97f, 0.90f),
        CAFE("카페존", 0.50f, 0.52f, 0.76f, 0.90f),
        DESK("입구/안내데스크", 0.27f, 0.62f, 0.47f, 0.90f),
        SHOE("신발장", 0.03f, 0.56f, 0.18f, 0.78f);

        final String label;
        final float l, t, r, b;

        Zone(String label, float l, float t, float r, float b) {
            this.label = label;
            this.l = l; this.t = t; this.r = r; this.b = b;
        }

        float cx() { return (l + r) / 2f; }
        float cy() { return (t + b) / 2f; }
    }

    private Robot robot;
    private ImageView imgMap;
    private FrameLayout overlay;
    private TextView marker;
    private View layoutIntro;
    private PlayerView playerView;
    private ExoPlayer player;
    private boolean introFinished = false;
    private Zone currentZone = Zone.DESK; // 시작 위치 = 입구/안내데스크

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        FullscreenHelper.setFullscreen(this);
        
        setContentView(R.layout.activity_guide_play);

        robot = Robot.getInstance();
        imgMap = findViewById(R.id.img_map);
        overlay = findViewById(R.id.overlay_zones);
        layoutIntro = findViewById(R.id.layout_guide_intro);
        playerView = findViewById(R.id.player_view_guide_intro);
        findViewById(R.id.btn_guide_back).setOnClickListener(v -> finish());

        // 안내 칩에 글래스 블러 적용(뒤의 지도가 흐려진다)
        GlassBlur.apply(this, findViewById(R.id.blur_guide_title),
                findViewById(R.id.root_guide), 16f, R.color.glass_dark);

        // 지도가 실제로 그려진 뒤(크기 확정) 오버레이를 배치한다(인트로 뒤에서 미리 준비).
        imgMap.post(this::buildOverlay);

        startIntro();
    }

    /** 진입 인트로 영상 1회 재생. 끝나거나 '건너뛰기'를 누르면 지도가 드러난다. */
    private void startIntro() {
        layoutIntro.setVisibility(View.VISIBLE);
        
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.map_intro);
        MediaItem mediaItem = MediaItem.fromUri(uri);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.setPlayWhenReady(true);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    finishIntro();
                }
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                Log.w(TAG, "인트로 영상 오류: " + error.getMessage());
                finishIntro();
            }
        });
    }

    /** 인트로 종료 → 지도 표시. (완료/건너뛰기 중복 호출에 안전) */
    private void finishIntro() {
        if (introFinished) return;
        introFinished = true;
        releasePlayer();
        layoutIntro.setVisibility(View.GONE);
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
        if (player != null && !introFinished) {
            player.play();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }

    @Override
    protected void onStart() {
        super.onStart();
        robot.addOnGoToLocationStatusChangedListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        robot.removeOnGoToLocationStatusChangedListener(this);
    }

    /** 지도 위에 존별 투명 버튼과 현재 위치 마커를 얹는다. */
    private void buildOverlay() {
        RectF map = computeFitCenterRect();
        if (map == null) {
            imgMap.post(this::buildOverlay); // 아직 크기 미확정이면 다음 프레임에 재시도
            return;
        }
        overlay.removeAllViews();

        // 1) 존별 보이지 않는 터치 버튼
        for (Zone zone : Zone.values()) {
            View hotspot = new View(this);
            hotspot.setLayoutParams(rectToParams(map, zone.l, zone.t, zone.r, zone.b));
            hotspot.setOnClickListener(v -> onZoneSelected(zone));
            overlay.addView(hotspot);
        }

        // 2) 현재 위치 마커(보이는 칩)
        marker = new TextView(this);
        marker.setText("📍 현재 위치");
        marker.setTextColor(Color.WHITE);
        marker.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        int padH = dp(14), padV = dp(8);
        marker.setPadding(padH, padV, padH, padV);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFE53935); // 빨강 칩
        bg.setCornerRadius(dp(20));
        marker.setBackground(bg);
        marker.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        overlay.addView(marker);
        moveMarkerTo(currentZone, map);
    }

    /** fitCenter로 표시된 지도 이미지의 실제 사각형(뷰 좌표계). */
    private RectF computeFitCenterRect() {
        Drawable d = imgMap.getDrawable();
        if (d == null) return null;
        float vw = imgMap.getWidth(), vh = imgMap.getHeight();
        float dw = d.getIntrinsicWidth(), dh = d.getIntrinsicHeight();
        if (vw <= 0 || vh <= 0 || dw <= 0 || dh <= 0) return null;
        float scale = Math.min(vw / dw, vh / dh);
        float w = dw * scale, h = dh * scale;
        float left = (vw - w) / 2f, top = (vh - h) / 2f;
        return new RectF(left, top, left + w, top + h);
    }

    private FrameLayout.LayoutParams rectToParams(RectF map, float fl, float ft, float fr, float fb) {
        int w = Math.round((fr - fl) * map.width());
        int h = Math.round((fb - ft) * map.height());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.max(1, w), Math.max(1, h));
        lp.leftMargin = Math.round(map.left + fl * map.width());
        lp.topMargin = Math.round(map.top + ft * map.height());
        return lp;
    }

    /** 마커를 존 중심으로 이동(마커 크기를 알아야 하므로 post로 측정 후 보정). */
    private void moveMarkerTo(Zone zone, RectF map) {
        marker.post(() -> {
            int cx = Math.round(map.left + zone.cx() * map.width());
            int cy = Math.round(map.top + zone.cy() * map.height());
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) marker.getLayoutParams();
            lp.leftMargin = Math.max(0, cx - marker.getWidth() / 2);
            lp.topMargin = Math.max(0, cy - marker.getHeight() / 2);
            marker.setLayoutParams(lp);
        });
    }

    private void onZoneSelected(Zone zone) {
        if (zone == currentZone) {
            speak("여긴 지금 우리가 있는 곳이야!");
            return;
        }
        speak(zone.label + "(으)로 안내할게! 나를 따라와!");
        try {
            robot.goTo(zone.label); // 테미에 저장된 위치명과 일치해야 실제 주행
        } catch (Exception e) {
            Log.w(TAG, "goTo 실패: " + e.getMessage());
        }
        currentZone = zone;
        RectF map = computeFitCenterRect();
        if (map != null) moveMarkerTo(zone, map);
    }

    @Override
    public void onGoToLocationStatusChanged(@NonNull String location, @NonNull String status,
                                            int descriptionId, @NonNull String description) {
        if (OnGoToLocationStatusChangedListener.COMPLETE.equals(status)) {
            speak("도착했어! 여기가 " + location + "(이)야. 재미있게 놀자!");
        } else if (OnGoToLocationStatusChangedListener.ABORT.equals(status)) {
            speak("앗, 지금은 그곳으로 갈 수 없어. 길이 막혔나 봐.");
        }
    }

    private void speak(String text) {
        robot.speak(TtsRequest.create(text, false));
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
