'use client';

import Link from 'next/link';
import { ShoppingBag, Search, User, ChevronDown } from 'lucide-react';
import { useCartStore } from '@/features/cart/store/cart-store';
import { useCurrencyStore } from '@/stores/currency-store';
import type { Currency } from '@/types';

const CURRENCIES: Currency[] = ['USD', 'KRW', 'JPY'];

const NAV_LINKS = [
  { href: '/drops', label: 'Drops' },
  { href: '/brands', label: 'Brands' },
  { href: '/products', label: 'Products' },
];

export function Header() {
  const totalItems = useCartStore((s) => s.totalItems);
  const { currency, setCurrency } = useCurrencyStore();

  return (
    <header className="sticky top-0 z-50 border-b border-[#e8e4df] bg-[#faf9f6]/95 backdrop-blur-sm">
      <div className="mx-auto flex h-14 max-w-7xl items-center justify-between px-4 md:px-6">
        {/* Left — Wordmark */}
        <Link href="/" className="font-heading text-xl font-bold tracking-tight text-[#1a1a1a]">
          FOUNDRY
        </Link>

        {/* Center — Nav */}
        <nav className="hidden items-center gap-8 md:flex" aria-label="Main navigation">
          {NAV_LINKS.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="text-sm font-medium text-[#1a1a1a] transition-colors hover:text-[#c4633e]"
            >
              {link.label}
            </Link>
          ))}
        </nav>

        {/* Right — Actions */}
        <div className="flex items-center gap-1">
          {/* Search */}
          <Link
            href="/products"
            className="flex h-10 w-10 items-center justify-center rounded-none text-[#1a1a1a] transition-colors hover:text-[#c4633e]"
            aria-label="Search products"
          >
            <Search size={18} strokeWidth={1.5} />
          </Link>

          {/* Currency selector */}
          <div className="relative hidden md:block">
            <select
              value={currency}
              onChange={(e) => setCurrency(e.target.value as Currency)}
              className="h-10 cursor-pointer appearance-none bg-transparent pl-2 pr-6 text-sm font-medium text-[#1a1a1a] focus:outline-none"
              aria-label="Select currency"
            >
              {CURRENCIES.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
            <ChevronDown
              size={12}
              className="pointer-events-none absolute right-1 top-1/2 -translate-y-1/2 text-[#6b6560]"
            />
          </div>

          {/* Auth */}
          <Link
            href="/auth"
            className="flex h-10 w-10 items-center justify-center rounded-none text-[#1a1a1a] transition-colors hover:text-[#c4633e]"
            aria-label="Account"
          >
            <User size={18} strokeWidth={1.5} />
          </Link>

          {/* Cart */}
          <Link
            href="/cart"
            className="relative flex h-10 w-10 items-center justify-center rounded-none text-[#1a1a1a] transition-colors hover:text-[#c4633e]"
            aria-label={`Cart, ${totalItems} items`}
          >
            <ShoppingBag size={18} strokeWidth={1.5} />
            {totalItems > 0 && (
              <span className="absolute right-1.5 top-1.5 flex h-4 w-4 items-center justify-center bg-[#c4633e] text-[10px] font-bold text-white">
                {totalItems > 99 ? '99+' : totalItems}
              </span>
            )}
          </Link>
        </div>
      </div>
    </header>
  );
}
