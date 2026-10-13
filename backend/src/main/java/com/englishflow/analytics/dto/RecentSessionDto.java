package com.englishflow.analytics.dto;

import java.time.Instant;

public record RecentSessionDto(
    String type,
    long count,
    Instant completedAt
) {}
