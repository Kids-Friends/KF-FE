package com.kidsFriend.domain.sensor;

import java.util.Map;

/**
 * 센서 도메인: 최신 센서 이벤트 데이터에서 미세먼지(공기질) 등급(좋음/보통/나쁨)을 해석한다.
 *
 * <p>원래 {@code TemiRepository}에 섞여 있던 센서 데이터 가공 로직을 sensor 도메인으로 분리했다.
 * 데이터 접근(API 호출)은 repository가, 센서 값의 의미 해석은 이 클래스가 책임진다.</p>
 *
 * <p>공기질 이벤트가 아니거나 값을 판별할 수 없으면 항상 "보통"으로 폴백한다.</p>
 */
public final class AirQualityResolver {

    private AirQualityResolver() {
    }

    /** 센서 데이터에서 공기질 등급(좋음/보통/나쁨)을 추출한다. 판별 불가 시 "보통". */
    public static String resolve(Map<String, Object> data) {
        if (data == null) {
            return "보통";
        }
        String type = (str(data.get("sensorType")) + str(data.get("eventType"))).toUpperCase();
        boolean isAir = type.contains("DUST") || type.contains("AIR")
                || type.contains("PM") || type.contains("미세");
        if (!isAir) {
            return "보통";
        }
        // value(문자열) 우선, 없으면 payload의 grade/pm25 확인
        String raw = str(data.get("value"));
        if (raw.isEmpty() && data.get("payload") instanceof Map) {
            Map<?, ?> p = (Map<?, ?>) data.get("payload");
            Object grade = p.get("grade");
            Object pm = p.get("pm25");
            if (grade != null) {
                raw = grade.toString();
            } else if (pm != null) {
                raw = pm.toString();
            }
        }
        raw = raw.trim();
        if (raw.contains("좋")) {
            return "좋음";
        }
        if (raw.contains("나쁨") || raw.toUpperCase().contains("BAD")) {
            return "나쁨";
        }
        if (raw.contains("보통") || raw.toUpperCase().contains("NORMAL") || raw.toUpperCase().contains("MODERATE")) {
            return "보통";
        }
        try {
            double pm = Double.parseDouble(raw);
            if (pm <= 15) {
                return "좋음";
            }
            if (pm <= 35) {
                return "보통";
            }
            return "나쁨";
        } catch (NumberFormatException ignored) {
            // 숫자가 아니면 아래 기본값으로
        }
        return "보통";
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
