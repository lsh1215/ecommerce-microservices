import { useQuery } from '@tanstack/react-query';
import { DropAPI } from '@/features/drops/api/drop-api';
import { mapDropResponse } from '@/lib/mappers';

export function useDrops(page = 0, size = 20) {
  return useQuery({
    queryKey: ['drops', page, size],
    queryFn: async () => {
      const res = await DropAPI.list(page, size);
      if (!res.success || !res.data) {
        throw new Error(res.error?.message ?? 'Failed to fetch drops');
      }
      return {
        ...res.data,
        content: res.data.content.map(mapDropResponse),
      };
    },
  });
}

export function useDrop(publicId: string | undefined) {
  return useQuery({
    queryKey: ['drop', publicId],
    enabled: !!publicId,
    queryFn: async () => {
      const res = await DropAPI.detail(publicId!);
      if (!res.success || !res.data) {
        throw new Error(res.error?.message ?? 'Failed to fetch drop');
      }
      return mapDropResponse(res.data);
    },
  });
}
