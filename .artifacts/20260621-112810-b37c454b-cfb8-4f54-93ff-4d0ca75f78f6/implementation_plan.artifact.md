# 사진 촬영 후 '처리 중' 화면 즉시 노출 개선 계획

사진 촬영 시 카메라 화면이 멈춘 상태로 유지되는 현상을 해결하기 위해, 촬영 명령 직후 즉시 로딩 화면으로 전환되도록 로직을 개선합니다.

## 변경 사항 요약

- 촬영 콜백(`onImageSaved`) 내부에 있던 `showProcessing()` 호출을 촬영 시작 직전으로 이동합니다.
- 사용자에게 즉각적인 시각적 피드백을 제공하여 "멈춤 현상" 오해를 방지합니다.

## Proposed Changes

### [Photo Domain]

#### [PhotoPlayActivity.java](file:///C:/dev/Study/kids_friends/KF_FE/app/src/main/java/com/kidsFriend/domain/photo/service/PhotoPlayActivity.java)

- `takePhoto()` 메소드 내 로직 순서 변경

```java
// AS-IS
imageCapture.takePicture(options, bgExecutor, new ImageCapture.OnImageSavedCallback() {
    @Override
    public void onImageSaved(...) {
        runOnUiThread(() -> showProcessing()); // 파일 저장 후 호출 (늦음)
        ...
    }
});

// TO-BE
showProcessing(); // 촬영 시작 전 즉시 호출 (빠름)
imageCapture.takePicture(options, bgExecutor, new ImageCapture.OnImageSavedCallback() {
    @Override
    public void onAsImageSaved(...) {
        // 이미 로딩 화면이 떠 있는 상태에서 합성 작업 수행
        ...
    }
});
```

---

## Verification Plan

### Manual Verification
- `PhotoPlayActivity`에서 사진 촬영 진행
- 카운트다운(3, 2, 1)이 끝나는 즉시 카메라 화면이 사라지고 "사진을 멋지게 꾸미고 있어!" 로딩 화면이 나타나는지 확인.
- 로딩 화면이 유지되는 동안 백그라운드에서 합성이 완료되고 결과 화면으로 넘어가는지 확인.
