package com.kidsFriend;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.kidsFriend.domain.greeting.service.MainActivity;

/**
 * 전역 애플리케이션 클래스.
 * 시연 중 발생할 수 있는 모든 Uncaught Exception을 낚아채서 앱이 강제종료(Crash)되는 것을 막고,
 * MainActivity로 부드럽게 복구(Restart)시키는 최후의 방어선(Global Exception Handler) 역할을 수행합니다.
 */
public class KidsFriendApp extends Application {
    private static final String TAG = "KidsFriendApp";

    @Override
    public void onCreate() {
        super.onCreate();

        // 기존 시스템의 예외 처리기를 백업해둡니다.
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();

        // 전역 예외 처리기 설정
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "🔥 [CRITICAL] Uncaught Exception caught! Prevents app from crashing.", throwable);

            // 메인 스레드(UI)에서 발생한 에러인 경우 사용자 몰래 Toast 하나만 띄우고 앱을 초기 상태로 되돌립니다.
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    Toast.makeText(getApplicationContext(), "일시적인 오류가 발생했습니다. 잠시 후 다시 시도합니다.", Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {
                }
            });

            try {
                // 앱이 완전히 뻗는 것을 방지하기 위해 MainActivity를 강제로 다시 띄웁니다.
                // Intent에 FLAG_ACTIVITY_NEW_TASK와 FLAG_ACTIVITY_CLEAR_TASK를 주어 스택을 모두 날리고 깔끔하게 재시작.
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to restart MainActivity", e);
            }

            // 시스템 기본 핸들러(강제 종료 다이얼로그 띄움)를 호출하지 않습니다.
            // 즉, 앱이 OS 수준에서 죽었다고 보고하지 않고 백그라운드에서 조용히 메인화면으로 롤백합니다.
            
            // 프로세스 종료 (이 코드는 새 Activity가 뜨면서 기존 망가진 프로세스는 죽이되, OS 에러창은 막음)
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        });
    }
}