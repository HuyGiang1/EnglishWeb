import { apiClient } from "./client";

export type DayState = "ACTIVE_DAY" | "ZERO_ACTIVITY" | "PRE_REGISTRATION" | "FUTURE_DATE";

export interface DayActivityBreakdown {
  flashcardReviews: number;
  dictationExercises: number;
  shadowingTakes: number;
  /** Legacy event-type counter matching QUIZ_QUESTION events; equals completed quiz assessment sessions in M2. */
  quizQuestions: number;
  /** Semantic alias for completed quiz assessment sessions. */
  quizzesCompleted?: number;
}

export interface DayActivity {
  date: string;
  state: DayState;
  actionCount: number;
  intensityLevel: 0 | 1 | 2 | 3 | 4;
  goalCompleted: boolean;
  breakdown: DayActivityBreakdown;
}

export interface HeatmapResponse {
  startDate: string;
  endDate: string;
  userTimezone: string;
  accountCreatedAt: string;
  currentStreakDays: number;
  streakStatus: "ACTIVE_STUDIED_TODAY" | "ACTIVE_PENDING_TODAY" | "BROKEN_RESET";
  longestStreakDays: number;
  totalActiveDays: number;
  days: Record<string, DayActivity>;
}

export const analyticsApi = {
  getHeatmap: async (): Promise<HeatmapResponse> => {
    return apiClient.get<HeatmapResponse>("/api/v1/analytics/activity-heatmap");
  },
};
