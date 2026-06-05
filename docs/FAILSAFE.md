# FAILSAFE & DEMO MODE DESIGN

이 문서는 데모 중단 제로("Demo Failure Zero")를 목표로 작성된 하드코어 예외 복구 지침입니다.

## 1. MOCK & FALLBACKS (구현 완료)

| 실패 상황 | 영향 | 복구 전략 (Fallback) | UI 작동 |
|---|---|---|---|
| **STT 실패/무응답** | 음성 인식 불가 | UI 버튼 Long Click으로 강제 텍스트 주입 | `MainActivity` 우측 상단 오퍼레이터 버튼 꾹 누르기 -> "퀴즈 풀고 싶어" 주입 |
| **물리 볼륨 0%** | 소리 안남 | 10초마다 `systemWatchdog`이 볼륨 80% 강제 원복 | 데모 자동 보호 |
| **로봇 센서 죽음** | HW 고장 | 로봇 화면 터치 이벤트를 통한 Mock Event 생성 | 얼굴(아이), 답변텍스트(장애물), 상태텍스트(기울어짐) |
| **백엔드/API 죽음** | 네트워크 장애 | `TemiRepository`에서 Mock Success 응답 강제 생성 | 사용자는 정상 동작으로 인지 (귀여운 에러 멘트 TTS) |

## 2. ACTIVITY RECOVERY
Android 시스템 이벤트(Configuration 변화)로 인한 앱 초기화를 막기 위해 `AndroidManifest.xml`에 다음 속성을 고정했습니다.
- `launchMode="singleTop"` 또는 `"singleTask"`: 무분별한 클릭으로 인한 다중 화면 쌓임 방지
- `screenOrientation="landscape"`: 강제 화면 전환으로 인한 재생성 방지
- `configChanges="orientation|screenSize|uiMode"`: 시스템 상태 변경에도 Activity 쌩존

## 3. CHAOS TESTING (검증 완료)
다음 사항들은 모두 방어선(Try-catch 및 Fallback)이 구축되어 시연에 어떠한 영향도 미치지 못함을 보장합니다.
- Wi-Fi 연결 해제 (오프라인 모드) 
- 테미 로봇 강제 Mute (볼륨 낮춤)
- 라즈베리파이 센서 모듈 강제 종료
- 아이들이 화면 마구잡이 연타 (Click Spamming)
