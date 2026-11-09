import { apiClient } from "./client";
import { VocabularyPageResponse } from "./vocabulary";

export interface CollectionItem {
  id: string;
  title: string;
  description?: string | null;
  wordCount: number;
  folderId?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CollectionPageResponse {
  content: CollectionItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateCollectionPayload {
  folderId?: string | null;
  title: string;
  description?: string | null;
}

export interface UpdateCollectionPayload {
  title: string;
  description?: string | null;
}

export interface AssignVocabularyPayload {
  vocabularyIds: string[];
}

export interface AssignVocabularyResponse {
  addedCount: number;
}

export interface CollectionSummaryItem {
  id: string;
  title: string;
}

export interface ListCollectionsParams {
  folderId?: string;
  unfiled?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export const collectionsApi = {
  list: (params: ListCollectionsParams = {}): Promise<CollectionPageResponse> => {
    return apiClient.get<CollectionPageResponse>("/collections", {
      params: {
        page: params.page ?? 0,
        size: params.size ?? 20,
        sort: params.sort || "updatedAt,desc",
        folderId: params.folderId,
        unfiled: params.unfiled,
      },
    });
  },

  move: (id: string, folderId: string | null): Promise<CollectionItem> =>
    apiClient.put<CollectionItem>("/collections/" + encodeURIComponent(id) + "/folder", { folderId }),

  getById: (id: string): Promise<CollectionItem> => {
    return apiClient.get<CollectionItem>(`/collections/${encodeURIComponent(id)}`);
  },

  create: (payload: CreateCollectionPayload): Promise<CollectionItem> => {
    return apiClient.post<CollectionItem>("/collections", payload);
  },

  update: (id: string, payload: UpdateCollectionPayload): Promise<CollectionItem> => {
    return apiClient.put<CollectionItem>(`/collections/${encodeURIComponent(id)}`, payload);
  },

  delete: (id: string): Promise<void> => {
    return apiClient.delete<void>(`/collections/${encodeURIComponent(id)}`);
  },

  listVocabularies: (
    id: string,
    params: { page?: number; size?: number } = {}
  ): Promise<VocabularyPageResponse> => {
    return apiClient.get<VocabularyPageResponse>(`/collections/${encodeURIComponent(id)}/vocabularies`, {
      params: {
        page: params.page ?? 0,
        size: params.size ?? 20,
      },
    });
  },

  assignVocabularies: (
    id: string,
    vocabularyIds: string[]
  ): Promise<AssignVocabularyResponse> => {
    return apiClient.post<AssignVocabularyResponse>(`/collections/${encodeURIComponent(id)}/vocabularies`, {
      vocabularyIds,
    });
  },

  removeVocabulary: (id: string, vocabId: string): Promise<void> => {
    return apiClient.delete<void>(
      `/collections/${encodeURIComponent(id)}/vocabularies/${encodeURIComponent(vocabId)}`
    );
  },

  getCollectionsForVocabulary: (vocabId: string): Promise<CollectionSummaryItem[]> => {
    return apiClient.get<CollectionSummaryItem[]>(`/vocabulary/${encodeURIComponent(vocabId)}/collections`);
  },
};
