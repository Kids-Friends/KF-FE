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

## 4. 권한 요구 사항

- `android.permission.INTERNET`: 백엔드 통신용
- `android.permission.RECORD_AUDIO`: 음성 인식용
- `com.robotemi.permission.SETTINGS`: 로봇 목소리(TTS) 설정을 변경하기 위해 필요
