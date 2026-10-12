import { apiClient } from "./client";

export type PartOfSpeech =
  | "noun"
  | "verb"
  | "adjective"
  | "adverb"
  | "idiom"
  | "phrase"
  | "preposition"
  | "conjunction"
  | "other";

export type DifficultyLevel = "A1" | "A2" | "B1" | "B2" | "C1" | "C2";

export interface SrsStatus {
  state: string;
  dueDate?: string | null;
  intervalDays: number;
}

export interface VocabularyItem {
  id: string;
  term: string;
  partOfSpeech: string;
  phoneticIpa?: string | null;
  vietnameseMeaning: string;
  englishDefinition?: string | null;
  exampleSentence?: string | null;
  exampleTranslation?: string | null;
  notes?: string | null;
  source?: string | null;
  difficultyLevel?: string | null;
  createdAt: string;
  updatedAt: string;
  srsStatus?: SrsStatus | null;
}

export interface VocabularyPageResponse {
  content: VocabularyItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateVocabularyPayload {
  term: string;
  partOfSpeech: string;
  phoneticIpa?: string | null;
  vietnameseMeaning: string;
  englishDefinition?: string | null;
  exampleSentence?: string | null;
  exampleTranslation?: string | null;
  notes?: string | null;
  source?: string | null;
  difficultyLevel?: string | null;
}

export interface UpdateVocabularyPayload {
  term: string;
  partOfSpeech: string;
  phoneticIpa?: string | null;
  vietnameseMeaning: string;
  englishDefinition?: string | null;
  exampleSentence?: string | null;
  exampleTranslation?: string | null;
  notes?: string | null;
  source?: string | null;
  difficultyLevel?: string | null;
}

export interface DuplicateEntryItem {
  id: string;
  term: string;
  partOfSpeech: string;
  vietnameseMeaning: string;
}

export interface DuplicateCheckResponse {
  exists: boolean;
  existingEntries: DuplicateEntryItem[];
}

export interface ListVocabularyParams {
  search?: string;
  partOfSpeech?: string;
  difficultyLevel?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const vocabularyApi = {
  list: (params: ListVocabularyParams = {}): Promise<VocabularyPageResponse> => {
    return apiClient.get<VocabularyPageResponse>("/vocabulary", {
      params: {
        search: params.search || undefined,
        partOfSpeech: params.partOfSpeech || undefined,
        difficultyLevel: params.difficultyLevel || undefined,
        page: params.page ?? 0,
        size: params.size ?? 20,
        sort: params.sort || "createdAt,desc",
      },
    });
  },

  getById: (id: string): Promise<VocabularyItem> => {
    return apiClient.get<VocabularyItem>(`/vocabulary/${encodeURIComponent(id)}`);
  },

  create: (payload: CreateVocabularyPayload): Promise<VocabularyItem> => {
    return apiClient.post<VocabularyItem>("/vocabulary", payload);
  },

  update: (id: string, payload: UpdateVocabularyPayload): Promise<VocabularyItem> => {
    return apiClient.put<VocabularyItem>(`/vocabulary/${encodeURIComponent(id)}`, payload);
  },

  delete: (id: string): Promise<void> => {
    return apiClient.delete<void>(`/vocabulary/${encodeURIComponent(id)}`);
  },

  checkDuplicates: (term: string, partOfSpeech?: string): Promise<DuplicateCheckResponse> => {
    return apiClient.get<DuplicateCheckResponse>("/vocabulary/duplicates/check", {
      params: {
        term,
        partOfSpeech: partOfSpeech || undefined,
      },
    });
  },
};
