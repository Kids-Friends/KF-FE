# Temi App API Schema

## Summary

This document defines the API contract between the Android Temi app and the Spring Boot backend.

- Client: Android Java app using Retrofit2, OkHttp, Gson
- Default base URL: `http://192.168.0.1:8081/`
- Runtime base URL: configurable in the app server settings dialog
- Temi real device base URL: use the Spring Boot server IP on the same network
- Content type: `application/json`
- Auth: Unspecified
- Common response wrapper:

```json
{
  "message": "string",
  "data": {}
}
```

## Endpoint Checklist

| Feature | Method | Path | App Method |
|---|---:|---|---|
| List clients | GET | `/api/clients` | `getClients()` from 아이 설정 |
| Client detail | GET | `/api/clients/{id}` | `getClient()` from manual 아이 ID 설정 |
| Add point | PATCH | `/api/clients/{id}/point` | `addClientPoint()` |
| Robot status | PATCH | `/api/robots/{id}/status` | `updateRobotStatus()` from app lifecycle |
| Staff call | POST | `/api/calls` | `createCall()` from 직원 호출 |
| Call status | PATCH | `/api/calls/{id}/status` | `updateCallStatus()` from 직원 도착 확인 / 호출 취소 |
| AI chat | POST | `/api/chat/ai` | `askAi()` from 질문하기 |
| Chat log | POST | `/api/chat` | `saveChatLog()` after AI reply |
| Photo metadata | POST | `/api/photos` | `savePhoto()` from 사진 저장 |

## Common Rules

- Return JSON using the wrapper for every successful response.
- The app stores `robotId` and `currentClientId` in SharedPreferences.
- The app stores the backend base URL in SharedPreferences and rebuilds Retrofit after it changes.
- Quiz, zones, navigation names, and rule/location templates remain mock data in the Android app.
- The app never sends binary image data to the backend. Send only the uploaded cloud URL.
- Current MVP does not implement login or authentication.
- The 운영 통계 and ZONE screens are local/mock utility screens because the current backend contract has no statistics or zone endpoints.

## 1. Clients

### `GET /api/clients`

Returns all child members.

### `GET /api/clients/{id}`

Returns one child member.

### Response Data

```json
{
  "clientId": "uuid",
  "childName": "string",
  "parentName": "string",
  "parentPhone": "string",
  "clientPoint": 0
}
```

## 2. Point Reward

### `PATCH /api/clients/{id}/point`

Adds points when a child answers an in-app mock quiz correctly.

### Request

```json
{
  "amount": 1
}
```

### Response

Returns `ApiResponse<ClientResponse>`.

## 3. Robot Status

### `PATCH /api/robots/{id}/status`

Updates the Temi robot status.

### Request

```json
{
  "status": "ACTIVE"
}
```

Allowed values:

| Value |
|---|
| `ACTIVE` |
| `INACTIVE` |
| `ERROR` |

App behavior:

- App starts or `onRobotReady(true)` -> `ACTIVE`
- App goes background or low battery -> `INACTIVE`
- Unexpected uncaught error -> `ERROR`

## 4. Staff Call

### `POST /api/calls`

Creates a call log. Backend defaults status to `WAITING`.

### Request

```json
{
  "robotId": "uuid",
  "clientId": "uuid",
  "reason": "화장실 가고 싶어요"
}
```

### Response Data

```json
{
  "callsId": "uuid",
  "robotId": "uuid",
  "clientId": "uuid",
  "reason": "string",
  "status": "WAITING"
}
```

### `PATCH /api/calls/{id}/status`

Updates a call state.

```json
{
  "status": "DONE"
}
```

Allowed values:

| Value |
|---|
| `WAITING` |
| `DONE` |
| `CANCELED` |

## 5. AI Chat

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
  "message": "채팅 성공",
  "data": {
    "reply": "화장실은 입구 오른쪽 복도 끝에 있습니다.",
    "created_at": "2026-05-14T10:00:00"
  }
}
```

The app displays and speaks `data.reply` using Temi TTS.

## 6. Chat Log

### `POST /api/chat`

Optional conversation history save after AI reply.

### Request

```json
{
  "clientId": "uuid",
  "robotId": "uuid",
  "question": "화장실은 어디에 있어요?",
  "answer": "화장실은 입구 오른쪽 복도 끝에 있습니다.",
  "chatType": "CHAT"
}
```

## 7. Photo Metadata

### `POST /api/photos`

Saves only uploaded photo metadata.

### Request

```json
{
  "clientId": "uuid",
  "photoUrl": "https://storage.example.com/photos/photo-001.jpg",
  "photoName": "photo-001.jpg"
}
```

## Quick Local Test Examples

## Runtime Server IP Change

1. On the Spring Boot server PC, run `ipconfig` on Windows or `ifconfig` on Mac/Linux.
2. Find the WiFi IPv4 address, for example `192.168.1.100`.
3. Open the Temi app and tap `서버 설정`.
4. Enter `192.168.1.100` or `http://192.168.1.100:8081/`.
5. Tap `저장`.

The app saves the base URL in SharedPreferences and calls `RetrofitClient.resetInstance()`. All future API calls use the new URL without rebuilding the APK.

```powershell
curl -X PATCH http://<YOUR_SERVER_IP>:8081/api/robots/00000000-0000-0000-0000-000000000001/status `
  -H "Content-Type: application/json" `
  -d "{\"status\":\"ACTIVE\"}"
```

```powershell
curl -X POST http://<YOUR_SERVER_IP>:8081/api/chat/ai `
  -H "Content-Type: application/json" `
  -d "{\"message\":\"화장실은 어디에 있어요?\"}"
```

```powershell
curl -X POST http://<YOUR_SERVER_IP>:8081/api/calls `
  -H "Content-Type: application/json" `
  -d "{\"robotId\":\"00000000-0000-0000-0000-000000000001\",\"clientId\":\"00000000-0000-0000-0000-000000000101\",\"reason\":\"도움이 필요해요\"}"
```
