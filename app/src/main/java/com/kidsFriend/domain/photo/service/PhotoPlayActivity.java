package com.kidsFriend.domain.photo.service;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.google.common.util.concurrent.ListenableFuture;
import com.kidsFriend.R;
import com.kidsFriend.global.ui.FullscreenHelper;
import com.kidsFriend.global.ui.KidAnimator;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 사진 찍기 놀이.
 *
 * <p>흐름: <b>인트로 영상</b> → <b>템플릿 선택(4색)</b> → <b>1장 촬영</b>(카운트다운) → <b>결과 표시.</b></p>
 */
public class PhotoPlayActivity extends AppCompatActivity {
    private static final String TAG = "PhotoPlayActivity";
    private static final int PERMISSION_REQUEST_CODE = 2001;

    /** 합성 입력 사진의 긴 변 상한(px). 템플릿(1536×1024) 해상도에 맞춰 다운샘플 → OOM 방지. */
    private static final int MAX_PHOTO_DIM = 1536;

    private enum Template {
        BLUE(R.drawable.camera_template_blue),
        GREEN(R.drawable.camera_template_green),
        PINK(R.drawable.camera_template_pink),
        YELLOW(R.drawable.camera_template_yellow);

        final int res;
        Template(int res) { this.res = res; }
    }

    private View layoutIntro, layoutTemplate, layoutCamera, layoutResult, layoutProcessing;
    private PlayerView playerView;
    private ExoPlayer player;
    private PreviewView cameraPreview;
    private ImageView imgCameraTemplate;
    private TextView textCountdown, textProgress;
    private ImageView imgResult, imgResultTemplate;
    private View flashOverlay, resultActions, layoutResultContainer;

    private ImageCapture imageCapture;
    private Robot robot;
    private Template selectedTemplate = Template.BLUE;

    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private CountDownTimer countDownTimer;
    private boolean isProcessing = false;
    private boolean introFinished = false;
    private Bitmap currentResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        FullscreenHelper.setFullscreen(this);
        
        setContentView(R.layout.activity_photo_play);

        robot = Robot.getInstance();
        layoutIntro = findViewById(R.id.layout_step_intro);
        playerView = findViewById(R.id.player_view_photo_intro);
        layoutTemplate = findViewById(R.id.layout_step_template);
        layoutCamera = findViewById(R.id.layout_step_camera);
        layoutResult = findViewById(R.id.layout_step_result);
        layoutProcessing = findViewById(R.id.layout_step_processing);
        cameraPreview = findViewById(R.id.camera_preview);
        imgCameraTemplate = findViewById(R.id.img_camera_template);
        textCountdown = findViewById(R.id.text_countdown);
        textProgress = findViewById(R.id.text_photo_progress);
        imgResult = findViewById(R.id.img_result);
        imgResultTemplate = findViewById(R.id.img_result_template);
        layoutResultContainer = findViewById(R.id.layout_result_container);
        flashOverlay = findViewById(R.id.view_flash);
        resultActions = findViewById(R.id.layout_result_actions);

        KidAnimator.onClick(findViewById(R.id.btn_tpl_blue), v -> selectTemplate(Template.BLUE));
        KidAnimator.onClick(findViewById(R.id.btn_tpl_green), v -> selectTemplate(Template.GREEN));
        KidAnimator.onClick(findViewById(R.id.btn_tpl_pink), v -> selectTemplate(Template.PINK));
        KidAnimator.onClick(findViewById(R.id.btn_tpl_yellow), v -> selectTemplate(Template.YELLOW));

        KidAnimator.onClick(findViewById(R.id.btn_photo_retry), v -> showTemplateStep());
        KidAnimator.onClick(findViewById(R.id.btn_photo_home), v -> finish());
        findViewById(R.id.btn_photo_back).setOnClickListener(v -> finish());

