# KF_FE — Kids-Friends 테미 로봇 앱 🤖

> 키즈카페 안내로봇 **Kids-Friends**의 "얼굴이자 몸"입니다.
> 아이가 만지고 말을 거는 **테미(Temi) 로봇 위에서 실행되는 안드로이드 앱**이에요.
> 화면 터치나 음성으로 명령하면, 로봇이 해당 놀이/안내 화면으로 바뀌고
> **캐릭터 표정 + 귀여운 목소리**로 대답합니다.

이 글은 이 로봇으로 수업을 들을 **후배들**을 위해 쓰였습니다.

---

## 1. 전체 그림에서 KF_FE는 어디에 있나요?

```
 [KF_HW 라즈베리파이]  ┐
 [KF_AD 아두이노(대체)] ┘ ──센서값──▶ [KF_BE 서버] ──WebSocket / AI 답변──▶ ┌───────────────┐
                                                                          │  KF_FE 앱     │
                                                                          │  (← 여기!)    │
                                                                          │  테미 로봇 위  │
                                                                          └───────────────┘
```

- **KF_FE(이 레포)** = 아이와 직접 만나는 앱. 서버([KF_BE](https://github.com/Kids-Friends/KF-BE))와
  ngrok 주소로 통신합니다.
- 형제 레포: [KF_BE](https://github.com/Kids-Friends/KF-BE)(서버) · [KF_HW](https://github.com/Kids-Friends/KF-HW)(라즈베리파이) · [KF_AD](https://github.com/Kids-Friends/KF_AD)(아두이노) · [KF_WEB](https://github.com/Kids-Friends/KF_WEB)(관리자 웹)

---

## 2. 무엇을 할 수 있나요? (시연 기능 5종 + 안전)

| 기능 | 어떻게 작동하나요 |
|---|---|
| 📷 **사진 찍기** | "사진 찍자" → 3초 카운트다운 후 촬영, 프레임/필터 선택 |
| 🎮 **게임 하기** | O/X 안전 퀴즈 (예: "미끄럼틀에서 친구 밀어도 될까?") |
| 📞 **친구에게 전화하기** | 캐릭터 친구를 골라 통화 → 아이 질문을 서버 AI로 보내 친구 말투로 답함 |
| 🗺️ **카페 안내** | 지도에서 존을 누르면 로봇이 그 위치로 직접 이동 |
| 🌫️ **공기 확인** | 센서가 보낸 실시간 미세먼지 값을 좋음/보통/나쁨으로 안내 |
| 🔥 **화재경보** | 화재 신호를 받으면 즉시 비상 화면 + "애들아 불이났어! 비상!" |

---

## 3. 폴더 구조 (핵심만)

```
app/src/main/java/com/kidsFriend/
├── MainActivity.java            # 홈 화면 + 모든 대화/표정 흐름의 중심
├── domain/                      # 기능별 화면 모음
│   ├── call/                    #   📞 친구에게 전화하기(AI 대화)
│   ├── guide/                   #   🗺️ 카페 안내(지도/이동)
│   └── sensor/                  #   📡 센서 수신 → 로봇 동작 변환
│       └── service/             #      RobotActionManager, SensorWebSocketClient
└── global/                      # 공통 부품
    ├── config/                  #   서버 주소(ApiConfig), 앱 설정
    └── ...
app/src/main/res/                # 화면 레이아웃(xml), 색상, 이미지, 영상
docs/                            # 📚 디자인 시스템·표정 에셋·기능 명세 (꼭 참고!)
```

---

## 4. 실행 방법 (처음 하는 사람 기준)

**준비물**
- **Android Studio**
- **테미 로봇 실기기** 또는 안드로이드 에뮬레이터
- 먼저 **[KF_BE 서버](https://github.com/Kids-Friends/KF-BE)가 켜져 있어야** AI 대화·센서가 동작합니다.

**순서**
1. Android Studio에서 이 폴더(`KF_FE`)를 엽니다.
2. Gradle 동기화가 끝날 때까지 기다립니다.
3. 위쪽 ▶(Run) 버튼을 눌러 테미 기기(또는 에뮬레이터)에 설치합니다.
4. 서버 주소가 바뀌면 `global/config/ApiConfig.java` 의 base URL(ngrok 주소)을 확인하세요.

---

## 5. ⚠️ 꼭 지켜야 할 규칙 (버전 동결)

이 앱은 **검증이 끝난 빌드 버전으로 고정(freeze)** 되어 있습니다. 함부로 올리면 빌드가 깨집니다.

| 항목 | 고정 값 |
|---|---|
| Gradle | **8.1** |
| Android Gradle Plugin(AGP) | **8.1.0** |
| Java | **11** |
| compileSdk | **33** |
| Temi SDK | **1.131.4** |

> 새 라이브러리 추가나 버전 변경이 정말 필요하면, 먼저 팀과 상의하세요.

---

## 6. 기술 스택
Android (Java) · Temi SDK 1.131.4 · Retrofit2 + OkHttp(WebSocket) · Media3(ExoPlayer)

> 💡 화면 색상·표정 등 **디자인 기준은 `docs/` 폴더**에 있습니다. UI를 만질 땐 항상 먼저 보세요.
