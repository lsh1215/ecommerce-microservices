import { productClient } from '@/lib/api-client';
import type { PageResponse } from '@/types';
import type { BrandResponse, ProductResponse } from '@/types/api-responses';
import type { ProductListParams } from '../types/product.types';

function buildQuery(params?: ProductListParams): string {
  if (!params) return '';
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value == null || value === '') continue;
    search.set(key, String(value));
  }
  const qs = search.toString();
  return qs ? `?${qs}` : '';
}

export const ProductAPI = {
  list: (params?: ProductListParams) =>
    productClient.get<PageResponse<ProductResponse>>(`/api/products${buildQuery(params)}`),

  detail: (id: string | number) => productClient.get<ProductResponse>(`/api/products/${id}`),

  search: (q: string, page = 0, size = 20) =>
    productClient.get<PageResponse<ProductResponse>>(
      `/api/products?keyword=${encodeURIComponent(q)}&page=${page}&size=${size}`,
    ),
};

export const BrandAPI = {
  list: () => productClient.get<BrandResponse[]>('/api/brands'),
  detail: (id: string | number) => productClient.get<BrandResponse>(`/api/brands/${id}`),
};
