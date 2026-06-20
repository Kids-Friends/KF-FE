package com.kidsFriend.domain.photo.service;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
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
import android.widget.VideoView;

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

import com.google.common.util.concurrent.ListenableFuture;
import com.kidsFriend.R;
import com.kidsFriend.global.ui.KidAnimator;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 사진 찍기 놀이.
 *
 * <p>흐름: <b>인트로 영상</b> → <b>템플릿 선택(4색)</b> → <b>1장 촬영</b>(카운트다운) → <b>합성</b>(촬영
 * 사진을 템플릿의 투명 창에 채우고 템플릿을 위에 덮음) → 결과 표시.</p>
 *
 * <p>안정성: 촬영 사진은 큰 해상도라 그대로 Bitmap으로 만들면 OOM이 날 수 있어 {@code inSampleSize}로
 * 다운샘플 디코드한다. 합성은 백그라운드 스레드에서 {@code try/catch(Throwable)}로 감싸 OOM/예외가 나도
 * 카메라 화면에서 멈추지 않고 안내와 함께 템플릿 선택으로 복귀한다. 촬영은 {@code isProcessing} 플래그로
 * 중복(연타로 인한 다중 촬영)을 막고, {@link CountDownTimer}와 결과 비트맵은 생명주기에 맞춰 정리한다.</p>
 */
public class PhotoPlayActivity extends AppCompatActivity {
    private static final String TAG = "PhotoPlayActivity";
    private static final int PERMISSION_REQUEST_CODE = 2001;

    /** 합성 입력 사진의 긴 변 상한(px). 템플릿(1536×1024) 해상도에 맞춰 다운샘플 → OOM 방지. */
    private static final int MAX_PHOTO_DIM = 1536;

    /** 템플릿 원본 해상도(창 좌표의 기준). 디코드 결과가 이와 다르면 창 좌표를 비례 보정한다. */
    private static final int TEMPLATE_W = 1536;
    private static final int TEMPLATE_H = 1024;

    /** 템플릿별 가운데 투명 창 사각형(원본 1536×1024 픽셀 기준, 알파 투영 분석으로 측정). */
    private enum Template {
        BLUE(R.drawable.camera_template_blue, 314, 148, 1308, 816),
        GREEN(R.drawable.camera_template_green, 316, 148, 1278, 826),
        PINK(R.drawable.camera_template_pink, 314, 146, 1280, 808),
        YELLOW(R.drawable.camera_template_yellow, 314, 148, 1274, 834);

        final int res;
        final Rect window;

        Template(int res, int l, int t, int r, int b) {
            this.res = res;
            this.window = new Rect(l, t, r, b);
        }
    }

    private View layoutIntro, layoutTemplate, layoutCamera, layoutResult, layoutProcessing;
    private VideoView videoIntro;
    private PreviewView cameraPreview;
    private ImageView imgCameraTemplate; // 촬영 화면에 얹는 선택 템플릿(라이브 프레임 미리보기)
    private TextView textCountdown, textProgress;
    private ImageView imgResult, imgResultTemplate;
    private View flashOverlay, resultActions, layoutResultContainer;

    private ImageCapture imageCapture;
    private Robot robot;
    private Template selectedTemplate = Template.BLUE;

    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private CountDownTimer countDownTimer;
    private boolean isProcessing = false;   // 촬영 세션 진행 중(중복 촬영 차단)
    private boolean introFinished = false;
    private Bitmap currentResult;        // 현재 표시 중인 합성 결과(생명주기 관리)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_play);

        robot = Robot.getInstance();
        layoutIntro = findViewById(R.id.layout_step_intro);
        videoIntro = findViewById(R.id.video_intro);
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

