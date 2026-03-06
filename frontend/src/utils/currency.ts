import type { Currency } from '@/types';

const formatters: Record<Currency, Intl.NumberFormat> = {
  KRW: new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW', maximumFractionDigits: 0 }),
  USD: new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }),
  JPY: new Intl.NumberFormat('ja-JP', { style: 'currency', currency: 'JPY', maximumFractionDigits: 0 }),
};

export function formatPrice(amount: number, currency: Currency): string {
  return formatters[currency].format(amount);
}

export function getPrice(
  prices: { priceKrw: number; priceUsd: number; priceJpy: number },
  currency: Currency,
): number {
  if (currency === 'KRW') return prices.priceKrw;
  if (currency === 'JPY') return prices.priceJpy;
  return prices.priceUsd;
}
