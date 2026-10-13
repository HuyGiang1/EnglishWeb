import { apiClient } from "./client";

export interface DictationExerciseSummary {
  contentId: string;
  title: string;
  cefrLevel: string;
  topicTag: string;
  audioUrl: string;
  audioDurationSeconds: number | null;
  isCompleted: boolean;
  lastAccuracyScore: number | null;
  isAudioAvailable?: boolean;
}

export interface DictationExerciseDetail {
  contentId: string;
  title: string;
  cefrLevel: string;
  topicTag: string;
  audioUrl: string;
  audioDurationSeconds: number | null;
  isAudioAvailable?: boolean;
}


export interface DictationSubmitRequest {
  attemptId: string;
  submittedTranscript: string;
  durationSeconds?: number;
}

export type DiffTokenType = "EQUAL" | "MISSING" | "EXTRA" | "SUBSTITUTION";

export interface DiffToken {
  text: string;
  type: DiffTokenType;
  expectedWord: string | null;
}

export interface DictationSubmitResponse {
  attemptId: string;
  contentId: string;
  canonicalTranscript: string;
  submittedTranscript: string;
  accuracyScore: number;
  wordCount: number;
  errorCount: number;
  isPassed: boolean;
  diffTokens: DiffToken[];
}

export const dictationApi = {
  /**
   * Lists available dictation exercises for the authenticated user.
   */
  async listExercises(): Promise<DictationExerciseSummary[]> {
    return apiClient.get<DictationExerciseSummary[]>("/api/v1/dictation");
  },

  /**
   * Retrieves exercise metadata (audio URL, title, level). Canonical answer withheld.
   */
  async getExercise(contentId: string): Promise<DictationExerciseDetail> {
    return apiClient.get<DictationExerciseDetail>(`/api/v1/dictation/${contentId}`);
  },

  /**
   * Submits a user's typed transcription for authoritative server grading.
   */
  async submitTranscription(
    contentId: string,
    payload: DictationSubmitRequest
  ): Promise<DictationSubmitResponse> {
    return apiClient.post<DictationSubmitResponse>(`/api/v1/dictation/${contentId}/submit`, payload);
  },
};
