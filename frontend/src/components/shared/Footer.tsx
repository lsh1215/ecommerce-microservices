import Link from 'next/link';
import { Instagram, Twitter } from 'lucide-react';

const SHOP_LINKS = [
  { href: '/products', label: 'All Products' },
  { href: '/products?category=tops', label: 'Tops' },
  { href: '/products?category=bottoms', label: 'Bottoms' },
  { href: '/products?category=outerwear', label: 'Outerwear' },
];

const SUPPORT_LINKS = [
  { href: '/info/shipping', label: 'Shipping & Returns' },
  { href: '/info/faq', label: 'FAQ' },
  { href: '/info/contact', label: 'Contact Us' },
];

const ACCOUNT_LINKS = [
  { href: '/auth', label: 'Login / Register' },
  { href: '/account/orders', label: 'Order History' },
  { href: '/account/profile', label: 'My Profile' },
];

export function Footer() {
  return (
    <footer className="border-t border-border bg-foreground text-background">
      <div className="mx-auto max-w-7xl px-4 py-12 md:px-6">
        <div className="grid grid-cols-2 gap-8 md:grid-cols-4">
          <div className="col-span-2 md:col-span-1">
            <p className="text-xl font-bold">Shop</p>
            <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
              Quality products at great prices. Free shipping on orders over ₩50,000.
            </p>
            <div className="mt-6 flex items-center gap-4">
              <a
                href="https://instagram.com"
                target="_blank"
                rel="noopener noreferrer"
                aria-label="Instagram"
                className="text-muted-foreground transition-colors hover:text-background"
              >
                <Instagram size={18} strokeWidth={1.5} />
              </a>
              <a
                href="https://twitter.com"
                target="_blank"
                rel="noopener noreferrer"
                aria-label="Twitter"
                className="text-muted-foreground transition-colors hover:text-background"
              >
                <Twitter size={18} strokeWidth={1.5} />
              </a>
            </div>
          </div>

          <div>
            <p className="mb-4 text-xs font-semibold uppercase tracking-widest text-muted-foreground">
              Shop
            </p>
            <ul className="flex flex-col gap-2.5">
              {SHOP_LINKS.map((link) => (
                <li key={link.href}>
                  <Link
                    href={link.href}
                    className="text-sm text-muted-foreground transition-colors hover:text-background"
                  >
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          <div>
            <p className="mb-4 text-xs font-semibold uppercase tracking-widest text-muted-foreground">
              Support
            </p>
            <ul className="flex flex-col gap-2.5">
              {SUPPORT_LINKS.map((link) => (
                <li key={link.href}>
                  <Link
                    href={link.href}
                    className="text-sm text-muted-foreground transition-colors hover:text-background"
                  >
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          <div>
            <p className="mb-4 text-xs font-semibold uppercase tracking-widest text-muted-foreground">
              Account
            </p>
            <ul className="flex flex-col gap-2.5">
              {ACCOUNT_LINKS.map((link) => (
                <li key={link.href}>
                  <Link
                    href={link.href}
                    className="text-sm text-muted-foreground transition-colors hover:text-background"
                  >
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        </div>

        <div className="mt-12 flex flex-col items-start justify-between gap-4 border-t border-muted pt-8 md:flex-row md:items-center">
          <p className="text-xs text-muted-foreground">
            &copy; {new Date().getFullYear()} Shop. All rights reserved.
          </p>
          <p className="text-xs text-muted-foreground">Prices in KRW</p>
        </div>
      </div>
    </footer>
  );
}
