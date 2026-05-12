# Temi App API Schema

## Summary

This document defines the API contract between the Android Temi app and the Spring Boot backend.

- Client: Android Java app using Retrofit2
- Local test base URL: `http://localhost:8080/`
- Android emulator to PC base URL: `http://10.0.2.2:8080/`
- Temi real device base URL: `http://{PC_LAN_IP}:8080/`
- Content type: `application/json`
- Auth: Unspecified
- Mock mode: disabled in app via `ApiConfig.USE_MOCK = false`

## Endpoint Checklist

| Feature | Method | Path | App Method |
|---|---:|---|---|
| Staff call | POST | `/api/calls` | `createCall()` |
| Text question | POST | `/api/ai/chat` | `askQuestion()` |
| Voice question | POST | `/api/ai/chat` | `askVoiceQuestion()` |
| Current quiz | GET | `/api/quizzes/current` | `getCurrentQuiz()` |
| Quiz answer | POST | `/api/quizzes/answers` | `submitQuizAnswer()` |
| Statistics summary | GET | `/api/statistics/summary` | `getStatisticsSummary()` |

## Common Rules

- Return JSON for every successful response.
- Use UTF-8 Korean text.
- For success responses, match the field names exactly.
- On non-2xx responses, the current app displays only `API 응답 오류: {statusCode}`.
- For the current local test setup, keep the app base URL as `http://localhost:8080/`.
- If the backend is running on the developer PC and the Android emulator is used, switch the app base URL to `http://10.0.2.2:8080/`.
- If testing on the real Temi device, replace the app base URL with the PC LAN IP, for example `http://192.168.0.10:8080/`.

## 1. Staff Call

### `POST /api/calls`

Creates a staff call request.

### Request

| Field | Type | Required | Description |
|---|---|---:|---|
| `reason` | string | yes | Selected call reason |

Allowed app values:

| Value |
|---|
| `도움이 필요해요` |
| `아이가 다쳤어요` |
| `분실물이 있어요` |
| `기타` |

### Request Example

```json
{
  "reason": "도움이 필요해요"
}
```

### Response

| Field | Type | Required | Description |
|---|---|---:|---|
| `success` | boolean | yes | Whether the request was accepted |
| `callId` | string | yes | Backend-generated call id |
| `message` | string | yes | User-facing result message |

### Response Example

```json
{
  "success": true,
  "callId": "CALL-20260430-0001",
  "message": "직원 호출이 접수되었습니다."
}
```

## 2. Text Question

### `POST /api/ai/chat`

Sends a typed question and receives an answer.

### Request

| Field | Type | Required | Description |
|---|---|---:|---|
| `question` | string | yes | User question text |

### Request Example

```json
{
  "question": "화장실은 어디에 있어요?"
}
```

### Response

| Field | Type | Required | Description |
|---|---|---:|---|
| `success` | boolean | yes | Whether answer generation succeeded |
| `answer` | string | yes | User-facing answer text. The app also reads this with Temi TTS. |

### Response Example

```json
{
  "success": true,
  "answer": "화장실은 입구 오른쪽 복도 끝에 있습니다."
}
```

## 3. Voice Question

### `POST /api/ai/chat`

Sends both the raw STT result and the reconstructed question.

### Request

| Field | Type | Required | Description |
|---|---|---:|---|
| `rawText` | string | yes | Original STT result from Android speech recognition |
| `reconstructedText` | string | yes | App-reconstructed question text |

### Request Example

```json
{
  "rawText": "친구야 화장실 어디",
  "reconstructedText": "화장실은 어디에 있어?"
}
```

### Response

Same as `POST /api/ai/chat`.

```json
{
  "success": true,
  "answer": "화장실은 입구 오른쪽 복도 끝에 있습니다."
}
```

## 4. Current Quiz

### `GET /api/quizzes/current`

Returns the quiz currently shown to children.

### Response

| Field | Type | Required | Description |
|---|---|---:|---|
| `quizId` | string | yes | Quiz id used when submitting an answer |
| `question` | string | yes | Quiz question |
| `options` | string[] | yes | Multiple-choice options. Current app UI supports 3 options. |
| `correctAnswer` | string | no | Development/mock field. Do not rely on this in production UI. |

Production note:

- The app currently does not need `correctAnswer` to render or submit the quiz.
- For real service, prefer omitting `correctAnswer` or returning `null` to avoid exposing the answer.

### Response Example

```json
{
  "quizId": "quiz-001",
  "question": "키즈카페에서 놀고 난 뒤 가장 먼저 해야 할 일은 무엇일까요?",
  "options": [
    "뛰어다녀요",
    "손을 깨끗이 씻어요",
    "장난감을 던져요"
  ],
  "correctAnswer": null
}
```

## 5. Quiz Answer

### `POST /api/quizzes/answers`

Submits a selected quiz answer.

### Request

| Field | Type | Required | Description |
|---|---|---:|---|
| `quizId` | string | yes | Quiz id from `/api/quizzes/current` |
| `selectedAnswer` | string | yes | Selected option text |

### Request Example

```json
{
  "quizId": "quiz-001",
  "selectedAnswer": "손을 깨끗이 씻어요"
}
```

### Response

| Field | Type | Required | Description |
|---|---|---:|---|
| `correct` | boolean | yes | Whether the selected answer is correct |
| `message` | string | yes | User-facing result message |

### Response Example

```json
{
  "correct": true,
  "message": "정답입니다. 손 씻기는 가장 기본적인 안전 습관이에요."
}
```

## 6. Statistics Summary

### `GET /api/statistics/summary`

Returns simple operator statistics.

### Response

| Field | Type | Required | Description |
|---|---|---:|---|
| `callCount` | number | yes | Total staff calls |
| `questionCount` | number | yes | Total questions |
| `quizPlayCount` | number | yes | Total quiz attempts |
| `quizCorrectCount` | number | yes | Total correct quiz answers |
| `summaryMessage` | string | yes | Short operator-facing summary |

### Response Example

```json
{
  "callCount": 12,
  "questionCount": 27,
  "quizPlayCount": 18,
  "quizCorrectCount": 14,
  "summaryMessage": "오늘은 질문 사용량이 가장 많습니다."
}
```

## Backend Implementation Notes

- Minimum viable BE can implement all endpoints with in-memory data first.
- `POST /api/ai/chat` should persist both `rawText` and `reconstructedText`.
- `answer` is read by Temi TTS, so keep it short and natural.
- For local Android emulator testing, start Spring Boot on port `8080`.
- For real Temi device testing, both PC and Temi must be on the same network, and Windows firewall must allow inbound port `8080`.

## Quick Local Test Examples

```powershell
curl -X POST http://localhost:8080/api/ai/chat `
  -H "Content-Type: application/json" `
  -d "{\"reason\":\"도움이 필요해요\"}"
```

```powershell
curl -X POST http://localhost:8080/api/ai/chat `
  -H "Content-Type: application/json" `
  -d "{\"rawText\":\"친구야 화장실 어디\",\"reconstructedText\":\"화장실은 어디에 있어?\"}"
```
