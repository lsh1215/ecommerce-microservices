import type { ReactNode } from 'react';
import { Header } from '@/components/shared/Header';
import { Footer } from '@/components/shared/Footer';
import { MobileNav } from '@/components/shared/MobileNav';
import { AccountSidebar } from './AccountSidebar';

export default function AccountLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <Header />
      <div className="mx-auto max-w-7xl px-4 py-8 md:px-6 md:py-12">
        <div className="flex gap-8">
          <AccountSidebar />
          <main className="min-w-0 flex-1 pb-16 md:pb-0">{children}</main>
        </div>
      </div>
      <Footer />
      <MobileNav />
    </>
  );
}
