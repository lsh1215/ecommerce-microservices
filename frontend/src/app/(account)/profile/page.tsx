'use client';

import { redirect } from 'next/navigation';
import { useAuthStore } from '@/features/auth/store/auth-store';
import { useFromStore } from '@/hooks/use-from-store';
import { Skeleton } from '@/components/shared/Skeleton';

export default function ProfilePage() {
  const user = useFromStore(useAuthStore, (s) => s.user);

  if (user === undefined) {
    return (
      <div>
        <h1 className="mb-8 text-3xl font-bold text-foreground">Profile</h1>
        <div className="space-y-6">
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-48 w-full" />
        </div>
      </div>
    );
  }

  if (user === null) {
    redirect('/auth?redirect=/account/profile');
  }

  return (
    <div>
      <h1 className="mb-8 text-3xl font-bold text-foreground">Profile</h1>

      <section className="mb-10">
        <h2 className="mb-4 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          Account Information
        </h2>
        <div className="rounded-lg border border-border p-5">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <p className="text-xs font-medium text-muted-foreground">Name</p>
              <p className="mt-1 text-sm font-medium text-foreground">{user.name}</p>
            </div>
            <div>
              <p className="text-xs font-medium text-muted-foreground">Email</p>
              <p className="mt-1 text-sm font-medium text-foreground">{user.email}</p>
            </div>
          </div>
        </div>
      </section>

      <section>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            Saved Addresses
          </h2>
        </div>
        <div className="rounded-lg border border-border p-5 text-center">
          <p className="text-sm text-muted-foreground">
            No saved addresses yet. Addresses will be saved from your orders.
          </p>
        </div>
      </section>
    </div>
  );
}