        checkPermissions();
        startIntro();
    }

    // ── 인트로 영상 ──────────────────────────────────────────────────────────

    private void startIntro() {
        layoutIntro.setVisibility(View.VISIBLE);
        layoutTemplate.setVisibility(View.GONE);
        
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.camera_intro);
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

    private void finishIntro() {
        if (introFinished) return;
        introFinished = true;
        releasePlayer();
        layoutIntro.setVisibility(View.GONE);
        layoutTemplate.setVisibility(View.VISIBLE);
        KidAnimator.slideIn(layoutTemplate);
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    // ── 촬영 흐름 ────────────────────────────────────────────────────────────

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
        }
    }

    private void showTemplateStep() {
        cancelCountdown();
        isProcessing = false;
        recycleResult();
        layoutProcessing.setVisibility(View.GONE);
        layoutResult.setVisibility(View.GONE);
        layoutCamera.setVisibility(View.GONE);
        layoutTemplate.setVisibility(View.VISIBLE);
        KidAnimator.slideIn(layoutTemplate);
    }

    private void showProcessing() {
        cancelCountdown();
        textCountdown.setVisibility(View.GONE);
        layoutCamera.setVisibility(View.GONE);
        layoutProcessing.setVisibility(View.VISIBLE);
        KidAnimator.slideIn(layoutProcessing);
    }

    private void selectTemplate(Template tpl) {
        if (isProcessing) return;
        isProcessing = true;
        selectedTemplate = tpl;
        imgCameraTemplate.setImageResource(tpl.res);
        layoutTemplate.setVisibility(View.GONE);
        layoutResult.setVisibility(View.GONE);
        layoutCamera.setVisibility(View.VISIBLE);
        startCamera();
        startCountdown();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetResolution(new android.util.Size(2048, 1536))
                        .build();

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
            } catch (Exception e) {
                Log.e(TAG, "Camera binding failed", e);
                onCaptureFailed("카메라를 열지 못했어. 다시 해볼까?");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void startCountdown() {
        cancelCountdown();
        textProgress.setText("예쁘게 포즈를 취해봐!");
        textCountdown.setVisibility(View.VISIBLE);

        countDownTimer = new CountDownTimer(4000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                if (seconds > 0) {
                    textCountdown.setText(String.valueOf(seconds));
                    KidAnimator.countdown(textCountdown);
                    robot.speak(TtsRequest.create(String.valueOf(seconds), false));
                }
            }

            @Override
            public void onFinish() {
                textCountdown.setVisibility(View.GONE);
                KidAnimator.flash(flashOverlay);
                takePhoto();
            }
        };
        countDownTimer.start();
    }

    private void cancelCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    private void takePhoto() {
        if (imageCapture == null) {
            onCaptureFailed("카메라가 아직 준비되지 않았어. 다시 해볼까?");
            return;
        }

        showProcessing();

        File photoFile = new File(getExternalCacheDir(), "kf_photo.jpg");
        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(options, bgExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        robot.speak(TtsRequest.create("찰칵!", false));

                        Bitmap photo = null;
                        try {
                            photo = decodeSampled(photoFile, MAX_PHOTO_DIM);
                            if (photo == null) {
                                throw new IllegalStateException("사진 디코드 결과가 null");
                            }
                            
                            final Bitmap finalPhoto = photo;
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) {
                                    if (finalPhoto != null) finalPhoto.recycle();
                                    return;
                                }
                                showResult(finalPhoto);
                            });
                        } catch (Throwable t) {
                            Log.e(TAG, "이미지 처리 실패", t);
                            runOnUiThread(() -> onCaptureFailed("사진을 불러오는데 문제가 생겼어. 다시 해볼까?"));
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Photo capture failed", exception);
                        runOnUiThread(() -> onCaptureFailed("사진을 찍지 못했어. 다시 해볼까?"));
                    }
                });
    }

    private void onCaptureFailed(String message) {
        robot.speak(TtsRequest.create(message, false));
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        showTemplateStep();
    }

    private Bitmap decodeSampled(File file, int maxDim) {
        String path = file.getAbsolutePath();

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int w = bounds.outWidth, h = bounds.outHeight;
        if (w <= 0 || h <= 0) return null;

        int sample = 1;
        while ((w / sample) > maxDim || (h / sample) > maxDim) {
            sample *= 2;
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeFile(path, opts);
        if (bmp == null) return null;
        return applyExifRotation(bmp, path);
    }

    private Bitmap applyExifRotation(Bitmap bmp, String path) {
        try {
            ExifInterface exif = new ExifInterface(path);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int degrees = orientation == ExifInterface.ORIENTATION_ROTATE_90 ? 90
                    : orientation == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : orientation == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            if (degrees != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(degrees);
                Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
                if (rotated != bmp) bmp.recycle();
                bmp = rotated;
            }
        } catch (Exception e) {
            Log.w(TAG, "EXIF 회전 보정 실패: " + e.getMessage());
        }
        return bmp;
    }

    private void showResult(Bitmap photo) {
        recycleResult();
        currentResult = photo;
        cancelCountdown();
        layoutProcessing.setVisibility(View.GONE);
        layoutCamera.setVisibility(View.GONE);
        textCountdown.setVisibility(View.GONE);
        layoutResult.setVisibility(View.VISIBLE);
        
        imgResult.setImageBitmap(photo);
        imgResultTemplate.setImageResource(selectedTemplate.res);

        KidAnimator.popIn(layoutResultContainer, 0.8f);
        KidAnimator.slideIn(resultActions);
        isProcessing = false;
        robot.speak(TtsRequest.create("와아! 멋진 사진이 완성됐어!", false));
    }

    private void recycleResult() {
        if (imgResult != null) imgResult.setImageBitmap(null);
        if (currentResult != null && !currentResult.isRecycled()) {
            currentResult.recycle();
        }
        currentResult = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        cancelCountdown();
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
        cancelCountdown();
        releasePlayer();
        bgExecutor.shutdownNow();
        recycleResult();
    }
}
