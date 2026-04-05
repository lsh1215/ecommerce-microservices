export { useCartStore } from '@/features/cart/store/cart-store';

import { useCartStore } from '@/features/cart/store/cart-store';

export function useCartCount(): number {
  return useCartStore((s) => s.totalItems);
}
