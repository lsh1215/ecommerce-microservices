export interface CheckoutShippingAddress {
  recipientName: string;
  phone: string;
  zipCode: string;
  address1: string;
  address2?: string;
}

export type CheckoutPaymentMethod = 'CARD';

export interface CheckoutState {
  shippingAddress: CheckoutShippingAddress | null;
  paymentMethod: CheckoutPaymentMethod | null;
}
