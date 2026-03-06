import Link from 'next/link';
import { Instagram, Youtube } from 'lucide-react';

const SHOP_LINKS = [
  { href: '/drops', label: 'Drops' },
  { href: '/products', label: 'All Products' },
  { href: '/brands', label: 'Brands' },
  { href: '/size-guide', label: 'Size Guide' },
];

const INFO_LINKS = [
  { href: '/info/shipping', label: 'Shipping & Returns' },
  { href: '/info/sizing', label: 'How to Measure' },
  { href: '/about', label: 'About FOUNDRY' },
];

const ACCOUNT_LINKS = [
  { href: '/auth', label: 'Login / Register' },
  { href: '/profile', label: 'My Profile' },
  { href: '/orders', label: 'Order History' },
];

export function Footer() {
  return (
    <footer className="border-t border-[#e8e4df] bg-[#1a1a1a] text-[#faf9f6]">
      <div className="mx-auto max-w-7xl px-4 py-16 md:px-6">
        <div className="grid grid-cols-2 gap-8 md:grid-cols-4">
          {/* Brand */}
          <div className="col-span-2 md:col-span-1">
            <p className="font-heading text-2xl font-bold">FOUNDRY</p>
            <p className="mt-3 text-sm leading-relaxed text-[#a39e93]">
              Curated heritage menswear from Korea, Japan, and the American frontier. Timed drops.
              No restocks.
            </p>
            <div className="mt-6 flex items-center gap-4">
              <a
                href="https://instagram.com"
                target="_blank"
                rel="noopener noreferrer"
                aria-label="Instagram"
                className="text-[#a39e93] transition-colors hover:text-[#faf9f6]"
              >
                <Instagram size={18} strokeWidth={1.5} />
              </a>
              <a
                href="https://youtube.com"
                target="_blank"
                rel="noopener noreferrer"
                aria-label="YouTube"
                className="text-[#a39e93] transition-colors hover:text-[#faf9f6]"
              >
                <Youtube size={18} strokeWidth={1.5} />
              </a>
            </div>
          </div>

          {/* Shop */}
          <div>
            <p className="mb-4 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
              Shop
            </p>
            <ul className="flex flex-col gap-2.5">
              {SHOP_LINKS.map((link) => (
                <li key={link.href}>
                  <Link
                    href={link.href}
                    className="text-sm text-[#a39e93] transition-colors hover:text-[#faf9f6]"
                  >
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          {/* Info */}
          <div>
            <p className="mb-4 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
              Information
            </p>
            <ul className="flex flex-col gap-2.5">
              {INFO_LINKS.map((link) => (
                <li key={link.href}>
                  <Link
                    href={link.href}
                    className="text-sm text-[#a39e93] transition-colors hover:text-[#faf9f6]"
                  >
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          {/* Account */}
          <div>
            <p className="mb-4 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
              Account
            </p>
            <ul className="flex flex-col gap-2.5">
              {ACCOUNT_LINKS.map((link) => (
                <li key={link.href}>
                  <Link
                    href={link.href}
                    className="text-sm text-[#a39e93] transition-colors hover:text-[#faf9f6]"
                  >
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        </div>

        <div className="mt-16 flex flex-col items-start justify-between gap-4 border-t border-[#333] pt-8 md:flex-row md:items-center">
          <p className="text-xs text-[#6b6560]">
            &copy; {new Date().getFullYear()} FOUNDRY. Heritage wear. Worldwide shipping.
          </p>
          <p className="text-xs text-[#6b6560]">KRW / USD / JPY</p>
        </div>
      </div>
    </footer>
  );
}
