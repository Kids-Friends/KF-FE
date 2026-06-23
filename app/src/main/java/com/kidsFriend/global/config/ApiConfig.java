package com.kidsFriend.global.config;

import com.kidsFriend.BuildConfig;

public final class ApiConfig {
    public static final boolean USE_MOCK = false;

    // .env 의 API_BASE_URL 값이 빌드 시 BuildConfig 로 주입된다(없으면 build.gradle fallback).
    public static final String DEFAULT_API_BASE_URL = BuildConfig.API_BASE_URL;

    private ApiConfig() {
    }
}