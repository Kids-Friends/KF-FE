# Kids-Friends API Endpoint Checklist

---

## 0. 공통 규칙

### 공통 요청 필드

| 필드 | 설명 | 예시 |
|---|---|---|
| `robotId` | 어떤 Temi에서 발생한 요청인지 | `TEMI_01` |
| `zoneId` | 어느 구역에서 발생한 요청인지 | `SLIDE_ZONE` |
| `role` | 사용자 유형 | `CHILD`, `GUARDIAN`, `STAFF` |
| `sessionId` | 임시 사용자 세션 | `sess-001` |

---

## 1. 직원 호출 Call

| 체크 | Method | Endpoint | 설명 |
|---|---|---|---|
| [ ] | POST | `/api/calls` | 직원 호출 생성 |
| [ ] | GET | `/api/calls` | 호출 목록 조회 |
| [ ] | GET | `/api/calls/{callId}` | 호출 상세 조회 |
| [ ] | PATCH | `/api/calls/{callId}/status` | 호출 상태 변경 |
| [ ] | GET | `/api/calls/waiting-count` | 대기 중 호출 수 조회 |

### Request 예시

```json
{
  "robotId": "TEMI_01",
  "zoneId": "BALL_POOL",
  "role": "CHILD",
  "reason": "도움이 필요해요"
}
```

---

## 2. 질문 대응 Question

| 체크 | Method | Endpoint | 설명 |
|---|---|---|---|
| [ ] | POST | `/api/questions` | 텍스트 질문 전송 |
| [ ] | POST | `/api/questions/voice` | 음성 질문 전송 |
| [ ] | GET | `/api/questions/logs` | 질문 로그 조회 |
| [ ] | GET | `/api/questions/frequent` | 자주 묻는 질문 조회 |
| [ ] | GET | `/api/faqs` | FAQ 목록 조회 |

### Voice Request 예시

```json
{
  "robotId": "TEMI_01",
  "zoneId": "MAIN_ZONE",
  "role": "CHILD",
  "wakeWord": "친구야",
  "rawText": "미끄럼 어디써",
  "reconstructedText": "미끄럼틀은 어디 있어?",
  "inputType": "VOICE"
}
```

---

## 3. 퀴즈 Quiz

| 체크 | Method | Endpoint | 설명 |
|---|---|---|---|
| [ ] | GET | `/api/quizzes/random` | 랜덤 퀴즈 조회 |
| [ ] | GET | `/api/quizzes/{quizId}` | 퀴즈 상세 조회 |
| [ ] | POST | `/api/quizzes/{quizId}/answers` | 퀴즈 정답 제출 |
| [ ] | GET | `/api/quizzes/results/{resultId}` | 퀴즈 결과 조회 |
| [ ] | GET | `/api/quizzes/statistics` | 퀴즈 참여 통계 조회 |

### Response 예시

```json
{
  "quizId": 1,
  "question": "키즈카페에서 뛰어다녀도 될까요?",
  "choices": ["된다", "안 된다"],
  "answerIndex": 1
}
```

---

## 4. 운영자용 통계 Statistics

| 체크 | Method | Endpoint | 설명 |
|---|---|---|---|
| [ ] | GET | `/api/admin/statistics` | 전체 운영 통계 조회 |
| [ ] | GET | `/api/admin/statistics/calls` | 호출 통계 조회 |
| [ ] | GET | `/api/admin/statistics/questions` | 질문 통계 조회 |
| [ ] | GET | `/api/admin/statistics/quizzes` | 퀴즈 통계 조회 |
| [ ] | GET | `/api/admin/statistics/zones` | 구역별 통계 조회 |

### Response 예시

```json
{
  "totalCalls": 12,
  "waitingCalls": 3,
  "totalQuestions": 28,
  "quizParticipationCount": 15,
  "topQuestions": [
    "미끄럼틀 어디 있어?",
    "화장실 어디 있어요?",
    "직원 불러줘"
  ],
  "topZones": [
    "BALL_POOL",
    "SLIDE_ZONE",
    "MAIN_ZONE"
  ]
}
```

---

## 확장 기능 Endpoint

---

## 5. 인식해서 다가가기 Approach

> 왕따 모션 정의, 상황 따라가기, 사용자를 인식하고 Temi가 다가가는 기능

| 체크 | Method | Endpoint | 설명 |
|---|---|---|---|
| [ ] | POST | `/api/approach-events` | 사용자 인식 이벤트 생성 |
| [ ] | POST | `/api/robot-actions/approach` | Temi 접근 명령 생성 |
| [ ] | GET | `/api/motions` | 모션 목록 조회 |
| [ ] | GET | `/api/motions/{motionId}` | 모션 상세 조회 |
| [ ] | POST | `/api/motions/{motionId}/execute` | 특정 모션 실행 |
| [ ] | PATCH | `/api/robot-actions/{actionId}/status` | 로봇 액션 상태 변경 |

### Request 예시

```json
{
  "robotId": "TEMI_01",
  "zoneId": "MAIN_ZONE",
  "detectedType": "CHILD_ALONE",
  "confidence": 0.82,
  "recommendedMotion": "FRIENDLY_APPROACH"
}
```

---

## 6. 위치 안내 Location Guide

