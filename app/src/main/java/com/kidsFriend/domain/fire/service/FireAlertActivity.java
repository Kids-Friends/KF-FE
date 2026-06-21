package com.kidsFriend.domain.fire.service;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.kidsFriend.domain.sensor.service.SensorWebSocketClient;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;

/**
 * 화재경보 전체화면 경고.
 * KF_AD 의 FIRE_ALARM(DETECTED) 이벤트가 WS 로 들어오면 KidsFriendApp 이 이 화면을 띄운다.
 * 진입 시 음성으로 대피 안내를 하고, FIRE_ALARM(CLEARED) 가 오면 자동으로 닫힌다.
 *
 * 레이아웃은 R 리소스 결합을 피하기 위해 코드로 구성한다(안전 오버레이라 단순/강조가 목적).
 */
public class FireAlertActivity extends AppCompatActivity
        implements SensorWebSocketClient.FireAlarmListener {

    private static final String TAG = "FireAlertActivity";
    private static final String SAFETY_MESSAGE =
            "불이 났어요! 침착하게 선생님을 따라 계단으로 대피하세요!";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        setContentView(buildView());
        speakSafety();
    }

    private View buildView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#D32F2F")); // 경고 레드
        int pad = dp(32);
        root.setPadding(pad, pad, pad, pad);

        TextView icon = new TextView(this);
        icon.setText("🔥");
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 96);
        icon.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("불이 났어요!");
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 56);
        title.setGravity(Gravity.CENTER);

        TextView message = new TextView(this);
        message.setText("침착하게 선생님을 따라\n계단으로 대피하세요!");
        message.setTextColor(Color.WHITE);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        msgLp.topMargin = dp(16);
        message.setLayoutParams(msgLp);

        Button close = new Button(this);
        close.setText("확인");
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                dp(220), LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = dp(40);
        close.setLayoutParams(btnLp);

        root.addView(icon);
        root.addView(title);
        root.addView(message);
        root.addView(close);
        return root;
    }

    private void speakSafety() {
        try {
            Robot.getInstance().speak(TtsRequest.create(SAFETY_MESSAGE, false));
        } catch (Exception e) {
            Log.w(TAG, "TTS 실패", e);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onStart() {
        super.onStart();
        SensorWebSocketClient.addFireAlarmListener(this);
        try {
            ActivityInfo activityInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activityInfo = getPackageManager().getActivityInfo(getComponentName(),
                        PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA));
            } else {
                activityInfo = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
            }
            Robot.getInstance().onStart(activityInfo);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Temi activity metadata is not available.", e);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        SensorWebSocketClient.removeFireAlarmListener(this);
    }

    @Override
    public void onFireAlarm(boolean detected) {
        if (detected) {
            // 경고가 떠 있는 동안 새 DETECTED 가 오면 안내만 다시 들려준다.
            speakSafety();
        } else {
            // 화재 해제 → 경고 화면 자동 닫기
            finish();
        }
    }
}
