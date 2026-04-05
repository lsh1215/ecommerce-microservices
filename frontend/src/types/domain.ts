export type Category =
  | 'tops'
  | 'bottoms'
  | 'outerwear'
  | 'shoes'
  | 'accessories'
  | 'electronics'
  | 'home';

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'PAID' | 'SHIPPING' | 'DELIVERED' | 'CANCELLED';

export type PaymentMethod = 'CARD' | 'BANK_TRANSFER' | 'VIRTUAL_ACCOUNT';

export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'REFUNDED';

export interface Brand {
  id: string;
  name: string;
  description?: string;
  logoUrl?: string;
}

export interface ProductVariant {
  id: string;
  size: string;
  color: string;
  sku: string;
  stockQuantity: number;
  price?: number;
}

export interface ProductImage {
  id: string;
  url: string;
  sortOrder: number;
  isPrimary: boolean;
}

export interface Product {
  id: string;
  name: string;
  description: string;
  price: number;
  category: Category;
  brand: Brand;
  images: ProductImage[];
  variants: ProductVariant[];
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: string;
}

export interface CartItem {
  productId: string;
  variantId: string;
  productName: string;
  brandName: string;
  size: string;
  color: string;
  price: number;
  imageUrl: string;
  quantity: number;
  stockAvailable?: number;
}

export interface ShippingAddress {
  recipientName: string;
  phone: string;
  zipCode: string;
  address1: string;
  address2?: string;
}

export interface OrderItem {
  productId: string;
  variantId: string;
  productName: string;
  brandName: string;
  size: string;
  color: string;
  price: number;
  imageUrl: string;
  quantity: number;
  totalPrice: number;
}

export interface Order {
  id: string;
  orderNumber: string;
  status: OrderStatus;
  items: OrderItem[];
  shippingAddress: ShippingAddress;
  totalAmount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProductFilters {
  q?: string;
  brandId?: string;
  category?: Category;
  minPrice?: number;
  maxPrice?: number;
  sort?: 'price_asc' | 'price_desc' | 'newest' | 'name_az';
  page?: number;
  size?: number;
}
