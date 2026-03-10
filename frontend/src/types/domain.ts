export type Currency = 'KRW' | 'USD' | 'JPY';

export type Origin = 'Korea' | 'Japan' | 'USA';

export type Category = 'denim' | 'outerwear' | 'shirts' | 'knitwear' | 'pants' | 'accessories';

export type DropStatus = 'ANNOUNCED' | 'OPEN' | 'SELLING' | 'SOLD_OUT' | 'CLOSED';

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

export interface Brand {
  id: string;
  slug: string;
  name: string;
  nameKo?: string;
  nameJa?: string;
  origin: Origin;
  description: string;
  fullDescription?: string;
  imageUrl: string;
  logoUrl?: string;
  featured: boolean;
  foundedYear?: number;
  styleCategory?: string;
}

export interface SizeStock {
  size: string;
  stock: number;
}

export interface Measurements {
  chest?: number;
  shoulder?: number;
  sleeve?: number;
  length?: number;
  waist?: number;
  inseam?: number;
  thigh?: number;
  hem?: number;
}

export interface ProductSize {
  id?: number;
  label: string;
  stock: number;
  measurements?: Measurements;
}

export interface Product {
  id: string;
  slug: string;
  name: string;
  nameKo?: string;
  nameJa?: string;
  description: string;
  brand: Brand;
  category: Category;
  origin: Origin;
  imageUrls: string[];
  priceKrw: number;
  priceUsd: number;
  priceJpy: number;
  fabric: {
    type: string;
    weightOz: number;
    weave: string;
  };
  era: string;
  sizes: ProductSize[];
  dropId?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Drop {
  id: string;
  slug: string;
  name: string;
  nameKo?: string;
  nameJa?: string;
  description: string;
  brand: Brand;
  status: DropStatus;
  heroImageUrl: string;
  opensAt: string;
  closesAt: string;
  productIds: string[];
  returnPolicy?: string;
  shippingTimeline?: string;
}

export interface DropSummary {
  id: string;
  status: DropStatus;
  opensAt: string;
  closesAt: string;
  stockByProduct: Record<string, Record<string, number>>;
  totalItemsRemaining: number;
}

export interface CartItem {
  productId: string;
  variantId?: number;
  productName: string;
  brandName: string;
  size: string;
  priceKrw: number;
  priceUsd: number;
  priceJpy: number;
  imageUrl: string;
  quantity: number;
  dropId?: string;
  dropName?: string;
  stockAvailable?: number;
}

export interface CartValidationResult {
  valid: boolean;
  issues: Array<{
    productId: string;
    size: string;
    requestedQty: number;
    availableQty: number;
    message: string;
  }>;
}

export interface ShippingAddress {
  name: string;
  phone: string;
  address: string;
  addressDetail?: string;
  city: string;
  country: string;
  postalCode: string;
}

export interface OrderItem {
  productId: string;
  productName: string;
  brandName: string;
  size: string;
  priceKrw: number;
  priceUsd: number;
  priceJpy: number;
  imageUrl: string;
  quantity: number;
}

export interface Order {
  id: string;
  status: OrderStatus;
  items: OrderItem[];
  shippingAddress: ShippingAddress;
  subtotalKrw: number;
  subtotalUsd: number;
  subtotalJpy: number;
  dutyKrw: number;
  dutyUsd: number;
  dutyJpy: number;
  totalKrw: number;
  totalUsd: number;
  totalJpy: number;
  dropId?: string;
  dropName?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProductFilters {
  q?: string;
  brand?: string;
  origin?: Origin;
  category?: Category;
  fabricWeightMin?: number;
  fabricWeightMax?: number;
  era?: string;
  priceMin?: number;
  priceMax?: number;
  dropStatus?: DropStatus;
  sort?: 'price_asc' | 'price_desc' | 'newest' | 'brand_az';
  page?: number;
  size?: number;
}
