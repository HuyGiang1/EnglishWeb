package com.englishflow.analytics.dto;

public record DayActivityDto(
    String date,
    String state,
    long actionCount,
    int intensityLevel,
    boolean goalCompleted,
    DayActivityBreakdownDto breakdown
) {}
