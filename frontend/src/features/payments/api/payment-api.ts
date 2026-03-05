import { apiClient } from '@/lib/api-client';
import type { Payment, ProcessPaymentRequest } from '../types/payment.types';

export const PaymentAPI = {
  process: (request: ProcessPaymentRequest) =>
    apiClient.post<Payment>('/api/v1/payments', request),

  detail: (id: string) => apiClient.get<Payment>(`/api/v1/payments/${id}`),
};
