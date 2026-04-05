import type { ReactNode } from 'react';
import { Header } from '@/components/shared/Header';
import { Footer } from '@/components/shared/Footer';
import { MobileNav } from '@/components/shared/MobileNav';

export default function ShopLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <Header />
      <main className="min-h-screen pb-16 md:pb-0">{children}</main>
      <Footer />
      <MobileNav />
    </>
  );
}
