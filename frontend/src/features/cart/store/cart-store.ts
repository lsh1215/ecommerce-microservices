import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { CartItem } from '@/types';

const CART_VERSION = 2;

interface CartStore {
  version: number;
  items: CartItem[];
  totalItems: number;
  addItem: (item: CartItem) => void;
  removeItem: (productId: string, variantId: string) => void;
  updateQuantity: (productId: string, variantId: string, quantity: number) => void;
  clearCart: () => void;
}

function recalculate(items: CartItem[]) {
  return {
    totalItems: items.reduce((sum, i) => sum + i.quantity, 0),
  };
}

export const useCartStore = create<CartStore>()(
  persist(
    (set) => ({
      version: CART_VERSION,
      items: [],
      totalItems: 0,

      addItem: (incoming) =>
        set((state) => {
          const existing = state.items.find(
            (i) => i.productId === incoming.productId && i.variantId === incoming.variantId,
          );
          const items = existing
            ? state.items.map((i) =>
                i.productId === incoming.productId && i.variantId === incoming.variantId
                  ? { ...i, quantity: i.quantity + incoming.quantity }
                  : i,
              )
            : [...state.items, incoming];
          return { items, ...recalculate(items) };
        }),

      removeItem: (productId, variantId) =>
        set((state) => {
          const items = state.items.filter(
            (i) => !(i.productId === productId && i.variantId === variantId),
          );
          return { items, ...recalculate(items) };
        }),

      updateQuantity: (productId, variantId, quantity) =>
        set((state) => {
          const items =
            quantity <= 0
              ? state.items.filter((i) => !(i.productId === productId && i.variantId === variantId))
              : state.items.map((i) =>
                  i.productId === productId && i.variantId === variantId ? { ...i, quantity } : i,
                );
          return { items, ...recalculate(items) };
        }),

      clearCart: () => set({ items: [], totalItems: 0 }),
    }),
    {
      name: 'cart-v2',
      storage: createJSONStorage(() => localStorage),
      onRehydrateStorage: () => (state) => {
        if (state && state.version !== CART_VERSION) {
          state.items = [];
          state.totalItems = 0;
          state.version = CART_VERSION;
        }
      },
    },
  ),
);
