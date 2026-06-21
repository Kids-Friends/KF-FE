package com.kidsFriend.global.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 아이용 마이크로 인터랙션 + 리워드 애니메이션 모음.
 *
 * <p>설계 원칙(Temi 하드웨어): 짧고(120~800ms) 단순한 프로퍼티 애니메이션만 사용한다.
 * 파티클 시스템/무한 루프/물리 시뮬레이션 없음. 별 버스트·글로우는 일회성 임시 뷰를
 * <b>액티비티 content 루트(FrameLayout)에 절대좌표로</b> 추가했다가 끝나면 제거하므로,
 * 버튼이 들어있는 LinearLayout 등 부모 레이아웃을 흔들지 않는다.</p>
 *
 * <pre>
 * KidAnimator.bounce(view);     // 가벼운 통통 튀는 터치 피드백
 * KidAnimator.jelly(view);      // 젤리 스쿼시(주요 버튼/시작/카메라)
 * KidAnimator.success(view);    // 정답: 팝 + 초록 글로우 + 별 버스트
 * KidAnimator.error(view);      // 오답: 좌우 쉐이크 + 부드러운 빨강 플래시
 * KidAnimator.countdown(view);  // 카운트다운 숫자 팝
 * KidAnimator.slideIn(view);    // 아래에서 떠오르며 페이드인
 * KidAnimator.flash(view);      // 촬영 순간 흰 플래시 오버레이
 * KidAnimator.popIn(view, 0.8f);// 결과 프리뷰 등 팝 등장
 * </pre>
 */
public final class KidAnimator {

    private KidAnimator() {}

    // 권장 지속시간(ms)
    public static final long DUR_TOUCH = 180L;
    public static final long DUR_JELLY = 250L;
    public static final long DUR_REWARD = 600L;
    public static final long DUR_ERROR = 300L;
    public static final long DUR_COUNTDOWN = 500L;
    public static final long DUR_FLASH = 150L;

    private static final int COLOR_SUCCESS = 0x6600C853; // 반투명 초록 글로우
    private static final int COLOR_ERROR = 0x44E53935;   // 부드러운 빨강(공격적이지 않게)

    // ── 터치 피드백 ───────────────────────────────────────────────────────────

