# Temi Robot Configuration (Frontend)

이 문서는 Kids-Friends 프로젝트의 FE(Android)에서 사용하는 temi 로봇의 SDK 버전 및 주요 설정 사항을 정리합니다.

## 1. SDK 정보

- **Dependency**: `com.robotemi:sdk:1.131.4`
- **Minimum SDK**: Android API Level 23 (Marshmallow)
- **Target SDK**: Android API Level 33 (Tiramisu)

## 2. 주요 로봇 설정 (MainActivity)

앱 실행 시 `onRobotReady`에서 다음과 같은 설정이 적용됩니다.

| 항목 | 설정 값 | 설명 |
|---|---|---|
| **Kiosk Mode** | `ON` | 사용자가 앱을 종료하거나 다른 시스템 메뉴로 나가는 것을 방지합니다. |
| **Top Bar** | `HIDDEN` | 화면 상단의 테미 시스템 바를 숨깁니다. |
| **Wakeup Mode** | `DISABLED` | 테mi의 기본 호출어("Hey temi") 반응을 차단하고, 앱 내 `VoiceInputManager`에서 커스텀 호출어("친구야")를 처리합니다. |
| **TTS Voice** | `Female (Speed: 1.2, Pitch: 10)` | 아동 친화적인 귀여운 목소리 톤으로 설정합니다. |

## 3. AndroidManifest 설정 (Skill 등록)

테미 시스템이 이 앱을 인식하고 제어할 수 있도록 다음과 같은 메타데이터가 등록되어 있습니다.

- **Skill ID**: `Kids_Friend`
- **Kiosk Mode Candidate**: `TRUE`
- **Override Conversation Layer**: `TRUE` (테미의 기본 대화창을 띄우지 않고 앱이 직접 대화 UI를 제어)
- **UI Mode**: `4` (상단 시스템 바를 매니페스트 단계에서 숨김 — 런타임 `hideTopBar()`와 병행)

## 5. 센서 이벤트 연동 (KF_HW → KF_BE → FE)

라즈베리파이(KF_HW)의 ToF 카메라가 감지한 이벤트는 KF_BE의 `POST /api/sensor-events`로 올라가고,
FE는 `GET /api/sensor-events/latest`를 2초마다 폴링해 `RobotActionManager`로 넘긴다.

| 이벤트 | 로봇 반응 (데모 기본값) |
|---|---|
| `OBSTACLE_DETECTED` (≤30cm) | 정지 + "너무 가까워요" |
| `OBSTACLE_DETECTED` (>30cm) | 고개 들기 + "거기 누구 있어요?" |
| `CHILD_DETECTED` | 고개 들기 + 인사 (자율 주행 `beWithMe`는 기본 비활성화) |
| `TILT` / `BUMP` | 정지 + 경고 |

> 자율 주행(`beWithMe`)은 짧은 시연 안전을 위해 꺼져 있다. `RobotActionManager.approachNearbyChild()`에서 한 줄 주석 해제로 켤 수 있다.

## 6. 시연 돌발상황 자동 복구 (Temi 내장 기능)

`RobotResilienceManager`가 로봇 준비 후 내장 기능을 등록해 돌발상황을 자동 처리한다.

| 돌발상황 | Temi 내장 기능 | 반응 |
|---|---|---|
| 백엔드/HW/ngrok 다운 | **내장 사람감지**(`setDetectionModeOn`, `OnDetectionStateChangedListener`) | 테미 자체 카메라로 아이 접근 감지 → 인사 (센서 경로 백업) |
| 누가 로봇을 듦 | `OnRobotLiftedListener` | 즉시 정지 + "천천히 내려주세요" |
| 누가 로봇을 밂 | `OnRobotDragStateChangedListener` | 즉시 정지 + 안내 |
| 배터리 부족 | `OnBatteryStatusChangedListener` | 음성 안내 (충전 복귀는 기본 비활성) |
| AI/네트워크 실패 | (앱) | 원시 에러 대신 친근한 발화로 대화 지속 |
| 대화 멈춤(STT/TTS 응답 없음) | (앱) 워치독 | 45초 무진행 시 대기 상태로 자동 복귀 |

> 데모 중 카메라/백엔드가 불안정해도, 화면 길게 누르기(비밀 트리거)나 테미 내장 사람감지로 반응을 유발할 수 있다.

## 4. 권한 요구 사항

- `android.permission.INTERNET`: 백엔드 통신용
- `android.permission.RECORD_AUDIO`: 음성 인식용
- `com.robotemi.permission.SETTINGS`: 로봇 목소리(TTS) 설정을 변경하기 위해 필요

## 7. 음성 인식 엔진 전략 (STT 이중화)

테미 SDK 1.131.4는 **부분(interim) ASR 콜백이 없다**(`onAsrResult`/`onNlpCompleted`/`onConversationStatusChanged` 모두 최종 1회). 단어별 실시간 자막을 위해 `VoiceInputManager`가 두 엔진을 상황별로 고른다.

| 모드 | 엔진 | 이유 |
|---|---|---|
| **웨이크 대기(연속)** | `TemiSttEngine` | 소음에 강함. "친구야" 안정 감지. 자막 불필요. |
| **대화 듣기(단일)** | `AndroidSttEngine` 우선 → `TemiSttEngine` 폴백 | `SpeechRecognizer.onPartialResults`로 단어별 실시간 자막. 무응답/데드락(KNOWN_RISKS #7) 시 4초 워치독으로 테미 폴백. |

- 인식 텍스트는 `KoreanPhonetics`(자모 유사도) 기반 `IntentRouter` 퍼지 분기 + `SpeechCorrector` 표준어 스냅으로 근사 오인식까지 흡수한다.
- `SpeechRecognizer` 사용을 위해 Manifest에 `<queries><intent><action android:name="android.speech.RecognitionService"/></intent></queries>`가 등록돼 있어야 한다(이미 존재).
- 언어는 `ko-KR` 강제(시스템 로케일이 영어로 튀어도 인식 유지, KNOWN_RISKS #40 연계).
