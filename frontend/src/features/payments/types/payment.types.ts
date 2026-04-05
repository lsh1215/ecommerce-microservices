export interface ProcessPaymentRequest {
  orderId: number;
  amount: number;
  paymentMethod: 'CARD' | 'BANK_TRANSFER' | 'VIRTUAL_ACCOUNT';
}

export interface RefundRequest {
  reason: string;
}
