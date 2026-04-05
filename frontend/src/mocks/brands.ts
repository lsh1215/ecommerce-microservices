import type { Brand } from '@/types';

export const mockBrands: Brand[] = [
  {
    id: 'brand-1',
    name: 'Nordic Basic',
    description: 'Minimalist essentials inspired by Scandinavian design.',
  },
  {
    id: 'brand-2',
    name: 'Urban Thread',
    description: 'Contemporary streetwear crafted for city living.',
  },
  {
    id: 'brand-3',
    name: 'Coastal Wear',
    description: 'Relaxed, resort-inspired clothing for everyday wear.',
  },
];

export function getBrandById(id: string): Brand | undefined {
  return mockBrands.find((b) => b.id === id);
}
