import { apiClient } from "./client";

export interface QuizSummary {
  id: string;
  title: string;
  cefrLevel: string;
  topicTag: string;
  description: string;
  questionCount: number;
  attemptCount: number;
  bestScore: number | null;
  latestScore: number | null;
}

export interface QuizQuestion {
  questionId: string;
  prompt: string;
  questionType: "MULTIPLE_CHOICE" | "CLOZE";
  options: string[];
  orderIndex: number;
}

export interface QuizSavedAnswer {
  questionId: string;
  selectedAnswer: string;
}

export interface QuizStartResponse {
  quizAttemptId: string;
  attemptId: string;
  contentId: string;
  title: string;
  totalQuestions: number;
  questions: QuizQuestion[];
  savedAnswers: QuizSavedAnswer[];
}

export interface QuizSubmitAnswerRequest {
  questionId: string;
  attemptId?: string;
  selectedAnswer: string;
}

export interface QuizSubmitAnswerResponse {
  questionId: string;
  saved: boolean;
}

export interface QuizAnswerItem {
  questionId: string;
  selectedAnswer: string;
}

export interface QuizCompleteRequest {
  attemptId?: string;
  answers?: QuizAnswerItem[];
}

export interface QuizQuestionResult {
  questionId: string;
  prompt: string;
  questionType: "MULTIPLE_CHOICE" | "CLOZE";
  options: string[];
  selectedAnswer: string;
  canonicalAnswer: string;
  isCorrect: boolean;
  explanation: string;
  orderIndex: number;
}

export interface QuizCompleteResponse {
  quizAttemptId: string;
  attemptId: string;
  contentId: string;
  title: string;
  scorePercentage: number;
  correctCount: number;
  totalQuestions: number;
  incorrectCount: number;
  unansweredCount: number;
  verifiedActivityCreated: boolean;
  completedAt: string;
  questions: QuizQuestionResult[];
}

export interface QuizHistoryItem {
  attemptId: string;
  quizAttemptId: string;
  contentId: string;
  title: string;
  cefrLevel: string;
  topicTag: string;
  scorePercentage: number;
  correctAnswers: number;
  totalQuestions: number;
  completedAt: string;
}

export const quizApi = {
  /**
   * List available published diagnostic quizzes with question counts and user performance.
   */
  async listQuizzes(): Promise<QuizSummary[]> {
    return apiClient.get<QuizSummary[]>("/api/v1/quizzes");
  },

  /**
   * Retrieve summary metadata for a specific quiz pack.
   */
  async getQuizSummary(contentId: string): Promise<QuizSummary> {
    return apiClient.get<QuizSummary>(`/api/v1/quizzes/${contentId}`);
  },

  /**
   * Initialize or resume a quiz attempt. Correct answers and explanations are withheld.
   */
  async startQuiz(contentId: string, attemptId?: string): Promise<QuizStartResponse> {
    return apiClient.post<QuizStartResponse>(`/api/v1/quizzes/${contentId}/start`, {
      attemptId,
    });
  },

  /**
   * Submit an individual question draft answer while navigating questions.
   */
  async submitAnswer(quizAttemptId: string, request: QuizSubmitAnswerRequest): Promise<QuizSubmitAnswerResponse> {
    return apiClient.post<QuizSubmitAnswerResponse>(`/api/v1/quizzes/attempts/${quizAttemptId}/answers`, request);
  },

  /**
   * Finalize and grade a quiz attempt. Returns full scorecard and question explanations.
   */
  async completeQuiz(quizAttemptId: string, request?: QuizCompleteRequest): Promise<QuizCompleteResponse> {
    return apiClient.post<QuizCompleteResponse>(`/api/v1/quizzes/attempts/${quizAttemptId}/complete`, request);
  },

  /**
   * Retrieve completed scorecard snapshot for review. Zero side-effects.
   */
  async getCompletedAttempt(quizAttemptId: string): Promise<QuizCompleteResponse> {
    return apiClient.get<QuizCompleteResponse>(`/api/v1/quizzes/attempts/${quizAttemptId}`);
  },

  /**
   * List historical completed attempts for the current user.
   */
  async getUserHistory(): Promise<QuizHistoryItem[]> {
    return apiClient.get<QuizHistoryItem[]>("/api/v1/quizzes/history");
  },
};
