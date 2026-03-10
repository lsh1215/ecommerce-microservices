import { useQuery } from '@tanstack/react-query';
import { OrderAPI } from '@/features/orders/api/order-api';
import { mapOrderResponse } from '@/lib/mappers';
import type { OrderListParams } from '@/features/orders/types/order.types';

export function useOrders(params: OrderListParams) {
  return useQuery({
    queryKey: ['orders', params],
    queryFn: async () => {
      const res = await OrderAPI.list(params);
      if (!res.success || !res.data) {
        throw new Error(res.error?.message ?? 'Failed to fetch orders');
      }
      return {
        ...res.data,
        content: res.data.content.map(mapOrderResponse),
      };
    },
  });
}

export function useOrder(publicId: string | undefined) {
  return useQuery({
    queryKey: ['order', publicId],
    enabled: !!publicId,
    queryFn: async () => {
      const res = await OrderAPI.detail(publicId!);
      if (!res.success || !res.data) {
        throw new Error(res.error?.message ?? 'Failed to fetch order');
      }
      return mapOrderResponse(res.data);
    },
  });
}
