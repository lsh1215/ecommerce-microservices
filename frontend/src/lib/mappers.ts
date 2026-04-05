import type { Product, Brand, Order, OrderItem, Category, OrderStatus } from '@/types/domain';
import type {
  ProductResponse,
  BrandResponse,
  OrderResponse,
  OrderItemResponse,
} from '@/types/api-responses';

function toCategory(raw: string): Category {
  const valid: Category[] = ['tops', 'bottoms', 'outerwear', 'shoes', 'accessories', 'electronics', 'home'];
  const lower = raw.toLowerCase();
  return valid.includes(lower as Category) ? (lower as Category) : 'accessories';
}

function toOrderStatus(raw: string): OrderStatus {
  const valid: OrderStatus[] = ['PENDING', 'CONFIRMED', 'PAID', 'SHIPPING', 'DELIVERED', 'CANCELLED'];
  return valid.includes(raw as OrderStatus) ? (raw as OrderStatus) : 'PENDING';
}

export function mapBrandResponse(backend: BrandResponse): Brand {
  return {
    id: String(backend.id),
    name: backend.name,
    description: backend.description,
    logoUrl: backend.logoUrl ?? undefined,
  };
}

export function mapProductResponse(backend: ProductResponse): Product {
  return {
    id: String(backend.id),
    name: backend.name,
    description: backend.description,
    price: backend.price,
    category: toCategory(backend.category),
    brand: mapBrandResponse(backend.brand),
    images: (backend.images ?? []).map((img) => ({
      id: String(img.id),
      url: img.url,
      sortOrder: img.sortOrder,
      isPrimary: img.isPrimary,
    })),
    variants: (backend.variants ?? []).map((v) => ({
      id: String(v.id),
      size: v.size,
      color: v.color,
      sku: v.sku,
      stockQuantity: v.stockQuantity,
      price: v.price,
    })),
    status: backend.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE',
    createdAt: backend.createdAt,
  };
}

export function mapOrderItemResponse(backend: OrderItemResponse): OrderItem {
  const parts = backend.variantInfo?.split('/') ?? [];
  return {
    productId: String(backend.productId),
    variantId: String(backend.variantId),
    productName: backend.productName,
    brandName: backend.brandName,
    size: parts[0]?.trim() ?? '',
    color: parts[1]?.trim() ?? '',
    price: backend.unitPrice,
    imageUrl: '',
    quantity: backend.quantity,
    totalPrice: backend.totalPrice,
  };
}

export function mapOrderResponse(backend: OrderResponse): Order {
  return {
    id: String(backend.id),
    orderNumber: backend.orderNumber,
    status: toOrderStatus(backend.status),
    items: (backend.items ?? []).map(mapOrderItemResponse),
    shippingAddress: {
      recipientName: backend.shippingAddress.recipientName,
      phone: backend.shippingAddress.phone,
      zipCode: backend.shippingAddress.zipCode,
      address1: backend.shippingAddress.address1,
      address2: backend.shippingAddress.address2,
    },
    totalAmount: backend.totalAmount,
    createdAt: backend.createdAt,
    updatedAt: backend.updatedAt,
  };
}
