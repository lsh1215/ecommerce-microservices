'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { User, Package, Settings } from 'lucide-react';

const ACCOUNT_NAV = [
  { href: '/profile', label: 'Profile', icon: User },
  { href: '/orders', label: 'Orders', icon: Package },
  { href: '#', label: 'Settings', icon: Settings, disabled: true },
];

export function AccountSidebar() {
  const pathname = usePathname();

  return (
    <aside className="hidden w-48 shrink-0 md:block">
      <p className="mb-6 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">Account</p>
      <nav className="flex flex-col gap-1" aria-label="Account navigation">
        {ACCOUNT_NAV.map((item) => {
          const Icon = item.icon;
          const isActive = pathname.startsWith(item.href) && !item.disabled;

          if (item.disabled) {
            return (
              <span
                key={item.label}
                className="flex items-center gap-2.5 px-3 py-2 text-sm text-[#a39e93] cursor-not-allowed"
              >
                <Icon size={16} strokeWidth={1.5} />
                {item.label}
              </span>
            );
          }

          return (
            <Link
              key={item.href}
              href={item.href}
              className={`flex items-center gap-2.5 px-3 py-2 text-sm transition-colors ${
                isActive
                  ? 'bg-[#f3f0eb] font-medium text-[#c4633e]'
                  : 'text-[#1a1a1a] hover:bg-[#f3f0eb] hover:text-[#c4633e]'
              }`}
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
