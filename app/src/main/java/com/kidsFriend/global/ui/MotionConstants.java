package com.kidsFriend.global.ui;

import android.view.animation.OvershootInterpolator;

public class MotionConstants {
    // Durations
    public static final int BOUNCE_DURATION = 180;
    public static final int LAUNCH_DURATION = 600;
    public static final int COLLAPSE_DURATION = 250;
    public static final int FLOAT_DURATION = 2500;
    public static final int PULSE_DURATION = 2200;
    public static final int SELECTED_SCALE_DURATION = 150;

    // Scales
    public static final float SCALE_PRESSED = 0.92f;
    public static final float SCALE_OVERSHOOT = 1.15f;
    public static final float SCALE_IDLE = 1.0f;
    public static final float SCALE_MIN = 0.2f;
    public static final float SCALE_PULSE_MAX = 1.03f;

    // Radius (dp)
    public static final int MENU_RADIUS_DP = 280;

    // Interpolators
    public static final OvershootInterpolator OVERSHOOT_INTERPOLATOR = new OvershootInterpolator(2.0f);
}
