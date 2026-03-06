import type { Product } from '@/types';
import { mockBrands } from './brands';

const outstanding = mockBrands[0]!;
const warehouse = mockBrands[1]!;
const rrl = mockBrands[2]!;

export const mockProducts: Product[] = [
  {
    id: 'prod-001',
    slug: 'outstanding-selvedge-denim-type-2',
    name: 'Selvedge Denim Type II Jacket',
    nameKo: '셀비지 데님 타입 II 재킷',
    description:
      '14oz selvedge right-hand twill denim jacket cut in the classic Type II silhouette. Sanforized for minimal shrinkage.',
    brand: outstanding,
    category: 'outerwear',
    origin: 'Korea',
    imageUrls: [
      'https://images.unsplash.com/photo-1601333144130-8cbb312386b6?w=800&q=80',
      'https://images.unsplash.com/photo-1578932750294-f5075e85f44a?w=800&q=80',
      'https://images.unsplash.com/photo-1591047139829-d91aecb6caea?w=800&q=80',
    ],
    priceKrw: 220000,
    priceUsd: 168,
    priceJpy: 25200,
    fabric: { type: 'Selvedge denim', weightOz: 14, weave: 'Right-hand twill' },
    era: '1950s workwear',
    sizes: [
      { label: '1', stock: 3, measurements: { chest: 52, shoulder: 43, sleeve: 60, length: 62 } },
      { label: '2', stock: 5, measurements: { chest: 54, shoulder: 44, sleeve: 61, length: 63 } },
      { label: '3', stock: 2, measurements: { chest: 56, shoulder: 45, sleeve: 62, length: 64 } },
      { label: '4', stock: 0, measurements: { chest: 58, shoulder: 46, sleeve: 63, length: 65 } },
    ],
    dropId: 'drop-fw2024-outstanding',
    createdAt: '2024-09-01T00:00:00Z',
    updatedAt: '2024-09-01T00:00:00Z',
  },
  {
    id: 'prod-002',
    slug: 'outstanding-reverse-weave-sweatshirt',
    name: 'Reverse Weave Sweatshirt',
    nameKo: '리버스위브 스웻셔츠',
    description:
      'Loopwheeled terry cotton sweatshirt with vintage print graphics. Cut in a slightly boxy silhouette.',
    brand: outstanding,
    category: 'knitwear',
    origin: 'Korea',
    imageUrls: [
      'https://images.unsplash.com/photo-1556821840-3a63f15732ce?w=800&q=80',
      'https://images.unsplash.com/photo-1578587018452-892bacefd3f2?w=800&q=80',
    ],
    priceKrw: 98000,
    priceUsd: 75,
    priceJpy: 11200,
    fabric: { type: 'Loopwheeled terry cotton', weightOz: 12, weave: 'French terry' },
    era: '1960s outdoor',
    sizes: [
      { label: 'S', stock: 8 },
      { label: 'M', stock: 12 },
      { label: 'L', stock: 6 },
      { label: 'XL', stock: 4 },
    ],
    createdAt: '2024-08-15T00:00:00Z',
    updatedAt: '2024-08-15T00:00:00Z',
  },
  {
    id: 'prod-003',
    slug: 'warehouse-1000xx-selvedge-jeans',
    name: 'Lot 1000XX Selvedge Jeans',
    nameJa: 'ロット1000XXセルヴィッジジーンズ',
    description:
      'Warehouse\'s flagship denim. 13.5oz Banner Denim selvedge, irregular slub texture reminiscent of vintage shuttle looms.',
    brand: warehouse,
    category: 'denim',
    origin: 'Japan',
    imageUrls: [
      'https://images.unsplash.com/photo-1542272604-787c3835535d?w=800&q=80',
      'https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?w=800&q=80',
    ],
    priceKrw: 320000,
    priceUsd: 245,
    priceJpy: 36700,
    fabric: { type: 'Banner Denim selvedge', weightOz: 13.5, weave: 'Left-hand twill' },
    era: '1950s workwear',
    sizes: [
      { label: '28', stock: 3, measurements: { waist: 74, inseam: 83, thigh: 31, hem: 18 } },
      { label: '30', stock: 5, measurements: { waist: 78, inseam: 83, thigh: 32, hem: 18 } },
      { label: '32', stock: 4, measurements: { waist: 82, inseam: 83, thigh: 33, hem: 19 } },
      { label: '34', stock: 2, measurements: { waist: 86, inseam: 83, thigh: 34, hem: 19 } },
      { label: '36', stock: 1, measurements: { waist: 90, inseam: 83, thigh: 35, hem: 20 } },
    ],
    createdAt: '2024-07-01T00:00:00Z',
    updatedAt: '2024-07-01T00:00:00Z',
  },
  {
    id: 'prod-004',
    slug: 'warehouse-chambray-work-shirt',
    name: 'Chambray Work Shirt',
    nameJa: 'シャンブレーワークシャツ',
    description:
      'Classic chambray work shirt cut from 5oz indigo-dyed cotton. Single needle construction throughout.',
    brand: warehouse,
    category: 'shirts',
    origin: 'Japan',
    imageUrls: [
      'https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=800&q=80',
    ],
    priceKrw: 195000,
    priceUsd: 149,
    priceJpy: 22300,
    fabric: { type: 'Chambray', weightOz: 5, weave: 'Plain' },
    era: '1950s workwear',
    sizes: [
      { label: '14', stock: 6 },
      { label: '14.5', stock: 8 },
      { label: '15', stock: 5 },
      { label: '15.5', stock: 3 },
      { label: '16', stock: 2 },
    ],
    createdAt: '2024-08-01T00:00:00Z',
    updatedAt: '2024-08-01T00:00:00Z',
  },
  {
    id: 'prod-005',
    slug: 'rrl-limited-indigo-field-jacket',
    name: 'Indigo-Dyed Field Jacket',
    description:
      'Hand-dyed indigo cotton canvas field jacket. Small batch of 150 pieces worldwide. Brass hardware.',
    brand: rrl,
    category: 'outerwear',
    origin: 'USA',
    imageUrls: [
      'https://images.unsplash.com/photo-1591047139829-d91aecb6caea?w=800&q=80',
      'https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf?w=800&q=80',
    ],
    priceKrw: 780000,
    priceUsd: 595,
    priceJpy: 89200,
    fabric: { type: 'Cotton canvas', weightOz: 10, weave: 'Plain weave' },
    era: '1940s military',
    sizes: [
      { label: 'S', stock: 5 },
      { label: 'M', stock: 8 },
      { label: 'L', stock: 6 },
      { label: 'XL', stock: 3 },
    ],
    dropId: 'drop-rrl-limited',
    createdAt: '2024-09-10T00:00:00Z',
    updatedAt: '2024-09-10T00:00:00Z',
  },
  {
    id: 'prod-006',
    slug: 'rrl-roughout-chinos',
    name: 'Roughout Chino Trousers',
    description:
      'Slim-cut chinos in 10oz roughout cotton. Inspired by 1950s Ivy League campus wear.',
    brand: rrl,
    category: 'pants',
    origin: 'USA',
    imageUrls: [
      'https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?w=800&q=80',
    ],
    priceKrw: 345000,
    priceUsd: 264,
    priceJpy: 39600,
    fabric: { type: 'Roughout cotton', weightOz: 10, weave: 'Twill' },
    era: '1950s workwear',
    sizes: [
      { label: '30x32', stock: 4 },
      { label: '32x32', stock: 6 },
      { label: '34x32', stock: 5 },
      { label: '36x32', stock: 2 },
    ],
    createdAt: '2024-06-01T00:00:00Z',
    updatedAt: '2024-06-01T00:00:00Z',
  },
  {
    id: 'prod-007',
    slug: 'outstanding-military-anorak',
    name: 'Mountain Anorak',
    nameKo: '마운틴 아노락',
    description:
      'Pullover anorak cut from 8oz waxed canvas. Korean outdoor interpretation of WWII military parkas.',
    brand: outstanding,
    category: 'outerwear',
    origin: 'Korea',
    imageUrls: [
      'https://images.unsplash.com/photo-1551028719-00167b16eac5?w=800&q=80',
    ],
    priceKrw: 165000,
    priceUsd: 126,
    priceJpy: 18900,
    fabric: { type: 'Waxed canvas', weightOz: 8, weave: 'Plain weave' },
    era: '1940s military',
    sizes: [
      { label: '1', stock: 4 },
      { label: '2', stock: 7 },
      { label: '3', stock: 5 },
      { label: '4', stock: 2 },
    ],
    createdAt: '2024-09-05T00:00:00Z',
    updatedAt: '2024-09-05T00:00:00Z',
  },
  {
    id: 'prod-008',
    slug: 'warehouse-lot-800xx-wide-jeans',
    name: 'Lot 800XX Wide Jeans',
    nameJa: 'ロット800XXワイドジーンズ',
    description:
      '1947 cut reproduction denim in 13.5oz pre-shrunk selvedge. Full-cut leg, high rise.',
    brand: warehouse,
    category: 'denim',
    origin: 'Japan',
    imageUrls: [
      'https://images.unsplash.com/photo-1555689502-c4b22d76c56f?w=800&q=80',
    ],
    priceKrw: 298000,
    priceUsd: 228,
    priceJpy: 34100,
    fabric: { type: 'Selvedge denim', weightOz: 13.5, weave: 'Right-hand twill' },
    era: '1940s military',
    sizes: [
      { label: '28', stock: 2 },
      { label: '30', stock: 5 },
      { label: '32', stock: 6 },
      { label: '34', stock: 3 },
    ],
    dropId: 'drop-fw2024-warehouse',
    createdAt: '2024-09-08T00:00:00Z',
    updatedAt: '2024-09-08T00:00:00Z',
  },
];

export function getProductById(id: string): Product | undefined {
  return mockProducts.find((p) => p.id === id || p.slug === id);
}

export function getProductsByBrand(brandSlug: string): Product[] {
  return mockProducts.filter((p) => p.brand.slug === brandSlug);
}

export function getProductsByDrop(dropId: string): Product[] {
  return mockProducts.filter((p) => p.dropId === dropId);
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
