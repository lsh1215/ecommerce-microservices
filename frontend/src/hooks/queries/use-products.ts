import { useQuery } from '@tanstack/react-query';
import { ProductAPI } from '@/features/products/api/product-api';
import { InventoryAPI } from '@/features/inventory/api/inventory-api';
import { mapProductResponse, mapProductDetailResponse } from '@/lib/mappers';
import type { ProductListParams } from '@/features/products/types/product.types';

export function useProducts(params?: ProductListParams) {
  return useQuery({
    queryKey: ['products', params],
    queryFn: async () => {
      const res = await ProductAPI.list(params);
      if (!res.success || !res.data) {
        throw new Error(res.error?.message ?? 'Failed to fetch products');
      }
      return {
        ...res.data,
        content: res.data.content.map(mapProductResponse),
      };
    },
  });
}

export function useProduct(publicId: string | undefined) {
  return useQuery({
    queryKey: ['product', publicId],
    enabled: !!publicId,
    queryFn: async () => {
      const res = await ProductAPI.detail(publicId!);
      if (!res.success || !res.data) {
        throw new Error(res.error?.message ?? 'Failed to fetch product');
      }

      const detail = res.data;
      const inventories = await Promise.all(
        (detail.variants ?? []).map(async (v) => {
          const invRes = await InventoryAPI.getByVariantId(v.id);
          return invRes.success && invRes.data ? invRes.data : null;
        }),
      );

      return mapProductDetailResponse(
        detail,
        inventories.filter((inv) => inv !== null),
      );
    },
  });
}

export function useProductSearch(query: string, page = 0, size = 20) {
  return useQuery({
    queryKey: ['products', 'search', query, page, size],
    enabled: query.length > 0,
    queryFn: async () => {
      const res = await ProductAPI.search(query, page, size);
      if (!res.success || !res.data) {
        throw new Error(res.error?.message ?? 'Search failed');
      }
      return {
        ...res.data,
        content: res.data.content.map(mapProductResponse),
      };
    },
  });
}
