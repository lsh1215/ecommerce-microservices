export interface ProductResponse {
  id: number;
  publicId: string;
  slug: string;
  category: string;
  era: string;
  basePriceAmount: number;
  basePriceCurrency: string;
  priceUsd: number;
  priceKrw: number;
  priceJpy: number;
  fabricWeightOz: number;
  fabricType: string;
  fabricWeave: string;
  brandName: string;
  brandSlug: string;
  createdAt: string;
}

export interface ProductVariantResponse {
  id: number;
  publicId: string;
  sku: string;
  sizeLabel: string;
  colorName: string;
  colorHex: string;
  priceOverrideAmount: number | null;
  priceOverrideCurrency: string | null;
  measChestCm: number | null;
  measShoulderCm: number | null;
  measSleeveCm: number | null;
  measBodyLengthCm: number | null;
  measWaistCm: number | null;
  measInseamCm: number | null;
  measThighCm: number | null;
  measHemCm: number | null;
}

export interface ProductTranslationResponse {
  id: number;
  locale: string;
  name: string;
  description: string;
}

export interface ProductImageResponse {
  id: number;
  url: string;
  sortOrder: number;
  isPrimary: boolean;
}

export interface ProductDetailResponse extends ProductResponse {
  variants: ProductVariantResponse[];
  translations: ProductTranslationResponse[];
  images: ProductImageResponse[];
}

export interface BrandResponse {
  id: number;
  publicId: string;
  name: string;
  slug: string;
  countryOfOrigin: string;
  styleCategory: string;
  foundedYear: number | null;
  description: string;
  logoUrl: string | null;
  createdAt: string;
}

export interface DropEventResponse {
  publicId: string;
  title: string;
  description: string;
  status: string;
  startsAt: string;
  endsAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface DropProductResponse {
  publicId: string;
  productVariantId: number;
  allocatedQuantity: number;
  soldQuantity: number;
  dropPriceAmount: number;
  dropPriceCurrency: string;
  createdAt: string;
}

export interface InventoryResponse {
  id: number;
  productVariantId: number;
  quantityAvailable: number;
  quantityReserved: number;
  quantitySold: number;
  updatedAt: string;
}

export interface CustomerResponse {
  publicId: string;
  email: string;
  name: string;
}

export interface LoginResponse {
  id: number;
  publicId: string;
  name: string;
  email: string;
}

export interface OrderItemResponse {
  id: number;
  productVariantId: number;
  quantity: number;
  productName: string;
  brandName: string;
  unitPriceAmount: number;
  unitPriceCurrency: string;
  sizeLabel: string;
  sku: string;
  subtotal: number;
}

export interface OrderResponse {
  id: number;
  publicId: string;
  customerId: number;
  status: string;
  totalAmount: number;
  totalCurrency: string;
  shippingAddress: string;
  items: OrderItemResponse[];
  createdAt: string;
}

export interface PaymentResponse {
  publicId: string;
  orderId: number;
  amount: number;
  currency: string;
  status: string;
  paymentMethod: string;
  createdAt: string;
  updatedAt: string;
}

export interface ExchangeRateResponse {
  id: number;
  fromCurrency: string;
  toCurrency: string;
  rate: number;
  effectiveDate: string;
  createdAt: string;
}
