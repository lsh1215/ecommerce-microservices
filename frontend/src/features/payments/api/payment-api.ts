import { paymentClient } from '@/lib/api-client';
import type { PaymentResponse } from '@/types/api-responses';
import type { ProcessPaymentRequest, RefundRequest } from '../types/payment.types';

export const PaymentAPI = {
  process: (request: ProcessPaymentRequest) =>
    paymentClient.post<PaymentResponse>('/api/payments/process', request),

  getByOrderId: (orderId: string | number) =>
    paymentClient.get<PaymentResponse>(`/api/payments/order/${orderId}`),

  refund: (paymentId: string | number, request: RefundRequest) =>
    paymentClient.post<PaymentResponse>(`/api/payments/${paymentId}/refund`, request),
};
