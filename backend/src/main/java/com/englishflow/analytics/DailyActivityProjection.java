package com.englishflow.analytics;

import java.time.LocalDate;

public interface DailyActivityProjection {
    LocalDate getHistoricalLocalDate();
    String getEventType();
    long getActionCount();
}
