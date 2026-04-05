export interface CreateOrderItem {
  productVariantId: number;
  quantity: number;
}

export interface CreateOrderShippingAddress {
  recipientName: string;
  phone: string;
  zipCode: string;
  address1: string;
  address2?: string;
}

export interface CreateOrderRequest {
  customerId: number;
  shippingAddress: CreateOrderShippingAddress;
  items: CreateOrderItem[];
  memo?: string;
}

export interface OrderListParams {
  customerId: number;
  page?: number;
  size?: number;
}
