import Link from 'next/link';
import { Separator } from '@/components/ui/separator';

const FOOTER_LINKS = [
  { href: '/about', label: '소개' },
  { href: '/contact', label: '고객센터' },
  { href: '/faq', label: 'FAQ' },
  { href: '/privacy', label: '개인정보처리방침' },
  { href: '/terms', label: '이용약관' },
] as const;

export function Footer() {
  return (
    <footer className="border-t border-border bg-background">
      <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <nav className="flex flex-wrap items-center justify-center gap-x-6 gap-y-2">
          {FOOTER_LINKS.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="text-sm text-muted-foreground transition-colors hover:text-foreground"
            >
              {link.label}
            </Link>
          ))}
        </nav>
        <Separator className="my-6" />
        <p className="text-center text-xs text-muted-foreground">
          &copy; {new Date().getFullYear()} E-Commerce. All rights reserved.
        </p>
      </div>
    </footer>
  );
}
