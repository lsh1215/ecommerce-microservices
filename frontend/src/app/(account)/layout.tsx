'use client';

import type { ReactNode } from 'react';
import { redirect } from 'next/navigation';
import { Header } from '@/components/shared/Header';
import { Footer } from '@/components/shared/Footer';
import { MobileNav } from '@/components/shared/MobileNav';
import { useAuthStore } from '@/features/auth/store/auth-store';
import { useFromStore } from '@/hooks/use-from-store';
import { Skeleton } from '@/components/shared/Skeleton';
import { AccountSidebar } from './AccountSidebar';

export default function AccountLayout({ children }: { children: ReactNode }) {
  const user = useFromStore(useAuthStore, (s) => s.user);

  if (user === undefined) {
    return (
      <>
        <Header />
        <div className="mx-auto max-w-7xl px-4 py-8 md:px-6 md:py-12">
          <div className="flex gap-8">
            <div className="hidden w-56 shrink-0 md:block">
              <Skeleton className="h-64 w-full" />
            </div>
            <main className="min-w-0 flex-1 pb-16 md:pb-0">
              <Skeleton className="h-96 w-full" />
            </main>
          </div>
        </div>
        <Footer />
        <MobileNav />
      </>
    );
  }

  if (user === null) {
    redirect('/auth');
  }

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
