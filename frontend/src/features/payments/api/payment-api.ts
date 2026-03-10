import { apiClient } from '@/lib/api-client';
import type { PaymentResponse } from '@/types/api-responses';
import type { ProcessPaymentRequest, RefundRequest } from '../types/payment.types';

export const PaymentAPI = {
  process: (request: ProcessPaymentRequest) =>
    apiClient.post<PaymentResponse>('/api/payments', request),

  detail: (publicId: string) => apiClient.get<PaymentResponse>(`/api/payments/${publicId}`),

  refund: (publicId: string, request: RefundRequest) =>
    apiClient.post<PaymentResponse>(`/api/payments/${publicId}/refund`, request),
};
