import type {
  Product,
  Brand,
  Drop,
  Order,
  OrderItem,
  ProductSize,
  Measurements,
  Category,
  Origin,
  DropStatus,
  OrderStatus,
} from '@/types/domain';
import type {
  ProductResponse,
  ProductDetailResponse,
  ProductVariantResponse,
  BrandResponse,
  DropEventResponse,
  OrderResponse,
  InventoryResponse,
} from '@/types/api-responses';

const COUNTRY_TO_ORIGIN: Record<string, Origin> = {
  JP: 'Japan',
  US: 'USA',
  KR: 'Korea',
};

function toOrigin(countryCode: string): Origin {
  return COUNTRY_TO_ORIGIN[countryCode] ?? 'USA';
}

function toCategory(raw: string): Category {
  const lower = raw.toLowerCase();
  const valid: Category[] = ['denim', 'outerwear', 'shirts', 'knitwear', 'pants', 'accessories'];
  return valid.includes(lower as Category) ? (lower as Category) : 'accessories';
}

function toDropStatus(raw: string): DropStatus {
  const valid: DropStatus[] = ['ANNOUNCED', 'OPEN', 'SELLING', 'SOLD_OUT', 'CLOSED'];
  return valid.includes(raw as DropStatus) ? (raw as DropStatus) : 'ANNOUNCED';
}

function toOrderStatus(raw: string): OrderStatus {
  const valid: OrderStatus[] = ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'];
  return valid.includes(raw as OrderStatus) ? (raw as OrderStatus) : 'PENDING';
}

function buildMeasurements(variant: ProductVariantResponse): Measurements | undefined {
  const m: Measurements = {};
  if (variant.measChestCm != null) m.chest = variant.measChestCm;
  if (variant.measShoulderCm != null) m.shoulder = variant.measShoulderCm;
  if (variant.measSleeveCm != null) m.sleeve = variant.measSleeveCm;
  if (variant.measBodyLengthCm != null) m.length = variant.measBodyLengthCm;
  if (variant.measWaistCm != null) m.waist = variant.measWaistCm;
  if (variant.measInseamCm != null) m.inseam = variant.measInseamCm;
  if (variant.measThighCm != null) m.thigh = variant.measThighCm;
  if (variant.measHemCm != null) m.hem = variant.measHemCm;

  return Object.keys(m).length > 0 ? m : undefined;
}

function buildPartialBrand(name: string, slug: string): Brand {
  return {
    id: slug,
    slug,
    name,
    origin: 'USA',
    description: '',
    imageUrl: '',
    featured: false,
  };
}

export function mapProductResponse(backend: ProductResponse): Product {
  return {
    id: backend.publicId,
    slug: backend.slug,
    name: backend.brandName + ' ' + backend.slug,
    description: '',
    brand: buildPartialBrand(backend.brandName, backend.brandSlug),
    category: toCategory(backend.category),
    origin: 'USA',
    imageUrls: [],
    priceKrw: backend.priceKrw,
    priceUsd: backend.priceUsd,
    priceJpy: backend.priceJpy,
    fabric: {
      type: backend.fabricType ?? '',
      weightOz: backend.fabricWeightOz ?? 0,
      weave: backend.fabricWeave ?? '',
    },
    era: backend.era ?? '',
    sizes: [],
    createdAt: backend.createdAt,
    updatedAt: backend.createdAt,
  };
}

export function mapProductDetailResponse(
  backend: ProductDetailResponse,
  inventories: InventoryResponse[] = [],
): Product {
  const inventoryMap = new Map<number, number>();
  for (const inv of inventories) {
    inventoryMap.set(inv.productVariantId, inv.quantityAvailable);
  }

  const enTranslation = backend.translations?.find((t) => t.locale === 'en');
  const koTranslation = backend.translations?.find((t) => t.locale === 'ko');
  const jaTranslation = backend.translations?.find((t) => t.locale === 'ja');

  const sortedImages = [...(backend.images ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);
  const imageUrls = sortedImages.map((img) => img.url);

  const sizes: ProductSize[] = (backend.variants ?? []).map((v) => ({
    label: v.sizeLabel,
    stock: inventoryMap.get(v.id) ?? 0,
    measurements: buildMeasurements(v),
  }));

  return {
    id: backend.publicId,
    slug: backend.slug,
    name: enTranslation?.name ?? backend.brandName + ' ' + backend.slug,
    nameKo: koTranslation?.name,
    nameJa: jaTranslation?.name,
    description: enTranslation?.description ?? '',
    brand: buildPartialBrand(backend.brandName, backend.brandSlug),
    category: toCategory(backend.category),
    origin: 'USA',
    imageUrls,
    priceKrw: backend.priceKrw,
    priceUsd: backend.priceUsd,
    priceJpy: backend.priceJpy,
    fabric: {
      type: backend.fabricType ?? '',
      weightOz: backend.fabricWeightOz ?? 0,
      weave: backend.fabricWeave ?? '',
    },
    era: backend.era ?? '',
    sizes,
    createdAt: backend.createdAt,
    updatedAt: backend.createdAt,
  };
}

export function mapBrandResponse(backend: BrandResponse): Brand {
  return {
    id: backend.publicId,
    slug: backend.slug,
    name: backend.name,
    origin: toOrigin(backend.countryOfOrigin),
    description: backend.description ?? '',
    imageUrl: backend.logoUrl ?? '',
    logoUrl: backend.logoUrl ?? undefined,
    featured: false,
    foundedYear: backend.foundedYear ?? undefined,
    styleCategory: backend.styleCategory ?? undefined,
  };
}

export function mapDropResponse(backend: DropEventResponse): Drop {
  return {
    id: backend.publicId,
    slug: backend.publicId,
    name: backend.title,
    description: backend.description ?? '',
    brand: buildPartialBrand('', ''),
    status: toDropStatus(backend.status),
    heroImageUrl: '',
    opensAt: backend.startsAt,
    closesAt: backend.endsAt,
    productIds: [],
  };
}

export function mapOrderResponse(backend: OrderResponse): Order {
  const items: OrderItem[] = backend.items.map((item) => ({
    productId: String(item.productVariantId),
    productName: item.productName,
    brandName: item.brandName,
    size: item.sizeLabel,
    priceKrw: item.unitPriceAmount,
    priceUsd: item.unitPriceAmount,
    priceJpy: item.unitPriceAmount,
    imageUrl: '',
    quantity: item.quantity,
  }));

  return {
    id: backend.publicId,
    status: toOrderStatus(backend.status),
    items,
    shippingAddress: {
      name: '',
      phone: '',
      address: backend.shippingAddress,
      city: '',
      country: '',
      postalCode: '',
    },
    subtotalKrw: backend.totalAmount,
    subtotalUsd: backend.totalAmount,
    subtotalJpy: backend.totalAmount,
    dutyKrw: 0,
    dutyUsd: 0,
    dutyJpy: 0,
    totalKrw: backend.totalAmount,
    totalUsd: backend.totalAmount,
    totalJpy: backend.totalAmount,
    createdAt: backend.createdAt,
    updatedAt: backend.createdAt,
  };
}
