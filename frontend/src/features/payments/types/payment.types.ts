export interface ProcessPaymentRequest {
  orderId: number;
  amount: number;
  currency: string;
  idempotencyKey: string;
  paymentMethod: string;
}

export interface RefundRequest {
  reason: string;
}
