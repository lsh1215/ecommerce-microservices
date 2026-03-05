import { apiClient } from '@/lib/api-client';
import type { PageResponse } from '@/types';
import type { Product, ProductListParams } from '../types/product.types';

export const ProductAPI = {
  list: (params?: ProductListParams) => {
    const query = params ? `?${new URLSearchParams(params as Record<string, string>)}` : '';
    return apiClient.get<PageResponse<Product>>(`/api/v1/products${query}`);
  },

  detail: (id: string) => apiClient.get<Product>(`/api/v1/products/${id}`),

  search: (keyword: string) =>
    apiClient.get<PageResponse<Product>>(`/api/v1/products/search?keyword=${encodeURIComponent(keyword)}`),
};
