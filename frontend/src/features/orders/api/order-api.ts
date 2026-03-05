import { apiClient } from '@/lib/api-client';
import type { PageResponse } from '@/types';
import type { Order, CreateOrderRequest } from '../types/order.types';

export const OrderAPI = {
  list: () => apiClient.get<PageResponse<Order>>('/api/v1/orders'),

  detail: (id: string) => apiClient.get<Order>(`/api/v1/orders/${id}`),

  create: (request: CreateOrderRequest) => apiClient.post<Order>('/api/v1/orders', request),
};
