import type { ReactNode } from 'react';
import Link from 'next/link';

export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-surface">
      <header className="flex items-center justify-center py-8">
        <Link href="/" className="text-2xl font-bold tracking-tight text-foreground">
          Shop
        </Link>
      </header>
      <main className="flex flex-1 items-start justify-center px-4 py-8">{children}</main>
    </div>
  );
}
