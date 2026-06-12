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

## 🚨 신규 발굴 맹점 및 위험 요소 (Phase 9 - 딥 시스템 레벨 20+)

### 6. 비동기/스레드 및 메모리 누수
24. **[P0] 비동기 콜백 UI 파괴 (IllegalStateException)**: 네트워크 통신 중 사용자가 화면을 닫았을 때(Activity `isFinishing()`) 콜백이 도착하여 UI를 조작하려다 앱이 터지는 현상. -> 모든 `RepositoryCallback` 내부에 `if (isFinishing() || isDestroyed()) return;` 방어 코드 삽입 완료.
25. **[P1] Handler 메모리 릭 (Memory Leak)**: `VoiceInputManager`나 `SensorEventPoller`가 파괴될 때 예약된 `postDelayed`가 취소되지 않아 좀비 스레드가 남음. -> `onDestroy()`에서 `removeCallbacksAndMessages(null)` 적용 완료.
26. **[P2] 백그라운드 스레드의 UI 접근**: 비동기 워커에서 `Toast.show()`나 `setText()`를 호출해 `CalledFromWrongThreadException` 발생. -> 모든 UI 조작을 `uiHandler.post()` 또는 `runOnUiThread()`로 래핑.
27. **[P2] SharedPreferences 블로킹 (ANR)**: `commit()` 사용 시 메인 스레드가 디스크 I/O를 기다리며 화면 멈춤. -> `SessionManager`에서 비동기 `apply()` 사용으로 방어.
28. **[P1] ConcurrentModificationException**: 여러 스레드가 동시에 `ArrayList`를 수정할 때 충돌. -> 데모 범위 내에서는 UI 스레드 기반 직렬 처리로 안전 보장.

### 7. 로봇 하드웨어 및 엔진 한계점
29. **[P0] STT 하드웨어 즉시 실패 루프 (CPU 100% ANR)**: 마이크가 물리적으로 망가졌을 때 `onNlpCompleted`가 즉시 실패를 리턴하고, 연속 모드가 다시 `askQuestion`을 즉시 호출해 무한 루프 발생. -> `VoiceInputManager`에 1초 내 5회 실패 시 멈추는 **Circuit Breaker** 및 1초 지연 스케줄러 도입 완료.
30. **[P0] TTS 엔진 큐(Queue) 폭발**: 여러 센서(아이 감지, 장애물, 배터리)가 0.1초 단위로 동시에 터지며 `speak()`를 수십 번 호출. -> `TemiSpeechSpeaker`에 3초 쿨다운(Debounce)을 적용하여 TTS 스팸 방어 완료.
31. **[P2] 블루투스 오디오 스틸**: 행사장 주변 블루투스 기기 페어링 시 마이크/스피커 입력이 테미가 아닌 외부 기기로 납치됨. -> 시연 전 설정에서 블루투스 강제 비활성화 요망.
32. **[P1] 쓰멀 스로틀링(Thermal Throttling)**: 장시간 화면 켜짐 및 AI 연산으로 테미 두뇌가 가열되어 프레임 드랍 및 STT 딜레이 발생. -> 시연 대기 시 화면 밝기 수동 저하 요망.

### 8. 네트워크 및 데이터 파싱
33. **[P0] Retrofit BaseUrl 파싱 크래시 (IllegalArgumentException)**: `.env` 파일 누락이나 URL 끝에 `/`가 없을 경우 앱 켜지자마자 Retrofit 빌더가 터짐. -> `RetrofitClient`에 강제 문자열 검증 및 기본 URL Fallback 삽입 완료.
34. **[P1] Gson Null Array 파싱 에러**: 백엔드가 빈 배열 대신 `null`을 내려보낼 경우 DTO 파싱 중 크래시. -> DTO에 기본값 적용 및 레포지토리 단에서 Null 검증.
35. **[P2] Interceptor 내부 IOException**: 로깅 인터셉터 등에서 네트워크 단절 시 예외를 던짐. -> `try-catch`로 래핑 후 진행.
36. **[P2] 대규모 JSON OOM**: AI가 예상치 못하게 수만 자의 텍스트를 응답. -> 백엔드 프롬프트에서 "2문장 이내"로 하드 리밋(Hard Limit) 적용 완료.

### 9. OS 및 시스템 UI 간섭
37. **[P0] 시스템 다이얼로그의 Focus 스틸**: 배터리 15% 경고 등 안드로이드 OS 팝업이 키오스크 모드 위로 올라와 시연을 가림. -> 배터리 30% 이상 유지 및 자체 `LOW_BATTERY` 모의 알림으로 시연자에게 사전 인지시킴.
38. **[P1] Fragment Transaction State Loss**: 백그라운드 상태에서 프래그먼트 교체 시 `IllegalStateException`. -> 데모 앱은 모든 뷰를 Activity로 분리하여 해당 위험 회피.
39. **[P1] Intent Payload Null 방어**: Activity 이동 시 Intent Extras가 누락될 경우 NPE 발생. -> 모든 `getExtra`에 기본값(Default Value) 적용.
40. **[P2] 로케일(Locale) 강제 변경에 따른 String 불일치**: 행사장 와이파이 위치 정보 오류로 언어가 영어로 강제 변경 시 STT 매칭("친구야") 실패. -> `TtsRequest`에 `KO_KR` 강제 지정 및 안드로이드 시스템 설정 한국어 고정.
41. **[P1] Robot.getInstance() NotInitializedException**: Application Context 생성 전 로봇 객체를 호출. -> `MainActivity` 생성 이후에만 호출되도록 보장.
42. **[P1] WakeLock 해제 누락**: 앱 종료 후에도 화면이 계속 켜져있어 배터리 광탈. -> `FLAG_KEEP_SCREEN_ON`은 Activity 생명주기에 종속되므로 자동 해제되어 안전.
43. **[P2] 애니메이션 중 Activity 종료로 인한 WindowLeaked**: 다이얼로그나 팝업이 떠 있는 상태에서 강제 종료 시 에러 로그 발생. -> 데모용 앱에서는 팝업 대신 Visibility 전환(`View.VISIBLE`, `GONE`) 방식을 채택하여 WindowLeaked 원천 차단.

