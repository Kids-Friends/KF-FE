package com.example.temiapplication.data.api;

public final class ApiConfig {
    public static final boolean USE_MOCK = false;

    // Local test server.
    public static final String LOCAL_API_BASE_URL = "http://localhost:8080/";

    // Android emulator -> local PC Spring Boot: http://10.0.2.2:8080/
    // Temi real device -> replace with PC LAN IP, for example: http://192.168.0.10:8080/

    private ApiConfig() {
    }
}
