package com.kidsFriend.global.ui;

import android.transition.AutoTransition;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;

import androidx.core.view.animation.PathInterpolatorCompat;

/**
 * 화면/스텝 전환 모션.
 *
 * <p>레이아웃을 갑자기 교체하지 않고 부드럽게 잇는다. 프레임워크 {@link TransitionManager}(API 19+)와
 * fast-out-slow-in 곡선만 사용하므로 추가 의존성이 없다.</p>
 *
 * <pre>
 * MotionManager.enter(content);            // 인트로 → 메인: 아래에서 떠오르며 등장
 * MotionManager.beginStepTransition(root); // 다음 visibility 변경을 250ms로 부드럽게(스텝 전환)
 * </pre>
 */
public final class MotionManager {

    private MotionManager() {}

    public static final long DUR_ENTER = 400L;
    public static final long DUR_STEP = 250L;

    /** Material fast-out-slow-in 곡선. */
    public static Interpolator fastOutSlowIn() {
        return PathInterpolatorCompat.create(0.4f, 0f, 0.2f, 1f);
    }

    /** Scenario A: UI가 아래(40dp)에서 부드럽게 떠오르며 나타난다(alpha 0→1, translationY 40dp→0). */
    public static void enter(View content) {
        if (content == null) return;
        float dy = 40f * content.getResources().getDisplayMetrics().density;
        content.animate().cancel();
        content.setAlpha(0f);
        content.setTranslationY(dy);
        content.animate()
                .alpha(1f).translationY(0f)
                .setDuration(DUR_ENTER)
                .setInterpolator(fastOutSlowIn())
                .start();
    }

    /**
     * Scenario B: 스텝 전환. 호출 직후 {@code scene} 안에서 일어나는 visibility/레이아웃 변경을
     * 250ms AutoTransition으로 부드럽게 잇는다. (Activity 재생성/하드 전환을 피한다.)
     */
    public static void beginStepTransition(ViewGroup scene) {
        if (scene == null) return;
        Transition t = new AutoTransition();
        t.setDuration(DUR_STEP);
        t.setInterpolator(fastOutSlowIn());
        TransitionManager.beginDelayedTransition(scene, t);
    }
}
