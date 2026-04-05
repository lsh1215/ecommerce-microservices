import { apiClient } from '@/lib/api-client';
import type { PageResponse } from '@/types';
import type { ProductResponse } from '@/types/api-responses';
import type { ProductListParams } from '../types/product.types';

export const ProductAPI = {
  list: (params?: ProductListParams) => {
    const query = params ? `?${new URLSearchParams(params as Record<string, string>)}` : '';
    return apiClient.get<PageResponse<ProductResponse>>(`/api/products${query}`);
  },

  detail: (id: string) => apiClient.get<ProductResponse>(`/api/products/${id}`),

  search: (q: string, page = 0, size = 20) =>
    apiClient.get<PageResponse<ProductResponse>>(
      `/api/products?q=${encodeURIComponent(q)}&page=${page}&size=${size}`,
    ),
};
