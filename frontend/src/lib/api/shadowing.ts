import { apiClient } from "./client";

export type SelfRating = "NEEDS_WORK" | "ACCEPTABLE" | "GOOD" | "FLUENT";
export type AudioReadiness = "MISSING" | "SYNTHETIC_FIXTURE" | "LOCAL_SPOKEN" | "LICENSED_DISTRIBUTABLE";

export interface ShadowingExerciseSummary {
  contentId: string;
  title: string;
  topicTag: string;
  cefrLevel: string;
  audioDurationSeconds: number;
  isAudioAvailable: boolean;
  audioReadiness?: AudioReadiness | string;
  isSpokenSpeech?: boolean;
  takeCount: number;
  lastSelfRating: SelfRating | null;
}

export interface ShadowingExerciseDetail {
  contentId: string;
  title: string;
  topicTag: string;
  cefrLevel: string;
  transcriptText: string;
  audioUrl: string;
  audioDurationSeconds: number;
  isAudioAvailable: boolean;
  audioReadiness?: AudioReadiness | string;
  isSpokenSpeech?: boolean;
}

export interface ShadowingCompleteRequest {
  attemptId: string;
  selfRating: SelfRating;
  durationSeconds: number;
  recordingDurationSeconds?: number;
}

export interface ShadowingCompleteResponse {
  attemptId: string;
  contentId: string;
  selfRating: SelfRating;
  hasRecordingSaved: boolean;
  verifiedActivityCreated?: boolean;
  completedAt: string;
}

export const shadowingApi = {
  /**
   * List available published shadowing exercises.
   */
  async listExercises(): Promise<ShadowingExerciseSummary[]> {
    return apiClient.get<ShadowingExerciseSummary[]>("/api/v1/shadowing");
  },

  /**
   * Retrieve prompt, reference transcript, and audio reference for an exercise.
   */
  async getExercise(contentId: string): Promise<ShadowingExerciseDetail> {
    return apiClient.get<ShadowingExerciseDetail>(`/api/v1/shadowing/${contentId}`);
  },

  /**
   * Complete practice take with learner self-assessment rating.
   */
  async completeTake(
    contentId: string,
    request: ShadowingCompleteRequest
  ): Promise<ShadowingCompleteResponse> {
    return apiClient.post<ShadowingCompleteResponse>(
      `/api/v1/shadowing/${contentId}/complete`,
      request
    );
  },
};
