import { apiClient } from "./client";

export interface FolderItem {
  id: string; name: string; description?: string | null; studySetCount: number;
  createdAt: string; updatedAt: string;
}
export interface FolderPage {
  content: FolderItem[]; page: number; size: number; totalElements: number; totalPages: number;
}
export interface FolderPayload { name: string; description?: string | null }
export const foldersApi = {
  list: (page = 0): Promise<FolderPage> => apiClient.get("/folders", { params: { page, size: 20 } }),
  create: (payload: FolderPayload): Promise<FolderItem> => apiClient.post("/folders", payload),
  update: (id: string, payload: FolderPayload): Promise<FolderItem> => apiClient.put("/folders/" + encodeURIComponent(id), payload),
  delete: (id: string): Promise<void> => apiClient.delete("/folders/" + encodeURIComponent(id)),
};
