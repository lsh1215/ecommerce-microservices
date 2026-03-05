export interface ShippingAddress {
  name: string;
  phone: string;
  address: string;
  addressDetail: string;
  zipCode: string;
}

export interface CheckoutState {
  shippingAddress: ShippingAddress | null;
  paymentMethod: string | null;
}
