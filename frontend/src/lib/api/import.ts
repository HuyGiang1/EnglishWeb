import { apiClient, ApiError } from './client';

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

export type PasteField = "term" | "vietnameseMeaning" | "phoneticIpa" | "partOfSpeech" | "ignore";
export interface PasteRow {
  rowNumber: number; term: string; vietnameseMeaning: string; phoneticIpa: string;
  partOfSpeech: string; excluded: boolean;
}
export interface PastePreviewRow extends PasteRow {
  cells: string[]; status: string; issues: string[]; existingMeaning?: string | null; existingIpa?: string | null;
}
export interface PastePreview {
  delimiter: string; mapping: PasteField[]; warnings: string[]; rows: PastePreviewRow[];
}
export interface PasteResult {
  createdCount: number; linkedCount: number; alreadyPresentCount: number;
  duplicateCount: number; excludedCount: number; rejectedCount: number; collectionId?: string | null;
}
export const PASTE_TEXT_BYTES = 2 * 1024 * 1024;
export const PASTE_REQUEST_BYTES = 4 * 1024 * 1024;
export const PASTE_MAX_ROWS = 1000;
async function pastePost<T>(route: string, payload: unknown): Promise<T> {
  if (new TextEncoder().encode(JSON.stringify(payload)).length > PASTE_REQUEST_BYTES)
    throw new ApiError(413, "Import request exceeds 4 MiB.", "PAYLOAD_TOO_LARGE");
  return apiClient.post<T>("/api/v1/vocabulary/bulk-import/paste/" + route, payload);
}
export const pasteImportApi = {
  preview: (payload: { text: string; delimiter: string; mapping: PasteField[] | null; skipHeader: boolean; collectionId?: string }) =>
    pastePost<PastePreview>("preview", payload),
  validate: (payload: { rows: PasteRow[]; collectionId?: string }) => pastePost<PastePreview>("validate", payload),
  confirm: (payload: { rows: PasteRow[]; collectionId?: string; conflictPolicy: "PRESERVE_EXISTING" }) =>
    pastePost<PasteResult>("confirm", payload),
};