    /** Concept A: 통통 튀는 버튼. 살짝 눌렸다가(0.92) 오버슈트로 되돌아온다(~1.05). */
    public static void bounce(final View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.animate()
                .scaleX(MotionConstants.SCALE_PRESSED).scaleY(MotionConstants.SCALE_PRESSED)
                .setDuration(MotionConstants.BOUNCE_DURATION / 2)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> v.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(MotionConstants.BOUNCE_DURATION / 2)
                        .setInterpolator(new OvershootInterpolator(3f))
                        .start())
                .start();
    }

    /** Concept B: 젤리. 가로로 눌렸다 세로로 늘어났다 복귀(스쿼시&스트레치). 주요/시작/카메라 버튼용. */
    public static void jelly(View v) {
        if (v == null) return;
        v.animate().cancel();
        PropertyValuesHolder sx = PropertyValuesHolder.ofKeyframe(View.SCALE_X,
                Keyframe.ofFloat(0f, 1f),
                Keyframe.ofFloat(0.4f, 0.9f),
                Keyframe.ofFloat(0.7f, 1.05f),
                Keyframe.ofFloat(1f, 1f));
        PropertyValuesHolder sy = PropertyValuesHolder.ofKeyframe(View.SCALE_Y,
                Keyframe.ofFloat(0f, 1f),
                Keyframe.ofFloat(0.4f, 1.1f),
                Keyframe.ofFloat(0.7f, 0.95f),
                Keyframe.ofFloat(1f, 1f));
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v, sx, sy);
        a.setDuration(DUR_JELLY);
        a.start();
    }

    /** 클릭을 바운스 피드백과 묶어주는 재사용 리스너. */
    public static void onClick(View v, final View.OnClickListener listener) {
        if (v == null) return;
        v.setOnClickListener(view -> {
            bounce(view);
            if (listener != null) listener.onClick(view);
        });
    }

    // ── 리워드(정답/오답) ──────────────────────────────────────────────────────

    /** 정답: 카드 팝(1→1.1→1) + 초록 글로우 + 작은 별 버스트. */
    public static void success(View v) {
        if (v == null) return;
        popPulse(v, 1.1f, DUR_REWARD);
        glow(v, COLOR_SUCCESS, 500L);
        starBurst(v, 8);
    }

    /** 오답: 좌우 쉐이크 + 부드러운 빨강 플래시(아이가 혼나는 느낌이 들지 않게 약하게). */
    public static void error(View v) {
        if (v == null) return;
        float d = dp(v, 12f);
        ObjectAnimator shake = ObjectAnimator.ofFloat(v, View.TRANSLATION_X,
                0f, -d, d, -d * 0.66f, d * 0.66f, 0f);
        shake.setDuration(DUR_ERROR);
        shake.start();
        glow(v, COLOR_ERROR, DUR_ERROR);
    }

    // ── 카운트다운/플래시/등장 ──────────────────────────────────────────────────

    /** 카운트다운 숫자 팝: 스케일 0.3→1.3→1.0 + 페이드 0→1. 숫자가 바뀔 때마다 호출. */
    public static void countdown(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setAlpha(0f);
        v.setScaleX(0.3f);
        v.setScaleY(0.3f);
        PropertyValuesHolder sx = PropertyValuesHolder.ofKeyframe(View.SCALE_X,
                Keyframe.ofFloat(0f, 0.3f), Keyframe.ofFloat(0.6f, 1.3f), Keyframe.ofFloat(1f, 1f));
        PropertyValuesHolder sy = PropertyValuesHolder.ofKeyframe(View.SCALE_Y,
                Keyframe.ofFloat(0f, 0.3f), Keyframe.ofFloat(0.6f, 1.3f), Keyframe.ofFloat(1f, 1f));
        PropertyValuesHolder al = PropertyValuesHolder.ofKeyframe(View.ALPHA,
                Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(0.5f, 1f), Keyframe.ofFloat(1f, 1f));
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v, sx, sy, al);
        a.setDuration(DUR_COUNTDOWN);
        a.start();
    }

    /** 촬영 순간 흰 플래시: alpha 0→1→0. 전달한 흰색 오버레이 뷰를 잠깐 번쩍인다. */
    public static void flash(final View flashView) {
        if (flashView == null) return;
        flashView.animate().cancel();
        flashView.setAlpha(0f);
        flashView.setVisibility(View.VISIBLE);
        ObjectAnimator a = ObjectAnimator.ofFloat(flashView, View.ALPHA, 0f, 1f, 0f);
        a.setDuration(DUR_FLASH);
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                flashView.setVisibility(View.GONE);
            }
        });
        a.start();
    }

    /** 결과 프리뷰 등 팝 등장: from 배율에서 1.0으로 오버슈트 + 페이드인. */
    public static void popIn(View v, float from) {
        if (v == null) return;
        v.animate().cancel();
        v.setScaleX(from);
        v.setScaleY(from);
        v.setAlpha(0f);
        v.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(400)
                .setInterpolator(new OvershootInterpolator(2f))
                .start();
    }

    /** 아래(40dp)에서 떠오르며 페이드인. */
    public static void slideIn(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setTranslationY(dp(v, 40f));
        v.setAlpha(0f);
        v.animate()
                .translationY(0f).alpha(1f)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────────────

    /** 1→peak→1 펄스(스케일). */
    private static void popPulse(View v, float peak, long duration) {
        v.animate().cancel();
        v.setScaleX(1f);
        v.setScaleY(1f);
        PropertyValuesHolder sx = PropertyValuesHolder.ofKeyframe(View.SCALE_X,
                Keyframe.ofFloat(0f, 1f), Keyframe.ofFloat(0.5f, peak), Keyframe.ofFloat(1f, 1f));
        PropertyValuesHolder sy = PropertyValuesHolder.ofKeyframe(View.SCALE_Y,
                Keyframe.ofFloat(0f, 1f), Keyframe.ofFloat(0.5f, peak), Keyframe.ofFloat(1f, 1f));
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v, sx, sy);
        a.setDuration(duration);
        a.start();
    }

    /** 대상 뷰 영역 위에 잠깐 색 글로우를 번쩍인다(부모 레이아웃을 건드리지 않게 content 루트에 얹음). */
    private static void glow(View target, int color, long duration) {
        final ViewGroup root = contentRoot(target);
        if (root == null) return;
        float[] pos = positionIn(root, target);

        final View overlay = new View(target.getContext());
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(target, 28f));
        bg.setColor(color);
        overlay.setBackground(bg);
        overlay.setAlpha(0f);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Math.max(1, target.getWidth()), Math.max(1, target.getHeight()));
        root.addView(overlay, lp);
        overlay.setX(pos[0]);
        overlay.setY(pos[1]);

        ObjectAnimator a = ObjectAnimator.ofFloat(overlay, View.ALPHA, 0f, 1f, 0f);
        a.setDuration(duration);
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                root.removeView(overlay);
            }
        });
        a.start();
    }

    /** 대상 뷰 중심에서 별(⭐) 몇 개가 사방으로 퍼지며 사라진다(일회성). */
    private static void starBurst(View target, int count) {
        final ViewGroup root = contentRoot(target);
        if (root == null) return;
        float[] pos = positionIn(root, target);
        float cx = pos[0] + target.getWidth() / 2f;
        float cy = pos[1] + target.getHeight() / 2f;
        float distance = dp(target, 90f);

        for (int i = 0; i < count; i++) {
            final TextView star = new TextView(target.getContext());
            star.setText("⭐");
            star.setTextColor(Color.WHITE);
            star.setTextSize(24f);
            star.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            root.addView(star, lp);
            star.setX(cx);
            star.setY(cy);

            double angle = Math.PI * 2 * i / count;
            float tx = (float) Math.cos(angle) * distance;
            float ty = (float) Math.sin(angle) * distance;

            star.setScaleX(0.4f);
            star.setScaleY(0.4f);
            star.animate()
                    .translationXBy(tx).translationYBy(ty)
                    .scaleX(1.2f).scaleY(1.2f)
                    .rotation(180f)
                    .alpha(0f)
                    .setDuration(DUR_REWARD)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> root.removeView(star))
                    .start();
        }
    }

    /** 로켓 발사 애니메이션: 중앙에서 타겟 위치로 날아감. */
    public static void launch(View v, float targetX, float targetY) {
        if (v == null) return;
        v.animate().cancel();
        v.setAlpha(0f);
        v.setScaleX(MotionConstants.SCALE_MIN);
        v.setScaleY(MotionConstants.SCALE_MIN);
        v.setRotation(-45f);
        v.setTranslationX(0f);
        v.setTranslationY(0f);

        v.animate()
                .alpha(1f)
                .scaleX(MotionConstants.SCALE_IDLE)
                .scaleY(MotionConstants.SCALE_IDLE)
                .rotation(0f)
                .translationX(targetX)
                .translationY(targetY)
                .setDuration(MotionConstants.LAUNCH_DURATION)
                .setInterpolator(MotionConstants.OVERSHOOT_INTERPOLATOR)
                .start();
    }

    /** 메뉴 축소 애니메이션: 현재 위치에서 중앙으로 돌아가며 사라짐. */
    public static void collapse(View v, Runnable endAction) {
        if (v == null) return;
        v.animate().cancel();
        v.animate()
                .alpha(0f)
                .scaleX(0f)
                .scaleY(0f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(MotionConstants.COLLAPSE_DURATION)
                .withEndAction(endAction)
                .start();
    }

    /** 둥둥 떠있는 효과 (무한 반복). */
    public static ObjectAnimator floatAnimation(View v, long delay) {
        if (v == null) return null;
        ObjectAnimator animator = ObjectAnimator.ofFloat(v, View.TRANSLATION_Y,
                v.getTranslationY(), v.getTranslationY() - 20f, v.getTranslationY());
        animator.setDuration(MotionConstants.FLOAT_DURATION);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setRepeatMode(ObjectAnimator.REVERSE);
        animator.setStartDelay(delay);
        animator.start();
        return animator;
    }

    /** 숨쉬는 효과 (무한 반복). */
    public static void pulse(View v) {
        if (v == null) return;
        PropertyValuesHolder sx = PropertyValuesHolder.ofKeyframe(View.SCALE_X,
                Keyframe.ofFloat(0f, 1f),
                Keyframe.ofFloat(0.5f, MotionConstants.SCALE_PULSE_MAX),
                Keyframe.ofFloat(1f, 1f));
        PropertyValuesHolder sy = PropertyValuesHolder.ofKeyframe(View.SCALE_Y,
                Keyframe.ofFloat(0f, 1f),
                Keyframe.ofFloat(0.5f, MotionConstants.SCALE_PULSE_MAX),
                Keyframe.ofFloat(1f, 1f));
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(v, sx, sy);
        animator.setDuration(MotionConstants.PULSE_DURATION);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setRepeatMode(ObjectAnimator.REVERSE);
        animator.start();
    }
    private static ViewGroup contentRoot(View view) {
        View root = view.getRootView().findViewById(android.R.id.content);
        return (root instanceof ViewGroup) ? (ViewGroup) root : null;
    }

    /** target의 좌상단을 root 좌표계로 변환. */
    private static float[] positionIn(View root, View target) {
        int[] r = new int[2];
        int[] t = new int[2];
        root.getLocationOnScreen(r);
        target.getLocationOnScreen(t);
        return new float[]{t[0] - r[0], t[1] - r[1]};
    }

    private static float dp(View v, float dp) {
        return dp * v.getResources().getDisplayMetrics().density;
    }
}
