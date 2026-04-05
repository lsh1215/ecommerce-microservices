export interface CheckoutShippingAddress {
  recipientName: string;
  phone: string;
  zipCode: string;
  address1: string;
  address2?: string;
}

export type CheckoutPaymentMethod = 'CARD' | 'BANK_TRANSFER' | 'VIRTUAL_ACCOUNT';

export interface CheckoutState {
  shippingAddress: CheckoutShippingAddress | null;
  paymentMethod: CheckoutPaymentMethod | null;
}
