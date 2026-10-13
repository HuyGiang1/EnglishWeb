import { apiClient } from './client';

export interface BulkImportRowError {
  rowNumber: number;
  reason: string;
  field?: string;
}

export interface BulkImportRowPreview {
  rowNumber: number;
  term: string;
  partOfSpeech?: string;
  vietnameseMeaning: string;
  phoneticIpa?: string;
  englishDefinition?: string;
  exampleSentence?: string;
  exampleTranslation?: string;
  notes?: string;
  source?: string;
  difficultyLevel?: string;
  status: 'VALID' | 'DUPLICATE_FILE' | 'DUPLICATE_LIBRARY' | 'INVALID';
  validationMessage?: string;
}

export interface BulkImportPreviewResponse {
  totalRows: number;
  validRows: number;
  duplicateRows: number;
  errorRows: number;
  errors: BulkImportRowError[];
  previewRows: BulkImportRowPreview[];
  previewSample: BulkImportRowPreview[];
}

export interface BulkImportItem {
  term: string;
  partOfSpeech?: string;
  vietnameseMeaning: string;
  phoneticIpa?: string;
  englishDefinition?: string;
  exampleSentence?: string;
  exampleTranslation?: string;
  notes?: string;
  source?: string;
  difficultyLevel?: string;
}

export interface BulkImportConfirmPayload {
  items: BulkImportItem[];
  collectionId?: string;
  duplicatePolicy?: 'SKIP' | 'REJECT';
}

export interface BulkImportConfirmResponse {
  importedCount: number;
  skippedCount: number;
  rejectedCount: number;
}

export const importApi = {
  /**
   * Dry-run preview: upload CSV/TSV to parse and preview rows without DB writes
   */
  async preview(file: File): Promise<BulkImportPreviewResponse> {
    const formData = new FormData();
    formData.append('file', file);

    return apiClient.post<BulkImportPreviewResponse>(
      '/api/v1/vocabulary/bulk-import/preview',
      formData
    );
  },

  /**
   * Confirm bulk import: atomically persists valid items to library and optional collection
   */
  async confirm(payload: BulkImportConfirmPayload): Promise<BulkImportConfirmResponse> {
    return apiClient.post<BulkImportConfirmResponse>(
      '/api/v1/vocabulary/bulk-import/confirm',
      payload
    );
  },

  /**
   * Download official CSV or TSV template with UTF-8 BOM
   */
  async downloadTemplate(format: 'csv' | 'tsv'): Promise<Blob> {
    const res = await fetch(`/api/v1/vocabulary/bulk-import/template?format=${format}`, {
      method: 'GET',
      credentials: 'include',
    });

    if (!res.ok) {
      throw new Error(`Failed to download template: HTTP ${res.status}`);
    }

    return res.blob();
  },
};
