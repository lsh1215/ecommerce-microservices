export interface CreateOrderItem {
  productVariantId: number;
  quantity: number;
}

export interface CreateOrderRequest {
  customerId: number;
  shippingAddress: string;
  idempotencyKey: string;
  currency: string;
  items: CreateOrderItem[];
}

export interface OrderListParams {
  customerId: number;
  page?: number;
  size?: number;
}
