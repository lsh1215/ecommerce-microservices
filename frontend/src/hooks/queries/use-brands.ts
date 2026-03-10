import { useQuery } from '@tanstack/react-query';
import { BrandAPI } from '@/features/brands/api/brand-api';
import { mapBrandResponse } from '@/lib/mappers';

export function useBrands() {
  return useQuery({
    queryKey: ['brands'],
    queryFn: async () => {
      const res = await BrandAPI.list();
      if (!res.success || !res.data) {
        throw new Error(res.error?.message ?? 'Failed to fetch brands');
      }
      return res.data.map(mapBrandResponse);
    },
  });
}

export function useBrand(slug: string | undefined) {
  return useQuery({
    queryKey: ['brand', slug],
    enabled: !!slug,
    queryFn: async () => {
      const res = await BrandAPI.detail(slug!);
      if (!res.success || !res.data) {
        throw new Error(res.error?.message ?? 'Failed to fetch brand');
      }
      return mapBrandResponse(res.data);
    },
  });
}
