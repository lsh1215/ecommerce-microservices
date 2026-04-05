'use client';

import Link from 'next/link';
import { Search, ShoppingCart, User, Menu } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet';
import { useState } from 'react';

const NAV_LINKS = [
  { href: '/products', label: '상품' },
  { href: '/categories', label: '카테고리' },
] as const;

export function Header() {
  const [searchOpen, setSearchOpen] = useState(false);

  return (
    <header className="sticky top-0 z-50 border-b border-border bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        {/* Mobile menu */}
        <div className="flex items-center gap-2 lg:hidden">
          <Sheet>
            <SheetTrigger render={<Button variant="ghost" size="icon" aria-label="메뉴" />}>
              <Menu className="h-5 w-5" />
            </SheetTrigger>
            <SheetContent side="left" className="w-72">
              <SheetHeader>
                <SheetTitle>메뉴</SheetTitle>
              </SheetHeader>
              <nav className="mt-6 flex flex-col gap-4 px-4">
                {NAV_LINKS.map((link) => (
                  <Link
                    key={link.href}
                    href={link.href}
                    className="text-base font-medium text-foreground transition-colors hover:text-primary"
                  >
                    {link.label}
                  </Link>
                ))}
              </nav>
            </SheetContent>
          </Sheet>
        </div>

        {/* Logo */}
        <Link href="/" className="text-xl font-semibold tracking-tight text-foreground">
          E-Commerce
        </Link>

        {/* Desktop nav */}
        <nav className="hidden items-center gap-8 lg:flex">
          {NAV_LINKS.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
            >
              {link.label}
            </Link>
          ))}
        </nav>

        {/* Actions */}
        <div className="flex items-center gap-1">
          {/* Search (desktop inline, mobile toggle) */}
          <div className="hidden items-center sm:flex">
            {searchOpen ? (
              <form className="relative" onSubmit={(e) => e.preventDefault()}>
                <Input
                  type="search"
                  placeholder="검색..."
                  className="h-9 w-48 pr-8 text-sm lg:w-64"
                  autoFocus
                  onBlur={() => setSearchOpen(false)}
                />
                <Search className="absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              </form>
            ) : (
              <Button
                variant="ghost"
                size="icon"
                aria-label="검색"
                onClick={() => setSearchOpen(true)}
              >
                <Search className="h-5 w-5" />
              </Button>
            )}
          </div>

          <Button variant="ghost" size="icon" className="sm:hidden" aria-label="검색">
            <Search className="h-5 w-5" />
          </Button>

          <Button variant="ghost" size="icon" render={<Link href="/auth" />} aria-label="내 계정">
            <User className="h-5 w-5" />
          </Button>

          <Button
            variant="ghost"
            size="icon"
            className="relative"
            render={<Link href="/cart" />}
            aria-label="장바구니"
          >
            <ShoppingCart className="h-5 w-5" />
          </Button>
        </div>
      </div>
    </header>
  );
}
