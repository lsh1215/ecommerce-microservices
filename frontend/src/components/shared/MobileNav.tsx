'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Home, Zap, Search, ShoppingBag, User } from 'lucide-react';
import { useCartStore } from '@/features/cart/store/cart-store';

const TABS = [
  { href: '/', label: 'Home', icon: Home, badge: false },
  { href: '/drops', label: 'Drops', icon: Zap, badge: false },
  { href: '/products', label: 'Search', icon: Search, badge: false },
  { href: '/cart', label: 'Cart', icon: ShoppingBag, badge: true },
  { href: '/auth', label: 'Account', icon: User, badge: false },
] as const;

export function MobileNav() {
  const pathname = usePathname();
  const totalItems = useCartStore((s) => s.totalItems);

  return (
    <nav
      className="fixed bottom-0 left-0 right-0 z-50 border-t border-[#e8e4df] bg-[#faf9f6] md:hidden"
      aria-label="Mobile navigation"
    >
      <div className="flex h-16 items-stretch">
        {TABS.map(({ href, label, icon: Icon, badge }) => {
          const isActive = pathname === href || (href !== '/' && pathname.startsWith(href));
          const showBadge = badge && totalItems > 0;

          return (
            <Link
              key={href}
              href={href}
              className={`relative flex flex-1 flex-col items-center justify-center gap-1 text-xs transition-colors ${
                isActive ? 'text-[#c4633e]' : 'text-[#6b6560]'
              }`}
              aria-label={label}
              aria-current={isActive ? 'page' : undefined}
            >
              <div className="relative">
                <Icon size={22} strokeWidth={isActive ? 2 : 1.5} />
                {showBadge && (
                  <span className="absolute -right-1.5 -top-1.5 flex h-4 w-4 items-center justify-center bg-[#c4633e] text-[9px] font-bold text-white">
                    {totalItems > 9 ? '9+' : totalItems}
                  </span>
                )}
              </div>
              <span className="font-medium">{label}</span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
