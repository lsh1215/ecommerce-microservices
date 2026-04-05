import type { Product } from '@/types';

const brandA = { id: 'brand-1', name: 'Nordic Basic' };
const brandB = { id: 'brand-2', name: 'Urban Thread' };
const brandC = { id: 'brand-3', name: 'Coastal Wear' };

export const mockProducts: Product[] = [
  {
    id: 'prod-001',
    name: 'Classic Oxford Shirt',
    description: 'A timeless oxford shirt in a relaxed fit. 100% cotton.',
    price: 49000,
    category: 'tops',
    brand: brandA,
    images: [
      {
        id: 'img-001',
        url: 'https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=800&q=80',
        sortOrder: 0,
        isPrimary: true,
      },
    ],
    variants: [
      { id: 'v-001-s', size: 'S', color: 'White', sku: 'OXF-S-W', stockQuantity: 10 },
      { id: 'v-001-m', size: 'M', color: 'White', sku: 'OXF-M-W', stockQuantity: 15 },
      { id: 'v-001-l', size: 'L', color: 'White', sku: 'OXF-L-W', stockQuantity: 8 },
    ],
    status: 'ACTIVE',
    createdAt: '2025-01-01T00:00:00Z',
  },
  {
    id: 'prod-002',
    name: 'Slim Chino Trousers',
    description: 'Clean-cut slim chinos with a comfortable stretch fabric.',
    price: 69000,
    category: 'bottoms',
    brand: brandA,
    images: [
      {
        id: 'img-002',
        url: 'https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?w=800&q=80',
        sortOrder: 0,
        isPrimary: true,
      },
    ],
    variants: [
      { id: 'v-002-30', size: '30', color: 'Khaki', sku: 'CHN-30-K', stockQuantity: 6 },
      { id: 'v-002-32', size: '32', color: 'Khaki', sku: 'CHN-32-K', stockQuantity: 9 },
      { id: 'v-002-34', size: '34', color: 'Khaki', sku: 'CHN-34-K', stockQuantity: 4 },
    ],
    status: 'ACTIVE',
    createdAt: '2025-01-05T00:00:00Z',
  },
  {
    id: 'prod-003',
    name: 'Wool Blend Overcoat',
    description: 'A sophisticated overcoat in a warm wool-polyester blend.',
    price: 189000,
    category: 'outerwear',
    brand: brandB,
    images: [
      {
        id: 'img-003',
        url: 'https://images.unsplash.com/photo-1551028719-00167b16eac5?w=800&q=80',
        sortOrder: 0,
        isPrimary: true,
      },
    ],
    variants: [
      { id: 'v-003-m', size: 'M', color: 'Camel', sku: 'OC-M-C', stockQuantity: 5 },
      { id: 'v-003-l', size: 'L', color: 'Camel', sku: 'OC-L-C', stockQuantity: 3 },
      { id: 'v-003-xl', size: 'XL', color: 'Camel', sku: 'OC-XL-C', stockQuantity: 2 },
    ],
    status: 'ACTIVE',
    createdAt: '2025-01-10T00:00:00Z',
  },
  {
    id: 'prod-004',
    name: 'Essential Crewneck Tee',
    description: 'Heavyweight cotton crewneck tee. Pre-washed for softness.',
    price: 29000,
    category: 'tops',
    brand: brandC,
    images: [
      {
        id: 'img-004',
        url: 'https://images.unsplash.com/photo-1556821840-3a63f15732ce?w=800&q=80',
        sortOrder: 0,
        isPrimary: true,
      },
    ],
    variants: [
      { id: 'v-004-s', size: 'S', color: 'Black', sku: 'TEE-S-BK', stockQuantity: 20 },
      { id: 'v-004-m', size: 'M', color: 'Black', sku: 'TEE-M-BK', stockQuantity: 25 },
      { id: 'v-004-l', size: 'L', color: 'Black', sku: 'TEE-L-BK', stockQuantity: 18 },
    ],
    status: 'ACTIVE',
    createdAt: '2025-01-12T00:00:00Z',
  },
  {
    id: 'prod-005',
    name: 'Denim Jacket',
    description: 'A classic denim jacket with a slightly boxy fit.',
    price: 99000,
    category: 'outerwear',
    brand: brandB,
    images: [
      {
        id: 'img-005',
        url: 'https://images.unsplash.com/photo-1601333144130-8cbb312386b6?w=800&q=80',
        sortOrder: 0,
        isPrimary: true,
      },
    ],
    variants: [
      { id: 'v-005-s', size: 'S', color: 'Indigo', sku: 'DJ-S-I', stockQuantity: 7 },
      { id: 'v-005-m', size: 'M', color: 'Indigo', sku: 'DJ-M-I', stockQuantity: 10 },
      { id: 'v-005-l', size: 'L', color: 'Indigo', sku: 'DJ-L-I', stockQuantity: 5 },
    ],
    status: 'ACTIVE',
    createdAt: '2025-01-15T00:00:00Z',
  },
  {
    id: 'prod-006',
    name: 'Linen Blend Shorts',
    description: 'Breathable linen-cotton shorts for warm weather.',
    price: 39000,
    category: 'bottoms',
    brand: brandC,
    images: [
      {
        id: 'img-006',
        url: 'https://images.unsplash.com/photo-1542272604-787c3835535d?w=800&q=80',
        sortOrder: 0,
        isPrimary: true,
      },
    ],
    variants: [
      { id: 'v-006-s', size: 'S', color: 'Beige', sku: 'SHT-S-B', stockQuantity: 12 },
      { id: 'v-006-m', size: 'M', color: 'Beige', sku: 'SHT-M-B', stockQuantity: 14 },
      { id: 'v-006-l', size: 'L', color: 'Beige', sku: 'SHT-L-B', stockQuantity: 8 },
    ],
    status: 'ACTIVE',
    createdAt: '2025-01-18T00:00:00Z',
  },
  {
    id: 'prod-007',
    name: 'Canvas Sneakers',
    description: 'Low-profile canvas sneakers with a rubber sole.',
    price: 59000,
    category: 'shoes',
    brand: brandA,
    images: [
      {
        id: 'img-007',
        url: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=800&q=80',
        sortOrder: 0,
        isPrimary: true,
      },
    ],
    variants: [
      { id: 'v-007-260', size: '260', color: 'White', sku: 'SNK-260-W', stockQuantity: 4 },
      { id: 'v-007-265', size: '265', color: 'White', sku: 'SNK-265-W', stockQuantity: 6 },
      { id: 'v-007-270', size: '270', color: 'White', sku: 'SNK-270-W', stockQuantity: 3 },
    ],
    status: 'ACTIVE',
    createdAt: '2025-01-20T00:00:00Z',
  },
  {
    id: 'prod-008',
    name: 'Knit Beanie',
    description: 'A warm ribbed knit beanie. One size fits most.',
    price: 19000,
    category: 'accessories',
    brand: brandC,
    images: [
      {
        id: 'img-008',
        url: 'https://images.unsplash.com/photo-1512327536842-5aa37d1ba3e3?w=800&q=80',
        sortOrder: 0,
        isPrimary: true,
      },
    ],
    variants: [
      { id: 'v-008-os', size: 'OS', color: 'Navy', sku: 'BNE-OS-N', stockQuantity: 30 },
    ],
    status: 'ACTIVE',
    createdAt: '2025-01-22T00:00:00Z',
  },
];

export function getProductById(id: string): Product | undefined {
  return mockProducts.find((p) => p.id === id);
}

export function getFeaturedProducts(limit = 8): Product[] {
  return mockProducts.slice(0, limit);
}

export function getProductsByCategory(category: string): Product[] {
  return mockProducts.filter((p) => p.category === category);
}

export function getRelatedProducts(product: Product, limit = 4): Product[] {
  return mockProducts
    .filter(
      (p) =>
        p.id !== product.id &&
        (p.brand.id === product.brand.id || p.category === product.category),
    )
    .slice(0, limit);
}
