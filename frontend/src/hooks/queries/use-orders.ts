import { useQuery } from '@tanstack/react-query';
import { OrderAPI } from '@/features/orders/api/order-api';
import { mapOrderResponse } from '@/lib/mappers';
import type { OrderListParams } from '@/features/orders/types/order.types';

export function useMyOrders(params: OrderListParams) {
  return useQuery({
    queryKey: ['orders', params],
    queryFn: async () => {
      const res = await OrderAPI.myOrders(params);
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

export function useOrder(id: string | undefined) {
  return useQuery({
    queryKey: ['order', id],
    enabled: !!id,
    queryFn: async () => {
      const res = await OrderAPI.detail(id!);
      if (!res.success || !res.data) {
        throw new Error(res.error?.message ?? 'Failed to fetch order');
      }
      return mapOrderResponse(res.data);
    },
  });
}
