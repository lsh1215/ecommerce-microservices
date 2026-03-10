import { apiClient } from '@/lib/api-client';
import type { PageResponse } from '@/types';
import type { DropEventResponse } from '@/types/api-responses';

export const DropAPI = {
  list: (page = 0, size = 20) =>
    apiClient.get<PageResponse<DropEventResponse>>(`/api/drops?page=${page}&size=${size}`),

  detail: (publicId: string) => apiClient.get<DropEventResponse>(`/api/drops/${publicId}`),
};
