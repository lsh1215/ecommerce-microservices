export interface ProcessPaymentRequest {
  orderId: number;
  amount: number;
  paymentMethod: 'CARD';
}

export interface RefundRequest {
  reason: string;
}
