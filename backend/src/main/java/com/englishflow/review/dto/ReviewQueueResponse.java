package com.englishflow.review.dto;

import java.util.List;

public record ReviewQueueResponse(
    long totalDue,
    List<ReviewCardDto> items
) {}
