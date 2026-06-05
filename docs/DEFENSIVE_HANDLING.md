# Temi 로봇 방어적 예외처리 및 시연(Demo) 안정성 확보 지침

이 문서는 Temi 로봇 시연 환경에서 센서나 네트워크 장애가 발생해도 앱이 중단되지 않도록, Temi SDK(v1.131.4)와 Android 기술을 바탕으로 작성된 방어적 예외처리 지침입니다.

## 1. SDK/API 및 권한 설정
- **Temi SDK 버전**: `com.robotemi:sdk:1.131.4`
- **필수 권한**: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `RECORD_AUDIO`, `CAMERA`, `WAKE_LOCK`
- **키오스크 모드 및 톱바 제어**: `UI_MODE=4` 메타데이터를 통해 톱바를 완전히 숨기고, `requestToBeKioskApp()`, `setKioskModeOn(true)` 호출로 앱 이탈을 방지합니다.
- **웨이크워드(Wakeup) 제어**: `toggleWakeup(false)`를 통해 기본 "Hey temi"를 끄고 앱 내장 커스텀 호출어("친구야")를 최우선으로 사용합니다.

## 2. 장애 탐지 및 폴백(Fallback) 시나리오
| 센서 종류 | 장애 탐지/판단 | 폴백 시나리오 | 안전 기본 동작 |
|---|---|---|---|
| **LiDAR/카메라** | Exception 발생 또는 데이터 이상 | 얼굴 인식 및 주행 기능 정지, 대화 모드로 강제 전환 | 이동 중지(`stopMovement()`), 안전 알림 송출 |
| **네트워크/API** | HTTP 5xx, 타임아웃, 예외 | 즉시 Mock 데이터(로컬 대체 텍스트/UI) 반환. 무조건 성공한 것으로 간주하여 흐름 유지 | `TemiRepository`에서 오류 캐치 후 Mock 응답 |
| **마이크/음성** | 인식 불가, ASR/NLU 예외 | 화면 터치 UI(얼굴 터치 등)를 통한 수동 이벤트 트리거 제공 | 수동 인터페이스로 완전히 조작 가능하도록 설정 |

## 3. 오프라인 & Mock 최우선 아키텍처
현재 앱은 데모를 위해 **단 하나의 API 호출 예외도 앱을 멈추게 하지 않도록 설계**되었습니다.
- 대화(AI), 회원 정보, 퀴즈, 포인트 적립 등 모든 네트워크 작업은 `TemiRepository` 레벨에서 `try-catch` 및 `Retrofit Callback` 방어 코드가 적용되어 있습니다.
- 장애 시 즉시 친근한 말투의 Fallback TTS("인터넷이 잠깐 끊겼나봐~" 등)를 출력합니다.

## 4. 라이프사이클 및 와치독(Watchdog)
- 앱이 백그라운드에서 포그라운드로 복귀할 때 `onRobotReady` 콜백을 통해 로봇과의 연결을 강제 갱신합니다.
- `MainActivity`에 내장된 와치독(Watchdog) 로직을 통해 로봇 상태를 주기적으로 확인하고, 필요 시 키오스크 모드를 재활성화합니다.

## 5. 결론 및 산출물
이 지침에 따라 FE 코드는 다음과 같은 방어 기제를 모두 갖추었습니다.
- **UI Trigger**: 화면 곳곳에 숨겨진 터치 이벤트(Long Click, Double Click)로 물리 센서 완전 대체.
- **Network Resilience**: 모든 API에 Mock Fallback 적용.
- **System Stability**: AndroidManifest에 WAKE_LOCK 및 필수 권한 적용 완료.
