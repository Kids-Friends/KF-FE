package com.kidsFriend.global.config;

public final class ApiConfig {
    public static final boolean USE_MOCK = false;

    // .env 파일의 API_BASE_URL 값이 빌드 시 주입되지만, 수동으로 ngrok 주소를 우선 적용합니다.
    public static final String DEFAULT_API_BASE_URL = "https://avengeful-shaunte-revolvingly.ngrok-free.dev/";

    private ApiConfig() {
    }
}