package com.example.javabackend.dto;

import java.util.Map;

public record QuoteConfigResponse(
        String versionCode,
        String description,
        String currency,
        Map<String, Object> config) {
}
