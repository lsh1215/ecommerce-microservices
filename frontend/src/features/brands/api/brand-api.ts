import { apiClient } from '@/lib/api-client';
import type { BrandResponse } from '@/types/api-responses';

export const BrandAPI = {
  list: () => apiClient.get<BrandResponse[]>('/api/brands'),

  detail: (slug: string) => apiClient.get<BrandResponse>(`/api/brands/${slug}`),
};
