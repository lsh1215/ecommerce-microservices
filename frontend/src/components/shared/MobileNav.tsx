'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Home, Search, ShoppingBag, User } from 'lucide-react';
import { useCartStore } from '@/features/cart/store/cart-store';
import { cn } from '@/lib/utils';

const TABS = [
  { href: '/', label: 'Home', icon: Home },
  { href: '/products', label: 'Products', icon: Search },
  { href: '/cart', label: 'Cart', icon: ShoppingBag, showBadge: true },
  { href: '/auth', label: 'Account', icon: User },
] as const;

export function MobileNav() {
  const pathname = usePathname();
  const totalItems = useCartStore((s) => s.totalItems);

  return (
    <nav
      className="fixed bottom-0 left-0 right-0 z-50 border-t border-border bg-background md:hidden"
      aria-label="Mobile navigation"
    >
      <div className="flex h-16 items-stretch">
        {TABS.map(({ href, label, icon: Icon, ...rest }) => {
          const showBadge = 'showBadge' in rest && rest.showBadge && totalItems > 0;
          const isActive = pathname === href || (href !== '/' && pathname.startsWith(href));

          return (
            <Link
              key={href}
              href={href}
              className={cn(
                'relative flex flex-1 flex-col items-center justify-center gap-1 text-xs transition-colors',
                isActive ? 'text-primary' : 'text-muted-foreground',
              )}
              aria-label={label}
              aria-current={isActive ? 'page' : undefined}
            >
              <div className="relative">
                <Icon size={22} strokeWidth={isActive ? 2 : 1.5} />
                {showBadge && (
                  <span className="absolute -right-1.5 -top-1.5 flex h-4 w-4 items-center justify-center rounded-full bg-primary text-[9px] font-bold text-primary-foreground">
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
