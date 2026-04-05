'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { User, Package, MapPin } from 'lucide-react';
import { cn } from '@/lib/utils';

const ACCOUNT_NAV = [
  { href: '/account/profile', label: 'Profile', icon: User },
  { href: '/account/addresses', label: 'Addresses', icon: MapPin },
  { href: '/account/orders', label: 'Orders', icon: Package },
];

export function AccountSidebar() {
  const pathname = usePathname();

  return (
    <aside className="hidden w-48 shrink-0 md:block">
      <p className="mb-6 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
        Account
      </p>
      <nav className="flex flex-col gap-1" aria-label="Account navigation">
        {ACCOUNT_NAV.map((item) => {
          const Icon = item.icon;
          const isActive = pathname.startsWith(item.href);

          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                'flex items-center gap-2.5 rounded-md px-3 py-2 text-sm transition-colors',
                isActive
                  ? 'bg-primary-light font-medium text-primary'
                  : 'text-foreground hover:bg-muted hover:text-primary',
              )}
            >
              <Icon size={16} strokeWidth={1.5} />
              {item.label}
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}
