import { apiClient } from '@/lib/api-client';
import type { PageResponse } from '@/types';
import type { OrderResponse } from '@/types/api-responses';
import type { CreateOrderRequest, OrderListParams } from '../types/order.types';

export const OrderAPI = {
  list: (params: OrderListParams) => {
    const query = new URLSearchParams({
      customerId: String(params.customerId),
      ...(params.page != null && { page: String(params.page) }),
      ...(params.size != null && { size: String(params.size) }),
    });
    return apiClient.get<PageResponse<OrderResponse>>(`/api/orders?${query}`);
  },

  detail: (publicId: string) =>
    apiClient.get<OrderResponse>(`/api/orders/${publicId}`),

  create: (request: CreateOrderRequest) =>
    apiClient.post<OrderResponse>('/api/orders', request),

  cancel: (publicId: string) =>
    apiClient.post<OrderResponse>(`/api/orders/${publicId}/cancel`, {}),
};
