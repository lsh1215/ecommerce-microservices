import { apiClient } from '@/lib/api-client';
import type { PageResponse } from '@/types';
import type { ProductResponse, ProductDetailResponse } from '@/types/api-responses';
import type { ProductListParams } from '../types/product.types';

export const ProductAPI = {
  list: (params?: ProductListParams) => {
    const query = params ? `?${new URLSearchParams(params as Record<string, string>)}` : '';
    return apiClient.get<PageResponse<ProductResponse>>(`/api/products${query}`);
  },

  detail: (publicId: string) =>
    apiClient.get<ProductDetailResponse>(`/api/products/${publicId}`),

  search: (q: string, page = 0, size = 20) =>
    apiClient.get<PageResponse<ProductResponse>>(
      `/api/products/search?q=${encodeURIComponent(q)}&page=${page}&size=${size}`,
    ),
};
