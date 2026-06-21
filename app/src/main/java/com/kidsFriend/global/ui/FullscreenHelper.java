package com.kidsFriend.global.ui;

import android.app.Activity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class FullscreenHelper {

    /**
     * 액티비티를 몰입형 전체화면으로 설정합니다.
     * 상단 바, 내비게이션 바를 숨기고 전체 화면을 사용하게 합니다.
     */
    public static void setFullscreen(Activity activity) {
        if (activity == null) return;

        Window window = activity.getWindow();
        
        // 화면 꺼짐 방지
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // WindowCompat을 사용하여 시스템 바 제어
        WindowCompat.setDecorFitsSystemWindows(window, false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }
}
