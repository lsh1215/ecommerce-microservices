import { orderClient } from '@/lib/api-client';
import type { PageResponse } from '@/types';
import type { OrderResponse } from '@/types/api-responses';
import type { CreateOrderRequest, OrderListParams } from '../types/order.types';

export const OrderAPI = {
  myOrders: (params: OrderListParams) => {
    const query = new URLSearchParams({
      customerId: String(params.customerId),
      ...(params.page != null && { page: String(params.page) }),
      ...(params.size != null && { size: String(params.size) }),
    });
    return orderClient.get<PageResponse<OrderResponse>>(`/api/orders/my?${query}`);
  },

  detail: (id: string | number) => orderClient.get<OrderResponse>(`/api/orders/${id}`),

  create: (request: CreateOrderRequest) => orderClient.post<OrderResponse>('/api/orders', request),

  cancel: (id: string | number) => orderClient.post<OrderResponse>(`/api/orders/${id}/cancel`),
};
