export interface BrandResponse {
  id: number;
  name: string;
  description?: string;
  logoUrl?: string;
  country?: string;
  createdAt?: string;
}

export interface ProductVariantResponse {
  id: number;
  size: string;
  color: string;
  sku: string;
  stockQuantity: number;
  price?: number;
}

export interface ProductImageResponse {
  id: number;
  url: string;
  sortOrder: number;
  isPrimary: boolean;
}

export interface ProductResponse {
  id: number;
  name: string;
  description: string;
  price: number;
  category: string;
  status: string;
  brand: BrandResponse;
  images: ProductImageResponse[];
  variants: ProductVariantResponse[];
  createdAt: string;
}

export interface CustomerResponse {
  id: number;
  email: string;
  name: string;
  phone?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface LoginResponse {
  id: number;
  name: string;
  email: string;
}

export type AddressLabel = 'HOME' | 'WORK' | 'OTHER';

export interface AddressResponse {
  id: number;
  label: AddressLabel;
  recipientName: string;
  phone: string;
  zipCode: string;
  address1: string;
  address2?: string;
  isDefault: boolean;
}

export interface OrderItemResponse {
  id: number;
  productId: number;
  productVariantId: number;
  productName: string;
  variantInfo: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
}

export interface ShippingAddressResponse {
  recipientName: string;
  phone: string;
  zipCode: string;
  address1: string;
  address2?: string;
}

export interface OrderResponse {
  id: number;
  orderNumber: string;
  customerId: number;
  status: string;
  totalAmount: number;
  shippingAddress: ShippingAddressResponse;
  memo?: string;
  items: OrderItemResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface PaymentResponse {
  id: number;
  orderId: number;
  orderNumber?: string;
  amount: number;
  status: string;
  paymentMethod: string;
  transactionId?: string;
  failureReason?: string;
  paidAt?: string;
  refundedAt?: string;
  createdAt: string;
  updatedAt: string;
}
