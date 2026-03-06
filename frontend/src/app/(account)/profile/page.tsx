'use client';

import { useCurrencyStore } from '@/stores/currency-store';
import { useAuthStore } from '@/features/auth/store/auth-store';
import { useFromStore } from '@/hooks/use-from-store';
import type { Currency, ShippingAddress } from '@/types';

const CURRENCIES: { value: Currency; label: string }[] = [
  { value: 'KRW', label: 'KRW — Korean Won (₩)' },
  { value: 'USD', label: 'USD — US Dollar ($)' },
  { value: 'JPY', label: 'JPY — Japanese Yen (¥)' },
];

const MOCK_ADDRESSES: ShippingAddress[] = [
  {
    name: 'Kim Minsu',
    phone: '+82-10-1234-5678',
    address: '123 Gangnam-daero',
    addressDetail: 'Apt 1204',
    city: 'Seoul',
    country: 'KR',
    postalCode: '06234',
  },
  {
    name: 'Kim Minsu',
    phone: '+82-10-1234-5678',
    address: '456 Teheran-ro',
    city: 'Seoul',
    country: 'KR',
    postalCode: '06174',
  },
];

export default function ProfilePage() {
  const user = useFromStore(useAuthStore, (s) => s.user);
  const currency = useFromStore(useCurrencyStore, (s) => s.currency);
  const setCurrency = useCurrencyStore((s) => s.setCurrency);

  const displayName = user?.name ?? 'Kim Minsu';
  const displayEmail = user?.email ?? 'minsu@example.com';

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
          <button
            type="button"
            className="text-sm font-medium text-[#c4633e] underline underline-offset-4"
          >
            Add New Address
          </button>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {MOCK_ADDRESSES.map((addr, idx) => (
            <div key={idx} className="border border-[#e8e4df] p-5">
              <p className="text-sm font-medium text-[#1a1a1a]">{addr.name}</p>
              <p className="mt-1 text-sm text-[#6b6560]">{addr.phone}</p>
              <p className="mt-2 text-sm text-[#6b6560]">
                {addr.address}
                {addr.addressDetail && `, ${addr.addressDetail}`}
              </p>
              <p className="text-sm text-[#6b6560]">
                {addr.city}, {addr.country} {addr.postalCode}
              </p>
              <div className="mt-4 flex gap-3">
                <button
                  type="button"
                  className="text-xs font-medium text-[#1a1a1a] underline underline-offset-4"
                >
                  Edit
                </button>
                <button
                  type="button"
                  className="text-xs font-medium text-[#c4633e] underline underline-offset-4"
                >
                  Remove
                </button>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
