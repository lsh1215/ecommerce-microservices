'use client';

import { redirect } from 'next/navigation';
import { useCurrencyStore } from '@/stores/currency-store';
import { useAuthStore } from '@/features/auth/store/auth-store';
import { useFromStore } from '@/hooks/use-from-store';
import { Skeleton } from '@/components/shared/Skeleton';
import type { Currency } from '@/types';

const CURRENCIES: { value: Currency; label: string }[] = [
  { value: 'KRW', label: 'KRW — Korean Won (₩)' },
  { value: 'USD', label: 'USD — US Dollar ($)' },
  { value: 'JPY', label: 'JPY — Japanese Yen (¥)' },
];

export default function ProfilePage() {
  const user = useFromStore(useAuthStore, (s) => s.user);
  const currency = useFromStore(useCurrencyStore, (s) => s.currency);
  const setCurrency = useCurrencyStore((s) => s.setCurrency);

  if (user === undefined) {
    return (
      <div>
        <h1 className="font-heading mb-8 text-3xl font-bold text-[#1a1a1a]">Profile</h1>
        <div className="space-y-6">
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-48 w-full" />
        </div>
      </div>
    );
  }

  if (user === null) {
    redirect('/auth?redirect=/profile');
  }

  const displayName = user.name;
  const displayEmail = user.email;

  return (
    <div>
      <h1 className="font-heading mb-8 text-3xl font-bold text-[#1a1a1a]">Profile</h1>

      {/* Account Info */}
      <section className="mb-10">
        <h2 className="mb-4 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
          Account Information
        </h2>
        <div className="border border-[#e8e4df] p-5">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <p className="text-xs font-medium text-[#a39e93]">Name</p>
              <p className="mt-1 text-sm font-medium text-[#1a1a1a]">{displayName}</p>
            </div>
            <div>
              <p className="text-xs font-medium text-[#a39e93]">Email</p>
              <p className="mt-1 text-sm font-medium text-[#1a1a1a]">{displayEmail}</p>
            </div>
          </div>
        </div>
      </section>

      {/* Currency Preference */}
      <section className="mb-10">
        <h2 className="mb-4 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
          Currency Preference
        </h2>
        <div className="border border-[#e8e4df] p-5">
          <p className="mb-4 text-sm text-[#6b6560]">
            All prices across the site will be displayed in your selected currency.
          </p>
          <div className="flex flex-col gap-2">
            {CURRENCIES.map((c) => (
              <label
                key={c.value}
                className={`flex cursor-pointer items-center gap-3 border px-4 py-3 text-sm transition-colors ${
                  currency === c.value
                    ? 'border-[#c4633e] bg-[#fdf7f4]'
                    : 'border-[#e8e4df] hover:border-[#a39e93]'
                }`}
              >
                <input
                  type="radio"
                  name="currency"
                  value={c.value}
                  checked={currency === c.value}
                  onChange={() => setCurrency(c.value)}
                  className="h-4 w-4 accent-[#c4633e]"
                />
                <span className="font-medium text-[#1a1a1a]">{c.label}</span>
              </label>
            ))}
          </div>
        </div>
      </section>

      {/* Saved Addresses */}
      <section>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
            Saved Addresses
          </h2>
        </div>

        <div className="border border-[#e8e4df] p-5 text-center">
          <p className="text-sm text-[#6b6560]">
            No saved addresses yet. Addresses will be saved from your orders.
          </p>
        </div>
      </section>
    </div>
  );
}
