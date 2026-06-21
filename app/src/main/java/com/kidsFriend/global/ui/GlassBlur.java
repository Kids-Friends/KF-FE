package com.kidsFriend.global.ui;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;

import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;

import eightbitlab.com.blurview.BlurView;

/**
 * 글래스모피즘 패널용 BlurView 설정 헬퍼.
 *
 * <p>{@link BlurView#setupWith(ViewGroup)}는 API 31+에서는 GPU 하드웨어 블러(RenderEffect),
 * 그 이하에서는 RenderScript를 자동으로 선택한다. 패널 뒤(같은 {@code root} 안)의 콘텐츠가 흐려진다.</p>
 */
public final class GlassBlur {

    private GlassBlur() {}

    /**
     * @param activity      윈도우 배경(프레임 클리어용)을 얻기 위한 액티비티
     * @param blurView      블러를 입힐 글래스 패널
     * @param root          블러 대상(이 뷰그룹 안에서 blurView 뒤에 있는 것들이 흐려진다)
     * @param radius        블러 강도(권장 12~22)
     * @param overlayColor  유리 위에 얹는 반투명 워시 색상 리소스(가독성/광택)
     */
    public static void apply(Activity activity, BlurView blurView, ViewGroup root,
                             float radius, @ColorRes int overlayColor) {
        Drawable windowBackground = activity.getWindow().getDecorView().getBackground();
        blurView.setupWith(root)
                .setFrameClearDrawable(windowBackground)
                .setBlurRadius(radius)
                .setOverlayColor(ContextCompat.getColor(activity, overlayColor));
    }
}
