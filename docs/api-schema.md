# Temi App API Schema

## Summary

This document defines the API contract between the Android Temi app and the Spring Boot backend (KF_BE).

- Client: Android Java app using Retrofit2, OkHttp, Gson
- Runtime base URL: configurable in the app server settings dialog (stored in SharedPreferences)
- Content type: `application/json`
- Auth: Unspecified (MVP has no login/auth)
- Common response wrapper:

```json
{
  "message": "string",
  "data": {}
}
```

> **시연 범위 정리 (2026-06):** 백엔드에서 회원(clients)·포인트·사진(photos)·직원호출(calls)·로봇상태(robots/status)·채팅로그(POST /api/chat)·센서이벤트(sensor-events) API가 제거되어, 앱도 해당 호출을 모두 제거했습니다. 회원등록/회원카드/사진 시나리오는 폐기했고, 미세먼지는 로컬 고정값("보통")으로 안내합니다. 아래는 앱이 현재 실제로 호출하는 엔드포인트만 담습니다.

## Endpoint Checklist

| Feature | Method | Path | App Method |
|---|---:|---|---|
| AI chat | POST | `/api/chat/ai` | `askAi()` — 자유질문 시나리오 |
| Intent resolve | POST | `/api/intent` | `resolveIntent()` |
| Robot position | POST | `/api/robot-position` | `reportRobotPosition()` |
| Robot locations | POST | `/api/robot-position/locations` | `reportRobotLocations()` |
| Health check | GET | `/api/health` | `health()` |

## 1. AI Chat

### `POST /api/chat/ai`

Sends the child speech or typed message to AI.

### Request

```json
{
  "message": "화장실은 어디에 있어요?"
}
```

### Response

```json
{
  "message": "AI 응답 성공",
  "data": {
    "reply": "화장실은 입구 오른쪽 복도 끝에 있습니다.",
    "created_at": "2026-05-14T10:00:00"
  }
}
```

The app displays and speaks `data.reply` using Temi TTS.

## 2. Intent Resolve

### `POST /api/intent`

STT로 변환된 텍스트를 LLM으로 분류해 의도와 필요한 데이터를 반환합니다.
(앱 기본 흐름은 로컬 `IntentRouter`로 분기하며, 이 API는 보조/확장용입니다.)

### Request

```json
{
  "text": "퀴즈 풀고 싶어"
}
```

### Response

Returns `ApiResponse<IntentResponse>`.

## 3. Robot Position

테미 매핑(자기 위치) 정보를 KF_BE로 보고해 운영 대시보드(KF_WEB)가 실시간 위치를 표시합니다.

### `POST /api/robot-position`

Reports the current robot position. Returns `204/Void`.

### `POST /api/robot-position/locations`

Reports the saved location list. Returns `204/Void`.

## 4. Health

### `GET /api/health`

Returns a simple status map for connectivity checks.

## Common Rules

- Return JSON using the wrapper for every successful response.
- The app stores the backend base URL in SharedPreferences and rebuilds Retrofit after it changes.
- Quiz, zones, navigation names, rule/location templates, and air-quality grade remain local/mock data in the Android app.
- Current MVP does not implement login or authentication.

## Runtime Server IP Change

1. On the Spring Boot server PC, run `ipconfig` (Windows) or `ifconfig` (Mac/Linux).
2. Find the WiFi IPv4 address, for example `192.168.1.100`.
3. Open the Temi app and tap `서버 설정`.
4. Enter `192.168.1.100` or `http://192.168.1.100:8081/`.
5. Tap `저장`.

The app saves the base URL in SharedPreferences and calls `RetrofitClient.resetInstance()`. All future API calls use the new URL without rebuilding the APK.

```powershell
curl -X POST http://<YOUR_SERVER_IP>:8081/api/chat/ai `
  -H "Content-Type: application/json" `
  -d "{\"message\":\"화장실은 어디에 있어요?\"}"
```
