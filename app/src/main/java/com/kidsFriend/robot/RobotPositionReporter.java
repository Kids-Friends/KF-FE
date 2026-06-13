package com.kidsFriend.robot;

import android.util.Log;

import com.kidsFriend.data.api.ApiClient;
import com.kidsFriend.data.model.RobotLocationsRequest;
import com.kidsFriend.data.model.RobotPositionRequest;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.listeners.OnLocationsUpdatedListener;
import com.robotemi.sdk.navigation.listener.OnCurrentPositionChangedListener;
import com.robotemi.sdk.navigation.model.Position;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 테미의 매핑(자기 위치 인식) 정보를 KF_BE로 보고하는 레이어.
 *
 * <p>Temi는 맵 상에서 위치가 바뀔 때마다 {@link OnCurrentPositionChangedListener}로 (x, y, yaw)를 푸시한다.
 * 이 값을 스로틀링해서 {@code POST /api/robot-position}으로 보고하면, 운영자 대시보드(KF_WEB)가
 * 로봇 위치를 실시간으로 표시할 수 있다. 저장된 장소 목록도 1회/변경 시 보고한다.</p>
 *
 * <p>모든 SDK/네트워크 호출은 로봇이 없는 환경에서도 죽지 않도록 try/catch + fire-and-forget 으로 처리한다.</p>
 */
public class RobotPositionReporter implements
        OnCurrentPositionChangedListener, OnLocationsUpdatedListener {

    private static final String TAG = "RobotPositionReporter";
    private static final String ROBOT_ID = "TEMI_01"; // HW 센서와 동일한 데모용 로봇 ID
    private static final long MIN_INTERVAL_MS = 1000L; // 위치 보고 최소 간격(스로틀)

    private boolean registered = false;
    private long lastSentAt = 0L;

    public void register() {
        if (registered) {
            return;
        }
        try {
            Robot robot = Robot.getInstance();
            robot.addOnCurrentPositionChangedListener(this);
            robot.addOnLocationsUpdatedListener(this);
            registered = true;
            // 시작 시 현재 저장된 장소 목록을 1회 보고한다.
            try {
                onLocationsUpdated(robot.getLocations());
            } catch (RuntimeException ignored) {
                // 맵이 없으면 장소도 없음 — 무시
            }
            Log.d(TAG, "위치/장소 리스너 등록");
        } catch (RuntimeException e) {
            Log.w(TAG, "위치 리스너 등록 실패(로봇 환경이 아닐 수 있음): " + e.getMessage());
        }
    }

    public void unregister() {
        if (!registered) {
            return;
        }
        try {
            // 장소 리스너는 SDK에 remove API가 없어 위치 리스너만 해제한다(키오스크라 무방).
            Robot.getInstance().removeOnCurrentPositionChangedListener(this);
        } catch (RuntimeException e) {
            Log.w(TAG, "위치 리스너 해제 실패: " + e.getMessage());
        }
        registered = false;
    }

    @Override
    public void onCurrentPositionChanged(Position position) {
        if (position == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSentAt < MIN_INTERVAL_MS) {
            return; // 너무 잦은 보고 방지
        }
        lastSentAt = now;
        RobotPositionRequest req = new RobotPositionRequest(
                ROBOT_ID, position.getX(), position.getY(), position.getYaw(), position.getTiltAngle());
        try {
            ApiClient.getService().reportRobotPosition(req).enqueue(NOOP);
        } catch (RuntimeException e) {
            Log.w(TAG, "위치 보고 실패: " + e.getMessage());
        }
    }

    @Override
    public void onLocationsUpdated(List<String> locations) {
        RobotLocationsRequest req = new RobotLocationsRequest(ROBOT_ID, locations, "home base");
        try {
            ApiClient.getService().reportRobotLocations(req).enqueue(NOOP);
        } catch (RuntimeException e) {
            Log.w(TAG, "장소 보고 실패: " + e.getMessage());
        }
    }

    private static final Callback<Void> NOOP = new Callback<Void>() {
        @Override
        public void onResponse(Call<Void> call, Response<Void> response) {
        }

        @Override
        public void onFailure(Call<Void> call, Throwable t) {
        }
    };
}
