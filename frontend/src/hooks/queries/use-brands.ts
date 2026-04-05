import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import { mapBrandResponse } from '@/lib/mappers';
import type { BrandResponse } from '@/types/api-responses';

export function useBrands() {
  return useQuery({
    queryKey: ['brands'],
    queryFn: async () => {
      const res = await apiClient.get<BrandResponse[]>('/api/brands');
      if (!res.success || !res.data) {
        throw new Error(res.error?.message ?? 'Failed to fetch brands');
      }
      return res.data.map(mapBrandResponse);
    },
  });
}