    /** 진입 인트로 영상 1회 재생. 끝나거나 '건너뛰기'를 누르면 템플릿 선택으로 넘어간다. */
    private void startIntro() {
        layoutIntro.setVisibility(View.VISIBLE);
        layoutTemplate.setVisibility(View.GONE);
        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.camera_intro);
        videoIntro.setVideoURI(uri);
        videoIntro.setOnPreparedListener(mp -> {
            // 비디오 크기에 맞춰 중앙 정렬 보정 (0.0 기준 방지)
            int videoWidth = mp.getVideoWidth();
            int videoHeight = mp.getVideoHeight();
            float viewWidth = videoIntro.getWidth();
            float viewHeight = videoIntro.getHeight();

            if (videoWidth > 0 && videoHeight > 0 && viewWidth > 0 && viewHeight > 0) {
                float videoAspect = (float) videoWidth / videoHeight;
                float viewAspect = viewWidth / viewHeight;

                if (viewAspect > videoAspect) {
                    // 뷰가 비디오보다 가로로 김 -> 비디오의 좌우를 늘려야 함
                    videoIntro.setScaleX(1.1f * (viewAspect / videoAspect));
                    videoIntro.setScaleY(1.1f);
                } else {
                    // 뷰가 비디오보다 세로로 김 -> 비디오의 위아래를 늘려야 함
                    videoIntro.setScaleX(1.1f);
                    videoIntro.setScaleY(1.1f * (videoAspect / viewAspect));
                }
            } else {
                videoIntro.setScaleX(1.1f);
                videoIntro.setScaleY(1.1f);
            }

            mp.setLooping(false);
            videoIntro.start();
        });
        videoIntro.setOnCompletionListener(mp -> finishIntro());
        videoIntro.setOnErrorListener((mp, what, extra) -> {
            Log.w(TAG, "인트로 영상 오류 what=" + what + " extra=" + extra);
            finishIntro();
            return true;
        });
    }

    /** 인트로 종료 → 준비한 기능(템플릿 선택)부터 표시. (완료/건너뛰기 중복 호출에 안전) */
    private void finishIntro() {
        if (introFinished) return;
        introFinished = true;
        if (videoIntro != null) videoIntro.stopPlayback();
        layoutIntro.setVisibility(View.GONE);
        layoutTemplate.setVisibility(View.VISIBLE);
        KidAnimator.slideIn(layoutTemplate);
    }

    // ── 촬영 흐름 ────────────────────────────────────────────────────────────

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
        }
    }

    /** 다시 찍기: 카메라/결과/처리중을 닫고 템플릿 선택으로 돌아간다(세션 초기화). */
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

    /** 촬영 직후 합성하는 동안 보여주는 '처리 중' 로딩 화면. */
    private void showProcessing() {
        cancelCountdown();
        textCountdown.setVisibility(View.GONE);
        layoutCamera.setVisibility(View.GONE);
        layoutProcessing.setVisibility(View.VISIBLE);
        KidAnimator.slideIn(layoutProcessing);
    }

    private void selectTemplate(Template tpl) {
        if (isProcessing) return; // 연타로 인한 다중 촬영/다중 카메라 바인딩 차단
        isProcessing = true;
        selectedTemplate = tpl;
        imgCameraTemplate.setImageResource(tpl.res); // 촬영 화면에 같은 템플릿을 라이브로 얹음
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

                // 하드웨어 성능을 고려하여 촬영 해상도를 2048x1536(약 3MP)으로 제한하여 타임아웃 방지
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
                    KidAnimator.countdown(textCountdown); // 숫자가 바뀔 때마다 팝
                    robot.speak(TtsRequest.create(String.valueOf(seconds), false));
                }
            }

            @Override
            public void onFinish() {
                textCountdown.setVisibility(View.GONE);
                KidAnimator.flash(flashOverlay); // 촬영 순간 흰 플래시
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

        // 촬영 즉시 로딩 화면을 띄워 멈춘 것처럼 보이지 않게 한다.
        showProcessing();

        File photoFile = new File(getExternalCacheDir(), "kf_photo.jpg");
        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        // 콜백 실행자를 백그라운드(bgExecutor)로 지정 → 디코드/합성을 메인 스레드 밖에서 수행.
        // UI 갱신(showResult/안내)만 runOnUiThread로 메인에서 처리한다.
        imageCapture.takePicture(options, bgExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        robot.speak(TtsRequest.create("찰칵!", false));

                        Bitmap photo = null;
                        try {
                            photo = decodeSampled(photoFile, MAX_PHOTO_DIM); // inSampleSize 다운샘플 및 회전 보정
                            if (photo == null) {
                                throw new IllegalStateException("사진 디코드 결과가 null");
                            }
                            
                            // 합성 과정을 생략하고 촬영된 사진을 그대로 사용
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

    /** 어떤 단계에서 실패하든 카메라에서 멈추지 않도록 안내 후 템플릿 선택으로 복귀. */
    private void onCaptureFailed(String message) {
        Log.w(TAG, "촬영 실패 복귀: " + message);
        robot.speak(TtsRequest.create(message, false));
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        showTemplateStep();
    }

    // ── 합성 ─────────────────────────────────────────────────────────────────

    /**
     * 촬영 사진을 템플릿의 투명 창에 center-crop으로 채우고, 그 위에 템플릿을 덮어 최종 1장을 만든다.
     * 그리기 순서: 흰 배경 → 창 영역에 사진 → 템플릿(투명 창으로 사진이 비쳐 보임).
     */
    private Bitmap composeWithTemplate(Bitmap photo, Template tpl) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false; // drawable-nodpi라 원본 1536×1024 그대로 디코드(창 좌표 일치)
        Bitmap template = BitmapFactory.decodeResource(getResources(), tpl.res, opts);
        if (template == null) {
            throw new IllegalStateException("템플릿 디코드 실패: " + tpl);
        }
        try {
            // 해상도 안전장치: 디코드된 템플릿이 기준(1536×1024)과 다르면(밀도 스케일 등) 창 좌표를 비례 보정.
            Rect window = scaledWindow(tpl, template.getWidth(), template.getHeight());

            Bitmap result = Bitmap.createBitmap(template.getWidth(), template.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);
            canvas.drawColor(Color.WHITE); // 템플릿 가장자리 투명부가 검게 나오지 않도록

            Rect src = centerCropSrc(photo.getWidth(), photo.getHeight(), window.width(), window.height());
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            canvas.drawBitmap(photo, src, window, paint);
            canvas.drawBitmap(template, 0, 0, null);
            return result;
        } finally {
            template.recycle(); // 중간 비트맵은 성공/실패와 무관하게 회수
        }
    }

    /** 템플릿 디코드 크기가 기준(1536×1024)과 다르면 창 좌표를 같은 비율로 보정한다. */
    private Rect scaledWindow(Template tpl, int decodedW, int decodedH) {
        if (decodedW == TEMPLATE_W && decodedH == TEMPLATE_H) {
            return tpl.window;
        }
        float sx = (float) decodedW / TEMPLATE_W;
        float sy = (float) decodedH / TEMPLATE_H;
        return new Rect(
                Math.round(tpl.window.left * sx), Math.round(tpl.window.top * sy),
                Math.round(tpl.window.right * sx), Math.round(tpl.window.bottom * sy));
    }

    /** 목적지 비율(dw:dh)에 맞춰 사진 중앙을 잘라낼 src 사각형(center-crop). */
    private Rect centerCropSrc(int sw, int sh, int dw, int dh) {
        double dstAspect = (double) dw / dh;
        double srcAspect = (double) sw / sh;
        int cw, ch;
        if (srcAspect > dstAspect) { // 원본이 더 가로로 김 → 좌우를 자름
            ch = sh;
            cw = (int) Math.round(sh * dstAspect);
        } else { // 원본이 더 세로로 김 → 위아래를 자름
            cw = sw;
            ch = (int) Math.round(sw / dstAspect);
        }
        int left = (sw - cw) / 2;
        int top = (sh - ch) / 2;
        return new Rect(left, top, left + cw, top + ch);
    }

    /** 긴 변을 maxDim 이하로 다운샘플 디코드(OOM 방지) 후 EXIF 회전을 반영한다. */
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

    /** EXIF 방향 메타만 있고 픽셀은 안 돌아간 경우 실제로 회전시킨다. */
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
            Log.w(TAG, "EXIF 회전 보정 실패(무시): " + e.getMessage());
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

        KidAnimator.popIn(layoutResultContainer, 0.8f); // 사진+템플릿 전체가 팝 등장
        KidAnimator.slideIn(resultActions);     // 액션 버튼이 아래에서 떠오름
        isProcessing = false; // 결과까지 도달 → 다시 찍기 가능
        robot.speak(TtsRequest.create("와아! 멋진 사진이 완성됐어!", false));
    }

    /** 이전 결과 비트맵을 화면에서 떼고 메모리를 회수한다. */
    private void recycleResult() {
        if (imgResult != null) imgResult.setImageBitmap(null);
        if (currentResult != null && !currentResult.isRecycled()) {
            currentResult.recycle();
        }
        currentResult = null;
    }

    // ── 생명주기 ────────────────────────────────────────────────────────────

    @Override
    protected void onPause() {
        super.onPause();
        cancelCountdown();
        if (videoIntro != null) videoIntro.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelCountdown();
        if (videoIntro != null) videoIntro.stopPlayback();
        bgExecutor.shutdownNow();
        recycleResult();
    }
}
