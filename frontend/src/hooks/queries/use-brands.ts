import { useQuery } from '@tanstack/react-query';
import { BrandAPI } from '@/features/products/api/product-api';
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