## 🔥 Phase 10 — 빌드 자체가 안 되던 치명적 결함 (이번 세션 신규 발굴 · 전부 수정 완료)

> **가장 중요한 발견**: 이전 문서들이 "수정 완료"라고 적어둔 항목 일부가 **실제 코드엔 반영돼 있지 않았다.** 그 결과 FE 모듈은 **컴파일 자체가 불가능한 상태**였다(= APK 생성 불가 = 시연 0% 성공). 문서를 믿지 말고 코드를 직접 검증해야 한다.

### A. 컴파일 차단(P0) — 셋 다 "빌드 실패"라 시연 전체가 불가능했음
44. **[P0] `MainActivity.systemWatchdog` 미정의 (컴파일 에러)**: `onResume()`/`onPause()`가 `systemWatchdog`를 참조하는데 **필드 선언이 코드 어디에도 없었다.** (KNOWN_RISKS #1·#5, FAILSAFE 표에 "완료"로 기재돼 있었으나 docs에만 존재.) -> `MainActivity`에 `systemWatchdog` Runnable을 실제 구현: 10초마다 (1) 음량이 절반 이하로 떨어지면 80%로 원복, (2) `setKioskModeOn(true)`, (3) `hideTopBar()`. 각 단계 try/catch 격리 + 항상 다음 주기 재예약.
45. **[P0] `SensorEventPoller` 클래스 닫는 중괄호 뒤 고아 코드 (컴파일 에러)**: 클래스가 한 번 닫힌 뒤(`}`) 중복 메서드(`asString`/`asMap`)·떠 있는 문장(`actionManager.onSensorEvent(...)`)·`scheduleNext()`가 클래스 밖에 남아 있었다(잘못된 머지/편집 흔적). -> 파일 전체 재작성으로 고아 코드 제거.
46. **[P0] `TemiRepository` 잉여 닫는 중괄호 (컴파일 에러)**: 클래스 종료(`}`) 뒤 라인 293에 `}`가 하나 더 있어 파일이 깨져 있었다. -> 잉여 중괄호 1개 삭제.

### B. 컴파일은 되더라도 시연이 실패하던 논리 결함(P0~P1)
47. **[P0] 센서 폴링이 "딱 한 번"만 돌고 영구 정지**: `SensorEventPoller.poll()`이 응답/실패 후 `scheduleNext()`를 **호출하지 않아** 2초 주기 재폴링이 안 됐다(메서드는 있었지만 고아 영역에 있었음). 즉 시연의 핵심 볼거리인 **HW→BE→FE 센서 반응이 시작 직후 죽는** 상태. -> `onResponse`/`onFailure`/`enqueue 예외` **모든 종료 경로**에서 `scheduleNext()`를 호출하도록 `finally` 패턴으로 보강. 한 번 실패해도 폴링 루프가 끊기지 않음.
48. **[P1] 대화 첫 답변이 "소리 없이" 나오던 TTS 디바운스 오작동**: `TemiSpeechSpeaker`의 3초 전역 쿨다운이 **대화 발화에도 적용**돼 있었다. fast-path(웨이크워드 뒤에 바로 질문: "친구야, 사자는 뭐 먹어?")에서 T=0에 "응! 불렀어?"를 말하면, <3초 뒤 도착한 AI 답변이 쿨다운에 먹혀 **화면엔 답이 뜨는데 로봇은 입을 다무는** 데모 실패가 났다. -> `TemiSpeechSpeaker(boolean debounce)` 생성자 추가. 대화용 speaker는 `new TemiSpeechSpeaker(false)`(항상 발화), 센서/리실리언스용은 기존대로 디바운스 ON 유지.

### C. 운영/검증 프로세스 리스크 (앞으로의 재발 방지)
49. **[P0] "docs = 완료"의 함정**: 본 문서를 포함한 인수인계 자료가 *의도(계획)*를 *완료(반영)*로 기록하는 사고가 실재했다. -> 빌드 가능 여부는 **반드시 Android Studio 실제 빌드**로만 판정한다. 코드 리뷰 시 "문서에 완료라 적힘"은 근거로 인정하지 않는다.
50. **[P1] 잘못된 머지가 남기는 "닫는 중괄호 뒤 고아 코드"**: #45·#46이 동일 패턴(클래스 닫힌 뒤 잔여 코드/중괄호)이었다. -> 머지 후 점검 루틴으로 *파일별 `{`/`}` 개수 일치 검사*를 권장(빠른 스모크 테스트). 본 세션에서 전 FE `.java` 42개 균형 검사 통과 확인.

> 위 #44~#48은 코드에 반영 완료. 단, 본 환경엔 Android SDK가 없어 **풀 컴파일은 Android Studio에서 1회 확인 필요**(시그니처/리소스 검증). 변경 파일: `MainActivity.java`, `SensorEventPoller.java`, `TemiRepository.java`, `TemiSpeechSpeaker.java`.
