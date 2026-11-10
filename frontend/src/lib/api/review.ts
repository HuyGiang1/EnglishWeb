import { apiClient } from "./client";

export type ReviewRating = "AGAIN" | "HARD" | "GOOD" | "EASY";

export interface ReviewCard {
  srsItemId: string;
  vocabularyId: string;
  term: string;
  partOfSpeech: string;
  phoneticIpa?: string | null;
  vietnameseMeaning: string;
  englishDefinition?: string | null;
  exampleSentence?: string | null;
  intervalDays: number;
  easeFactor: number;
}

export interface ReviewQueueResponse {
  totalDue: number;
  items: ReviewCard[];
}

export interface ReviewSubmissionPayload {
  srsItemId: string;
  attemptId: string;
  grade: ReviewRating;
  durationSeconds?: number;
}

export interface ReviewSubmissionResponse {
  srsItemId: string;
  attemptId: string;
  newIntervalDays: number;
  newEaseFactor: number;
  dueDate: string;
  nextReviewLabel: string;
}

export const reviewApi = {
  getDueCount: (collectionId?: string): Promise<{ totalDue: number }> =>
    apiClient.get("/api/v1/review/due-count", { params: { collectionId } }),
  getQueue: async (params?: { collectionId?: string; limit?: number }): Promise<ReviewQueueResponse> => {
    const query = new URLSearchParams();
    if (params?.collectionId !== undefined) {
      query.set("collectionId", params.collectionId);
    }
    if (params?.limit) {
      query.set("limit", String(params.limit));
    }
    const qs = query.toString();
    const endpoint = `/api/v1/review/queue${qs ? `?${qs}` : ""}`;
    return apiClient.get<ReviewQueueResponse>(endpoint);
  },

  submitGrade: async (payload: ReviewSubmissionPayload): Promise<ReviewSubmissionResponse> => {
    return apiClient.post<ReviewSubmissionResponse>("/api/v1/review/grade", payload);
  },
};
