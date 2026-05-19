package com.kidsFriend.data.api;

import com.kidsFriend.BuildConfig;

public final class ApiConfig {
    public static final boolean USE_MOCK = false;

    // .env 파일의 API_BASE_URL 값이 빌드 시 주입됩니다
    public static final String DEFAULT_API_BASE_URL = BuildConfig.API_BASE_URL;

    private ApiConfig() {
    }
}
