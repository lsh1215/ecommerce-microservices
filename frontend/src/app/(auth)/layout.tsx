import type { ReactNode } from 'react';
import Link from 'next/link';

export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-[#faf9f6]">
      <header className="flex items-center justify-center py-8">
        <Link href="/" className="font-heading text-2xl font-bold tracking-widest text-[#1a1a1a]">
          FOUNDRY
        </Link>
      </header>
      <main className="flex flex-1 items-start justify-center px-4 py-8">{children}</main>
    </div>
  );
}