| 체크 | Method | Endpoint | 설명 |
|---|---|---|---|
| [ ] | GET | `/api/locations` | 위치 목록 조회 |
| [ ] | GET | `/api/locations/{locationId}` | 위치 상세 조회 |
| [ ] | POST | `/api/guides` | 위치 안내 요청 생성 |
| [ ] | POST | `/api/robot-actions/goto` | Temi 이동 명령 생성 |
| [ ] | PATCH | `/api/guides/{guideId}/status` | 안내 상태 변경 |

### Request 예시

```json
{
  "robotId": "TEMI_01",
  "zoneId": "ENTRANCE",
  "role": "GUARDIAN",
  "targetLocationId": "RESTROOM"
}
```

---

## 7. TTS

> 실제 음성 출력은 Android Temi SDK의 `speak` 계층에서 실행.  
> BE는 어떤 문장을 말할지 내려주는 역할.

| 체크 | Method | Endpoint | 설명 |
|---|---|---|---|
| [ ] | POST | `/api/tts/commands` | TTS 명령 생성 |
| [ ] | GET | `/api/tts/scripts` | 상황별 TTS 문구 조회 |
| [ ] | GET | `/api/tts/scripts/{scriptId}` | TTS 문구 상세 조회 |
| [ ] | POST | `/api/robot-actions/speak` | Temi 발화 명령 생성 |
| [ ] | GET | `/api/tts/logs` | TTS 실행 로그 조회 |

### Request 예시

```json
{
  "robotId": "TEMI_01",
  "zoneId": "MAIN_ZONE",
  "speechText": "직원을 불렀어요. 잠시만 기다려주세요.",
  "expression": "HAPPY"
}
```

---

## 8. 리워드 시스템 Reward

> DB를 조회해서 착한 어린이 여부 판단.  
> 실제 아동 개인정보 대신 `childId`, `sessionId` 사용 권장.

| 체크 | Method | Endpoint | 설명 |
|---|---|---|---|
| [ ] | POST | `/api/rewards/events` | 리워드 이벤트 기록 |
| [ ] | POST | `/api/rewards/evaluate` | 리워드 지급 여부 판단 |
| [ ] | GET | `/api/rewards/children/{childId}` | 아이별 리워드 조회 |
| [ ] | GET | `/api/rewards/sessions/{sessionId}` | 세션별 리워드 조회 |
| [ ] | POST | `/api/rewards` | 리워드 지급 |
| [ ] | GET | `/api/rewards/statistics` | 리워드 통계 조회 |

### Request 예시

```json
{
  "childId": "CHILD_001",
  "sessionId": "sess-001",
  "eventType": "QUIZ_CORRECT",
  "score": 10
}
```

---

## 9. 사진 촬영 Photo

> "치즈", "김치"라고 하면 사진 촬영 -> 저장 -> 애니메이션 얼굴 출력.

| 체크 | Method | Endpoint | 설명 |
|---|---|---|---|
| [ ] | POST | `/api/photos/sessions` | 사진 촬영 세션 생성 |
| [ ] | POST | `/api/photos` | 사진 메타데이터 저장 |
| [ ] | GET | `/api/photos/{photoId}` | 사진 상세 조회 |
| [ ] | GET | `/api/photos` | 사진 목록 조회 |
| [ ] | DELETE | `/api/photos/{photoId}` | 사진 삭제 |
| [ ] | POST | `/api/animations/face` | 애니메이션 얼굴 실행 |
| [ ] | GET | `/api/animations/face-presets` | 얼굴 애니메이션 목록 조회 |

### Request 예시

```json
{
  "robotId": "TEMI_01",
  "zoneId": "PHOTO_ZONE",
  "triggerWord": "치즈",
  "animationType": "SMILE",
  "saveRequired": true
}
```

---

## 10. 분리수거 Recycling

| 체크 | Method | Endpoint | 설명 |
|---|---|---|---|
| [ ] | GET | `/api/recycling/categories` | 분리수거 카테고리 조회 |
| [ ] | POST | `/api/recycling/classifications` | 쓰레기 분류 요청 |
| [ ] | GET | `/api/recycling/classifications/{classificationId}` | 분류 결과 조회 |
| [ ] | POST | `/api/recycling/logs` | 분리수거 기록 저장 |
| [ ] | GET | `/api/recycling/statistics` | 분리수거 통계 조회 |

### Request 예시

```json
{
  "robotId": "TEMI_01",
  "zoneId": "RECYCLING_ZONE",
  "imageUrl": "https://example.com/trash-image.jpg",
  "detectedObject": "plastic_bottle"
}
```

---

## 최종 구현 우선순위

| 우선순위 | 체크 | 기능 | 대표 Endpoint |
|---|---|---|---|
| 1 | [ ] | 직원 호출 | `POST /api/calls` |
| 2 | [ ] | 질문 대응 | `POST /api/questions` |
| 3 | [ ] | 음성 질문 | `POST /api/questions/voice` |
| 4 | [ ] | 퀴즈 | `GET /api/quizzes/random` |
| 5 | [ ] | 운영자 통계 | `GET /api/admin/statistics` |
| 6 | [ ] | 위치 안내 | `POST /api/guides` |
| 7 | [ ] | TTS | `POST /api/tts/commands` |
| 8 | [ ] | 인식 후 접근 | `POST /api/approach-events` |
| 9 | [ ] | 리워드 | `POST /api/rewards/evaluate` |
| 10 | [ ] | 사진 촬영 | `POST /api/photos/sessions` |
| 11 | [ ] | 분리수거 | `POST /api/recycling/classifications` |
