package com.kidsFriend.global.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.kidsFriend.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RocketMenuManager {
    private final View rootMenu;
    private final List<View> buttons = new ArrayList<>();
    private final View aiChatButton;
    private final float radius;
    private boolean isMenuOpen = false;

    public RocketMenuManager(View rootMenu) {
        this.rootMenu = rootMenu;
        this.radius = rootMenu.getContext().getResources().getDisplayMetrics().density * MotionConstants.MENU_RADIUS_DP;

        buttons.add(rootMenu.findViewById(R.id.btn_menu_photo));
        buttons.add(rootMenu.findViewById(R.id.btn_menu_game));
        buttons.add(rootMenu.findViewById(R.id.btn_menu_music));
        buttons.add(rootMenu.findViewById(R.id.btn_menu_dance));
        buttons.add(rootMenu.findViewById(R.id.btn_menu_object_game));

        this.aiChatButton = rootMenu.findViewById(R.id.btn_menu_ai_chat);

        for (View v : buttons) {
            v.setAlpha(0f);
            v.setScaleX(MotionConstants.SCALE_MIN);
            v.setScaleY(MotionConstants.SCALE_MIN);
        }
        
        if (aiChatButton != null) {
            aiChatButton.setAlpha(0f);
            aiChatButton.setScaleX(MotionConstants.SCALE_MIN);
            aiChatButton.setScaleY(MotionConstants.SCALE_MIN);
        }
    }

    public void toggleMenu() {
        if (isMenuOpen) {
            closeMenu(null);
        } else {
            openMenu();
        }
    }

    public void openMenu() {
        if (isMenuOpen) return;
        isMenuOpen = true;
        rootMenu.setVisibility(View.VISIBLE);
        rootMenu.setAlpha(0f);
        rootMenu.animate().alpha(1f).setDuration(200).start();

        // 바깥 버튼은 원 둘레에 균등 분배(개수 무관), aiChat은 중앙(0,0).
        int outerCount = buttons.size();
        List<View> allButtons = new ArrayList<>(buttons);
        if (aiChatButton != null) allButtons.add(aiChatButton);

        for (int i = 0; i < allButtons.size(); i++) {
            View btn = allButtons.get(i);
            float tx = 0, ty = 0;

            // 바깥 버튼(중앙 aiChat 제외)만 반지름만큼 균등 배치
            if (i < outerCount) {
                double angle = 225.0 + (360.0 / outerCount) * i;
                double rad = Math.toRadians(angle);
                tx = (float) (radius * Math.cos(rad));
                ty = (float) (radius * Math.sin(rad));
            }

            KidAnimator.launch(btn, tx, ty);
            
            // Start idle animations after launch for ALL buttons
            final long delay = new Random().nextInt(800);
            btn.postDelayed(() -> {
                if (isMenuOpen) {
                    KidAnimator.floatAnimation(btn, 0);
                    KidAnimator.pulse(btn);
                }
            }, MotionConstants.LAUNCH_DURATION + delay);
        }
    }

    public void closeMenu(final View selectedView) {
        if (!isMenuOpen) return;
        isMenuOpen = false;

        List<View> allButtons = new ArrayList<>(buttons);
        if (aiChatButton != null) allButtons.add(aiChatButton);

        for (View btn : allButtons) {
            btn.animate().cancel(); // Stop idle animations
            if (btn == selectedView) {
                btn.animate()
                        .scaleX(MotionConstants.SCALE_OVERSHOOT)
                        .scaleY(MotionConstants.SCALE_OVERSHOOT)
                        .setDuration(MotionConstants.SELECTED_SCALE_DURATION)
                        .withEndAction(() -> collapseAll(null))
                        .start();
            }
        }
        
        if (selectedView == null) {
            collapseAll(null);
        }
    }

    private void collapseAll(final Runnable endAction) {
        List<View> allButtons = new ArrayList<>(buttons);
        if (aiChatButton != null) allButtons.add(aiChatButton);

        for (int i = 0; i < allButtons.size(); i++) {
            final boolean isLast = (i == allButtons.size() - 1);
            KidAnimator.collapse(allButtons.get(i), () -> {
                if (isLast) {
                    rootMenu.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                        rootMenu.setVisibility(View.INVISIBLE);
                        if (endAction != null) endAction.run();
                    }).start();
                }
            });
        }
    }
    
    public boolean isMenuOpen() {
        return isMenuOpen;
    }
}
