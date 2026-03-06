'use client';

import { useCurrencyStore } from '@/stores/currency-store';
import { formatPrice, getPrice } from '@/utils/currency';

interface CurrencyPriceProps {
  priceKrw: number;
  priceUsd: number;
  priceJpy: number;
  className?: string;
}

export function CurrencyPrice({
  priceKrw,
  priceUsd,
  priceJpy,
  className = '',
}: CurrencyPriceProps) {
  const currency = useCurrencyStore((s) => s.currency);
  const amount = getPrice({ priceKrw, priceUsd, priceJpy }, currency);

  return <span className={className}>{formatPrice(amount, currency)}</span>;
}
