import type { Drop } from '@/types';
import { mockBrands } from './brands';

const outstanding = mockBrands[0]!;
const warehouse = mockBrands[1]!;
const rrl = mockBrands[2]!;

const now = new Date();

const inTwoDays = new Date(now.getTime() + 2 * 24 * 60 * 60 * 1000).toISOString();
const inFiveDays = new Date(now.getTime() + 5 * 24 * 60 * 60 * 1000).toISOString();
const inTwoHours = new Date(now.getTime() + 2 * 60 * 60 * 1000).toISOString();
const twoWeeksAgo = new Date(now.getTime() - 14 * 24 * 60 * 60 * 1000).toISOString();
const oneMonthAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000).toISOString();
const oneWeekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000).toISOString();

export const mockDrops: Drop[] = [
  {
    id: 'drop-fw2024-outstanding',
    slug: 'outstanding-fw2024',
    name: 'Outstanding FW2024 — Harvest Season',
    nameKo: '아웃스탠딩 24FW — 수확의 계절',
    description:
      'Outstanding & Co. presents their Fall/Winter 2024 collection. Eight pieces inspired by Korean harvest season traditions filtered through vintage American workwear.',
    brand: outstanding,
    status: 'SELLING',
    heroImageUrl: 'https://images.unsplash.com/photo-1601333144130-8cbb312386b6?w=1600&q=80',
    opensAt: oneWeekAgo,
    closesAt: inTwoHours,
    productIds: ['prod-001', 'prod-002', 'prod-007'],
    returnPolicy: 'Final sale. No returns on drop items.',
    shippingTimeline: 'Ships within 5 business days of drop close.',
  },
  {
    id: 'drop-fw2024-warehouse',
    slug: 'warehouse-fw2024',
    name: 'Warehouse FW2024 — Banner Denim Annual',
    nameJa: 'ウエアハウス 24FW — バナーデニム年次',
    description:
      'Annual flagship drop from Warehouse & Co. featuring their Banner Denim selvedge in deadstock indigo. Limited to production run only.',
    brand: warehouse,
    status: 'ANNOUNCED',
    heroImageUrl: 'https://images.unsplash.com/photo-1542272604-787c3835535d?w=1600&q=80',
    opensAt: inTwoDays,
    closesAt: inFiveDays,
    productIds: ['prod-003', 'prod-004', 'prod-008'],
    returnPolicy: 'Final sale. No returns.',
    shippingTimeline: 'Ships within 7-10 business days.',
  },
  {
    id: 'drop-rrl-limited',
    slug: 'rrl-indigo-limited',
    name: 'RRL — Indigo Archive Limited',
    description:
      "A tight edit of hand-dyed archival pieces from RRL's design archive. 150 units worldwide. No restock.",
    brand: rrl,
    status: 'SELLING',
    heroImageUrl: 'https://images.unsplash.com/photo-1604644401890-0bd678c83788?w=1600&q=80',
    opensAt: oneWeekAgo,
    closesAt: inTwoDays,
    productIds: ['prod-005', 'prod-006'],
    returnPolicy: 'No returns on limited edition drops.',
    shippingTimeline: 'Ships within 3 business days.',
  },
  {
    id: 'drop-ss2024-outstanding',
    slug: 'outstanding-ss2024',
    name: 'Outstanding SS2024 — Monsoon Edit',
    nameKo: '아웃스탠딩 24SS — 장마 에디션',
    description:
      'Spring/Summer 2024 collection. Lightweight chambray and poplin pieces for humid Korean summers.',
    brand: outstanding,
    status: 'CLOSED',
    heroImageUrl: 'https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=1600&q=80',
    opensAt: twoWeeksAgo,
    closesAt: oneWeekAgo,
    productIds: ['prod-002'],
  },
  {
    id: 'drop-aw2023-warehouse',
    slug: 'warehouse-aw2023',
    name: 'Warehouse AW2023 — Indigo Deep',
    nameJa: 'ウエアハウス 23AW — インディゴディープ',
    description: 'Autumn/Winter 2023 annual denim drop. Sold out in 12 minutes.',
    brand: warehouse,
    status: 'SOLD_OUT',
    heroImageUrl: 'https://images.unsplash.com/photo-1555689502-c4b22d76c56f?w=1600&q=80',
    opensAt: oneMonthAgo,
    closesAt: twoWeeksAgo,
    productIds: ['prod-003', 'prod-008'],
  },
];

export function getDropById(id: string): Drop | undefined {
  return mockDrops.find((d) => d.id === id || d.slug === id);
}

export function getDropsByStatus(statuses: Drop['status'][]): Drop[] {
  return mockDrops.filter((d) => statuses.includes(d.status));
}

export function getLiveDrops(): Drop[] {
  return getDropsByStatus(['SELLING', 'OPEN']);
}

export function getUpcomingDrops(): Drop[] {
  return getDropsByStatus(['ANNOUNCED']);
}

export function getPastDrops(): Drop[] {
  return getDropsByStatus(['CLOSED', 'SOLD_OUT']);
}
