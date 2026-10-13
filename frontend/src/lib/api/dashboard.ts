import { apiClient } from "./client";

export type StreakStatus = "ACTIVE_STUDIED_TODAY" | "ACTIVE_PENDING_TODAY" | "BROKEN_RESET";

export interface RecentSession {
  type: string;
  count: number;
  completedAt: string;
}

export interface DashboardSummary {
  greeting: string;
  currentDate: string;
  vocabularyTotal: number;
  dueReviewCount: number;
  dailyGoalTarget: number;
  dailyGoalCompletedCount: number;
  goalProgressPercentage: number;
  currentStreakDays: number;
  streakStatus: StreakStatus;
  recentSessions: RecentSession[];
}

export const dashboardApi = {
  getSummary: async (): Promise<DashboardSummary> => {
    return apiClient.get<DashboardSummary>("/api/v1/dashboard/summary");
  },
  getHeatmap: async (): Promise<import("./analytics").HeatmapResponse> => {
    return apiClient.get<import("./analytics").HeatmapResponse>("/api/v1/analytics/activity-heatmap");
  },
};
