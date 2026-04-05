'use client';

import { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { ShoppingBag, Search, User, Menu, X } from 'lucide-react';
import { useCartStore } from '@/features/cart/store/cart-store';
import { useFromStore } from '@/hooks/use-from-store';
import { useAuthStore } from '@/features/auth/store/auth-store';
import { cn } from '@/lib/utils';

const NAV_LINKS = [
  { href: '/', label: 'Home' },
  { href: '/products', label: 'Products' },
];

export function Header() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const pathname = usePathname();
  const totalItems = useCartStore((s) => s.totalItems);
  const user = useFromStore(useAuthStore, (s) => s.user);

  return (
    <header className="sticky top-0 z-50 border-b border-border bg-background/95 backdrop-blur-sm">
      <div className="mx-auto flex h-14 max-w-7xl items-center justify-between px-4 md:px-6">
        <Link href="/" className="text-xl font-bold tracking-tight text-foreground">
          Shop
        </Link>

        <nav className="hidden items-center gap-8 md:flex" aria-label="Main navigation">
          {NAV_LINKS.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className={cn(
                'text-sm font-medium transition-colors hover:text-primary',
                pathname === link.href ? 'text-primary' : 'text-foreground',
              )}
            >
              {link.label}
            </Link>
          ))}
        </nav>

        <div className="flex items-center gap-1">
          <Link
            href="/products"
            className="flex h-10 w-10 items-center justify-center text-foreground transition-colors hover:text-primary"
            aria-label="Search products"
          >
            <Search size={18} strokeWidth={1.5} />
          </Link>

          <Link
            href={user ? '/account/profile' : '/auth'}
            className="flex h-10 w-10 items-center justify-center text-foreground transition-colors hover:text-primary"
            aria-label="Account"
          >
            <User size={18} strokeWidth={1.5} />
          </Link>

          <Link
            href="/cart"
            className="relative flex h-10 w-10 items-center justify-center text-foreground transition-colors hover:text-primary"
            aria-label={`Cart, ${totalItems} items`}
          >
            <ShoppingBag size={18} strokeWidth={1.5} />
            {totalItems > 0 && (
              <span className="absolute right-1.5 top-1.5 flex h-4 w-4 items-center justify-center rounded-full bg-primary text-[10px] font-bold text-primary-foreground">
                {totalItems > 99 ? '99+' : totalItems}
              </span>
            )}
          </Link>

          <button
            className="flex h-10 w-10 items-center justify-center text-foreground transition-colors hover:text-primary md:hidden"
            onClick={() => setMobileOpen((v) => !v)}
            aria-label="Toggle menu"
            aria-expanded={mobileOpen}
          >
            {mobileOpen ? <X size={20} strokeWidth={1.5} /> : <Menu size={20} strokeWidth={1.5} />}
          </button>
        </div>
      </div>

      {mobileOpen && (
        <div className="border-t border-border bg-background px-4 pb-4 md:hidden">
          <nav className="flex flex-col gap-1 pt-2" aria-label="Mobile navigation">
            {NAV_LINKS.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                className={cn(
                  'rounded-md px-3 py-2.5 text-sm font-medium transition-colors hover:bg-muted',
                  pathname === link.href ? 'text-primary' : 'text-foreground',
                )}
                onClick={() => setMobileOpen(false)}
              >
                {link.label}
              </Link>
            ))}
          </nav>
        </div>
      )}
    </header>
  );
}
