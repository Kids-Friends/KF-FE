# 물건 맞추기 놀이 — 비전 센서(파이썬) 연동 요청서

> FE(테미 로봇 앱) ↔ 파이썬 비전 코드 **직접 연동**. **KF_BE(백엔드)를 거치지 않습니다.**
> 기존 `/api/sensor-events` / `/ws/sensors` 파이프라인은 이 게임에 쓰지 않습니다.

---

## 1. 전체 흐름

1. 아이가 메뉴에서 **"물건 맞추기 놀이"** 버튼을 누른다.
2. FE가 물체를 하나 제시한다. (예: 화면에 🍎 + "사과를 보여줘!", 음성 안내)
3. 아이가 카메라에 물체를 보여준다 → **파이썬이 감지한 물체 라벨을 FE로 전송**.
4. FE가 제시 물체와 비교 → 맞으면 **O(⭕)**, 틀리면 **X(❌)** 결과 화면.

---

## 2. 연결 구조 (누가 서버인가)

- **파이썬 = WebSocket 서버**, **FE = 클라이언트**.
- FE가 `ws://<파이썬_IP>:8765` 로 접속한다.
- 같은 네트워크(같은 WiFi/핫스팟)에 있어야 한다. 포트 기본값 **8765**.
- FE에서 파이썬 IP 설정: 게임 화면 우측 상단 **"연결 설정"** 버튼 → `192.168.0.10:8765` 형식으로 입력·저장.
  - `ws://` 생략 가능, 포트 생략 시 8765 자동.

> 파이썬 PC의 IP는 터미널에서 확인: Windows `ipconfig`, Mac/Linux `ifconfig` 또는 `ip addr`. WiFi의 IPv4 주소.

---

## 3. 메시지 프로토콜 (JSON, 텍스트 프레임)

### 3-1. FE → 파이썬 : 라운드 시작 (어떤 물체를 찾는 중인지 알림)

FE가 새 물체를 제시할 때마다 보냅니다. **참고용**이며, 파이썬은 이걸 무시하고 그냥 감지 결과만 보내도 됩니다(매칭은 FE가 함). 다만 이 값으로 감지 대상을 좁히면 정확도에 도움이 됩니다.

```json
{ "type": "round_start", "target": "apple", "targetKo": "사과" }
```

### 3-2. 파이썬 → FE : 감지 결과 (핵심)

카메라가 물체를 **확실히** 인식했을 때 1건씩 보냅니다.

```json
{ "type": "detection", "label": "apple", "confidence": 0.93 }
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `type` | string | `"detection"` (생략 시 detection으로 간주) |
| `label` | string | 감지한 물체의 **영어 라벨** (아래 표의 label과 정확히 일치, 소문자) |
| `confidence` | number | 0.0 ~ 1.0 신뢰도. **0.5 미만은 FE가 무시**합니다 |

> FE는 한 라운드에서 **첫 번째 유효 감지(confidence ≥ 0.5)** 로 채점합니다.
> 그러니 "확실할 때 한 번" 보내는 게 좋습니다. (배경 물체를 계속 흘려보내면 오답 처리될 수 있음)

---

## 4. 물체 라벨 표 (반드시 이 `label` 문자열로 보낼 것)

| label (파이썬이 보낼 값) | 한글 표시 | 카드 |
|---|---|---|
| `oral_bottle`    | 약병       | 💊 |
| `color_pen`      | 색펜       | 🖍️ |
| `battery`        | 건전지     | 🔋 |
| `brush`          | 붓         | 🖌️ |
| `plastic_bottle` | 페트병     | 🧴 |
| `umbrella`       | 우산       | ☂️ |
| `banana_peel`    | 바나나 껍질 | 🍌 |
| `ketchup`        | 케첩       | 🥫 |
| `bone`           | 뼈다귀     | 🦴 |
| `chopstick`      | 젓가락     | 🥢 |
| `plate`          | 접시       | 🍽️ |
| `cigarette_end`  | 담배꽁초   | 🚬 |

- 위 `label`은 센서 모델의 클래스명 그대로입니다(클래스 번호 순서는 무관). 모델 출력 클래스명과 **정확히 일치**해야 합니다.
- 물체를 추가/변경하면 FE의 `ObjectGameActivity.OBJECTS` 배열과 이 표를 함께 맞춥니다. (FE 담당)

---

## 5. 파이썬 서버 예시 (websockets)

```python
# pip install websockets
import asyncio, json, websockets

current_target = None  # FE가 알려준 현재 찾는 물체(선택적 활용)

async def handler(ws):
    global current_target
    print("FE 연결됨:", ws.remote_address)
    async for raw in ws:                      # FE → 파이썬 (round_start) 수신
        try:
            msg = json.loads(raw)
        except Exception:
            continue
        if msg.get("type") == "round_start":
            current_target = msg.get("target")
            print("이번 라운드 타깃:", current_target)

        # ── 여기서 카메라 추론 루프와 연결 ──
        # 물체를 확실히 인식했다면 아래처럼 FE로 보냅니다:
        # await ws.send(json.dumps({"type":"detection","label":"apple","confidence":0.93}))

async def main():
    # 0.0.0.0 으로 열어야 로봇이 외부에서 접속 가능
    async with websockets.serve(handler, "0.0.0.0", 8765):
        print("Vision WS server on :8765")
        await asyncio.Future()  # run forever

asyncio.run(main())
```

> 실제로는 카메라 추론 루프(별도 태스크/스레드)에서 감지가 나올 때마다
> 연결된 `ws`로 `detection` 메시지를 `send` 하면 됩니다.
> 다중 클라이언트가 필요 없으면 위 단일 핸들러로 충분합니다.

---

## 6. 체크리스트 / 테스트

- [ ] 파이썬 PC와 테미 로봇이 **같은 WiFi/핫스팟**에 있다.
- [ ] 파이썬 서버를 `0.0.0.0:8765` 로 띄웠다. (방화벽에서 8765 인바운드 허용)
- [ ] FE "물건 맞추기 놀이" → "연결 설정"에 파이썬 PC IP 입력 → 저장.
- [ ] 화면 상태표시가 "카메라에 보여줘!" 로 바뀌면 연결 성공. ("카메라 연결 끊김"이면 IP/포트/방화벽 확인)
- [ ] `apple` 라벨을 0.9 신뢰도로 보내면, FE가 "사과" 라운드일 때 ⭕ 가 떠야 한다.
- [ ] 다른 라벨을 보내면 ❌ 가 떠야 한다.

### 빠른 수동 테스트 (카메라 없이)
파이썬 서버에서 임의로 `{"type":"detection","label":"apple","confidence":0.95}` 한 줄만 보내봐도 FE 채점이 동작하는지 확인할 수 있습니다.

---

## 7. FE 측 구현 위치 (참고)

- `domain/objectgame/service/ObjectGameActivity.java` — 게임 화면/판정
- `domain/objectgame/service/VisionSocketClient.java` — 파이썬 직결 WebSocket 클라이언트
- `domain/objectgame/service/VisionConfig.java` — 파이썬 서버 주소 저장(기본 `ws://10.26.137.108:8765`)
