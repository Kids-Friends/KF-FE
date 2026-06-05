# KNOWN RISKS & RECOVERY GUIDE

본 문서는 **"100% 시연 성공률 달성"**을 위한 신규 발굴된 20개 이상의 잠재적 위험 요소와 그 해결/우회 방안을 정리한 기술 부채 및 복구 가이드입니다. 

## 🚨 신규 발굴된 맹점 및 위험 요소 (P0~P2)

### 1. UI/UX 및 하드웨어 조작
1. **[P0] 하드웨어 볼륨 버튼 오작동**: 사용자가 로봇 이동 중 볼륨 버튼을 눌러 음소거(0%) 상태가 됨. -> `systemWatchdog`이 10초마다 볼륨을 80%로 강제 원복하도록 수정 완료.
2. **[P0] 스피커 하드웨어 고장**: 소리가 물리적으로 아예 안 남. -> 향후 자막 UI 옵션 추가 필요.
3. **[P1] 다중 클릭(Click Spamming)**: 뒤로가기나 메뉴 버튼을 연속 클릭하여 액티비티가 수십 개 쌓임. -> `AndroidManifest.xml`에 모든 액티비티를 `singleTop` 설정 완료.
4. **[P1] 화면 방향 센서 이상**: 이동 중 로봇이 흔들리며 가로/세로 화면 전환 시도 발생 시 앱 리로드. -> `screenOrientation="landscape"`, `configChanges` 떡칠로 방어 완료.
5. **[P2] 키오스크 모드 풀림**: `robot.setKioskModeOn(true)`가 OS에 의해 일시적으로 풀림. -> `systemWatchdog`이 10초마다 재활성화.
6. **[P2] 멀티 터치 제스처로 인한 OS 메뉴 노출**: 네 손가락 스와이프 등 Temi OS의 숨겨진 제스처 발생. -> `UI_MODE=4` (풀스크린) 고정으로 최소화.

### 2. 음성 (STT / TTS) 및 오디오
7. **[P0] 안드로이드 SpeechRecognizer 무응답 (Deadlock)**: 행사장의 백색 소음으로 인해 STT가 끝을 맺지 못하고 `onResults` 콜백 영구 미발생. -> 오퍼레이터 버튼 `Long Click` 시 강제로 텍스트("퀴즈 풀고 싶어") 주입하는 백도어 마련.
8. **[P1] 하울링 현상 (자신의 목소리 인식)**: TTS가 끝나기 전에 STT가 활성화되어 로봇이 자기 말을 인식. -> TTS 재생 길이 기반 예상 시간 계산(`estimatedMs`) 딜레이 적용 완료.
9. **[P1] Temi TTS 엔진 Hang**: `robot.speak()` 이후 콜백이 오지 않음. -> 45초 `conversationWatchdog`을 통해 무조건 IDLE 상태로 복구되도록 구현 완료.
10. **[P2] 오디오 포커스(Audio Focus) 뺏김**: 백그라운드 앱(또는 알림)이 재생되며 TTS 소리가 먹힘. -> AudioManager 강제화로 방어 중.

### 3. 통신 및 백엔드 (BE/HW)
11. **[P0] ngrok 도메인 만료/변경**: 시연 직전 ngrok 세션이 죽어 URL이 변경됨. -> 예약 고정 도메인(`avengeful-shaunte-revolvingly.ngrok-free.dev`) 사용 및 터널 자동화 스크립트로 방어 완료.
12. **[P1] 라즈베리파이 Python 크래시**: `mock_sensor.py` 또는 카메라 프로세스가 죽어 센서 이벤트가 안 옴. -> FE `MainActivity`의 화면(얼굴, 텍스트) 터치 제스처를 통해 센서 이벤트 수동 발생 조치 완료.
13. **[P1] 클라이언트 ID 세션 증발**: SharedPreferences에서 `clientId`를 못 가져옴. -> `TemiRepository`에서 "테미친구", 1004점이라는 든든한 하드코딩 Mock 데이터로 덮어쓰기 완료.
14. **[P2] 대용량 JSON 파싱 시 Main Thread 블로킹**: ANR 발생 가능성. -> 통신 결과가 매우 짧은 구조라 현재는 안전.
15. **[P2] 네트워크 타임아웃 미세 설정 실패**: 핑이 느린 환경에서 Retrofit 기본 10초 대기 중 화면 멈춤. -> 비동기 Callback(`enqueue`) 사용 및 타임아웃 시 즉시 Mock 응답.

### 4. 로봇 네비게이션 및 센서
16. **[P0] LiDAR 센서 고장**: `beWithMe()` 실행 시 사람을 못 찾거나 충돌 위험. -> 시연의 안전을 위해 기본적으로 `tiltAngle()`과 `speak()`만 사용하도록 주행 로직 주석 처리(OFF) 완료.
17. **[P1] ToF 센서 폭주 (DDoS)**: 물체가 가까이 있을 때 초당 수십 개의 HTTP POST 발생. -> 파이썬 스크립트 단에서 3초 `send_interval` 스로틀링(Throttling) 적용 완료.
18. **[P2] 로봇 배터리 방전**: 전원 코드가 뽑힌 채로 장시간 대기하여 0%에 도달. -> 화면 텍스트 2번 클릭 시 배터리 경고 알림(`LOW_BATTERY`) 모의 실행하여 충전 필요성을 귀엽게 알림.

### 5. 라이프사이클 및 메모리
19. **[P1] 메모리 릭(Memory Leak)**: `SensorEventPoller`나 `Handler`의 Runnable이 Activity 종료 시 해제 안 됨. -> `onPause()` / `onDestroy()`에 `removeCallbacksAndMessages(null)` 적용 완료.
20. **[P1] 런타임 권한(Permission) 팝업**: 시연 중 마이크 권한 요청 팝업이 떠서 흐름이 끊김. -> `onStart/onResume` 시 권한 체크하여 없으면 바로 재요청(시연 전 세팅으로 방어).
21. **[P2] 백그라운드 킬(Doze Mode)**: 화면이 켜진 채로 방치되다 배터리 최적화로 프로세스가 정지됨. -> `WAKE_LOCK` 권한과 `FLAG_KEEP_SCREEN_ON` 적용으로 방어 완료.
22. **[P0] 알 수 없는 런타임 강제 종료 (Uncaught Exception)**: NullPointer 등 예상치 못한 에러 발생 시 OS에서 "앱이 중지되었습니다" 다이얼로그 노출. -> `KidsFriendApp`에 `GlobalExceptionHandler`를 등록해 에러를 씹고 조용히 `MainActivity`를 재시작하도록 조치(Crash Zero 달성).
23. **[P2] TTS 엔진 런타임 크래시**: `Robot.getInstance().speak()`가 Temi 코어 에러로 죽음. -> 기존 `RuntimeException`에서 최상위 `Exception` Catch로 방어 범위 확장 완료.
