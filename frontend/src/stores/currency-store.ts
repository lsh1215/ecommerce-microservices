import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { Currency } from '@/types';

interface CurrencyStore {
  currency: Currency;
  setCurrency: (currency: Currency) => void;
}

export const useCurrencyStore = create<CurrencyStore>()(
  persist(
    (set) => ({
      currency: 'USD',
      setCurrency: (currency) => set({ currency }),
    }),
    {
      name: 'currency-preference',
      storage: createJSONStorage(() => localStorage),
    },
  ),
);
