'use client';

import { useState, useMemo } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { z } from 'zod/v4';
import { zodResolver } from '@hookform/resolvers/zod';
import { ChevronDown, ChevronUp, Loader2 } from 'lucide-react';
import { useCartStore } from '@/features/cart/store/cart-store';
import { useCurrencyStore } from '@/stores/currency-store';
import { CurrencyPrice } from '@/components/shared/CurrencyPrice';
import { formatPrice, getPrice } from '@/utils/currency';
import { useFromStore } from '@/hooks/use-from-store';
import { mockOrder } from '@/mocks/orders';

const DUTY_RATE = 0.08;

const shippingSchema = z.object({
  name: z.string().min(2, 'Name must be at least 2 characters'),
  phone: z.string().min(7, 'Enter a valid phone number'),
  address: z.string().min(5, 'Enter your full address'),
  addressDetail: z.string().optional(),
  city: z.string().min(2, 'Enter your city'),
  country: z.string().min(2, 'Select a country'),
  postalCode: z.string().min(3, 'Enter a valid postal code'),
});

type ShippingFormData = z.infer<typeof shippingSchema>;

const PAYMENT_METHODS = [
  { value: 'credit_card', label: 'Credit Card' },
  { value: 'bank_transfer', label: 'Bank Transfer' },
] as const;

const COUNTRIES = [
  'South Korea',
  'Japan',
  'United States',
  'Canada',
  'United Kingdom',
  'Germany',
  'France',
  'Australia',
  'Singapore',
  'Hong Kong',
];

export default function CheckoutPage() {
  const router = useRouter();
  const items = useFromStore(useCartStore, (s) => s.items);
  const clearCart = useCartStore((s) => s.clearCart);
  const currency = useCurrencyStore((s) => s.currency);

  const [paymentMethod, setPaymentMethod] = useState<string>('credit_card');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [summaryOpen, setSummaryOpen] = useState(true);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ShippingFormData>({
    resolver: zodResolver(shippingSchema),
    defaultValues: {
      country: 'South Korea',
    },
  });

  const subtotal = useMemo(() => {
    if (!items) return { priceKrw: 0, priceUsd: 0, priceJpy: 0 };
    return items.reduce(
      (acc, item) => ({
        priceKrw: acc.priceKrw + item.priceKrw * item.quantity,
        priceUsd: acc.priceUsd + item.priceUsd * item.quantity,
        priceJpy: acc.priceJpy + item.priceJpy * item.quantity,
      }),
      { priceKrw: 0, priceUsd: 0, priceJpy: 0 },
    );
  }, [items]);

  const duty = {
    priceKrw: Math.round(subtotal.priceKrw * DUTY_RATE),
    priceUsd: Math.round(subtotal.priceUsd * DUTY_RATE * 100) / 100,
    priceJpy: Math.round(subtotal.priceJpy * DUTY_RATE),
  };

  const total = {
    priceKrw: subtotal.priceKrw + duty.priceKrw,
    priceUsd: subtotal.priceUsd + duty.priceUsd,
    priceJpy: subtotal.priceJpy + duty.priceJpy,
  };

  const onSubmit = async () => {
    setIsSubmitting(true);
    await new Promise((resolve) => setTimeout(resolve, 1500));
    clearCart();
    router.push(`/orders/${mockOrder.id}/confirmation`);
  };

  if (items === undefined) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-16 md:px-6">
        <div className="animate-pulse space-y-4">
          <div className="h-8 w-40 bg-[#e8e4df]" />
          <div className="h-64 bg-[#e8e4df]" />
        </div>
      </div>
    );
  }

  if (!items || items.length === 0) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-24 text-center md:px-6">
        <h1 className="font-heading text-2xl font-bold text-[#1a1a1a]">Nothing to check out</h1>
        <p className="mt-2 text-sm text-[#6b6560]">Your cart is empty.</p>
        <Link
          href="/products"
          className="mt-8 inline-block bg-[#c4633e] px-8 py-3 text-sm font-semibold uppercase tracking-widest text-white transition-colors hover:bg-[#a84f2e]"
        >
          Shop Products
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 md:px-6">
      <h1 className="font-heading text-3xl font-bold text-[#1a1a1a]">Checkout</h1>

      <form
        onSubmit={handleSubmit(onSubmit)}
        className="mt-8 grid grid-cols-1 gap-10 lg:grid-cols-3"
      >
        {/* Left: Shipping + Payment */}
        <div className="space-y-8 lg:col-span-2">
          {/* Shipping */}
          <section>
            <h2 className="font-heading text-lg font-bold text-[#1a1a1a]">Shipping Address</h2>
            <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
              <div className="md:col-span-2">
                <label
                  htmlFor="name"
                  className="mb-1 block text-xs font-semibold uppercase tracking-wider text-[#6b6560]"
                >
                  Full Name
                </label>
                <input
                  id="name"
                  type="text"
                  {...register('name')}
                  className={`w-full border px-3 py-2.5 text-sm text-[#1a1a1a] placeholder:text-[#a39e93] focus:outline-none ${
                    errors.name
                      ? 'border-red-400 focus:border-red-500'
                      : 'border-[#e8e4df] focus:border-[#1a1a1a]'
                  }`}
                  placeholder="Kim Minsu"
                />
                {errors.name && <p className="mt-1 text-xs text-red-500">{errors.name.message}</p>}
              </div>

              <div>
                <label
                  htmlFor="phone"
                  className="mb-1 block text-xs font-semibold uppercase tracking-wider text-[#6b6560]"
                >
                  Phone
                </label>
                <input
                  id="phone"
                  type="tel"
                  {...register('phone')}
                  className={`w-full border px-3 py-2.5 text-sm text-[#1a1a1a] placeholder:text-[#a39e93] focus:outline-none ${
                    errors.phone
                      ? 'border-red-400 focus:border-red-500'
                      : 'border-[#e8e4df] focus:border-[#1a1a1a]'
                  }`}
                  placeholder="+82-10-1234-5678"
                />
                {errors.phone && (
                  <p className="mt-1 text-xs text-red-500">{errors.phone.message}</p>
                )}
              </div>

              <div>
                <label
                  htmlFor="country"
                  className="mb-1 block text-xs font-semibold uppercase tracking-wider text-[#6b6560]"
                >
                  Country
                </label>
                <select
                  id="country"
                  {...register('country')}
                  className={`w-full border bg-white px-3 py-2.5 text-sm text-[#1a1a1a] focus:outline-none ${
                    errors.country
                      ? 'border-red-400 focus:border-red-500'
                      : 'border-[#e8e4df] focus:border-[#1a1a1a]'
                  }`}
                >
                  {COUNTRIES.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
                {errors.country && (
                  <p className="mt-1 text-xs text-red-500">{errors.country.message}</p>
                )}
              </div>

              <div className="md:col-span-2">
                <label
                  htmlFor="address"
                  className="mb-1 block text-xs font-semibold uppercase tracking-wider text-[#6b6560]"
                >
                  Address
                </label>
                <input
                  id="address"
                  type="text"
                  {...register('address')}
                  className={`w-full border px-3 py-2.5 text-sm text-[#1a1a1a] placeholder:text-[#a39e93] focus:outline-none ${
                    errors.address
                      ? 'border-red-400 focus:border-red-500'
                      : 'border-[#e8e4df] focus:border-[#1a1a1a]'
                  }`}
                  placeholder="123 Gangnam-daero"
                />
                {errors.address && (
                  <p className="mt-1 text-xs text-red-500">{errors.address.message}</p>
                )}
              </div>

              <div className="md:col-span-2">
                <label
                  htmlFor="addressDetail"
                  className="mb-1 block text-xs font-semibold uppercase tracking-wider text-[#6b6560]"
                >
                  Address Detail (optional)
                </label>
                <input
                  id="addressDetail"
                  type="text"
                  {...register('addressDetail')}
                  className="w-full border border-[#e8e4df] px-3 py-2.5 text-sm text-[#1a1a1a] placeholder:text-[#a39e93] focus:border-[#1a1a1a] focus:outline-none"
                  placeholder="Apt, suite, floor..."
                />
              </div>

              <div>
                <label
                  htmlFor="city"
                  className="mb-1 block text-xs font-semibold uppercase tracking-wider text-[#6b6560]"
                >
                  City
                </label>
                <input
                  id="city"
                  type="text"
                  {...register('city')}
                  className={`w-full border px-3 py-2.5 text-sm text-[#1a1a1a] placeholder:text-[#a39e93] focus:outline-none ${
                    errors.city
                      ? 'border-red-400 focus:border-red-500'
                      : 'border-[#e8e4df] focus:border-[#1a1a1a]'
                  }`}
                  placeholder="Seoul"
                />
                {errors.city && <p className="mt-1 text-xs text-red-500">{errors.city.message}</p>}
              </div>

              <div>
                <label
                  htmlFor="postalCode"
                  className="mb-1 block text-xs font-semibold uppercase tracking-wider text-[#6b6560]"
                >
                  Postal Code
                </label>
                <input
                  id="postalCode"
                  type="text"
                  {...register('postalCode')}
                  className={`w-full border px-3 py-2.5 text-sm text-[#1a1a1a] placeholder:text-[#a39e93] focus:outline-none ${
                    errors.postalCode
                      ? 'border-red-400 focus:border-red-500'
                      : 'border-[#e8e4df] focus:border-[#1a1a1a]'
                  }`}
                  placeholder="06234"
                />
                {errors.postalCode && (
                  <p className="mt-1 text-xs text-red-500">{errors.postalCode.message}</p>
                )}
              </div>
            </div>
          </section>

          {/* Payment method */}
          <section className="border-t border-[#e8e4df] pt-8">
            <h2 className="font-heading text-lg font-bold text-[#1a1a1a]">Payment Method</h2>
            <div className="mt-4 space-y-3">
              {PAYMENT_METHODS.map((method) => (
                <label
                  key={method.value}
                  className={`flex cursor-pointer items-center gap-3 border p-4 transition-colors ${
                    paymentMethod === method.value
                      ? 'border-[#1a1a1a] bg-[#f3f0eb]'
                      : 'border-[#e8e4df] hover:border-[#a39e93]'
                  }`}
                >
                  <input
                    type="radio"
                    name="payment"
                    value={method.value}
                    checked={paymentMethod === method.value}
                    onChange={() => setPaymentMethod(method.value)}
                    className="accent-[#1a1a1a]"
                  />
                  <span className="text-sm font-medium text-[#1a1a1a]">{method.label}</span>
                </label>
              ))}
            </div>
          </section>

          {/* Place order (desktop) */}
          <div className="hidden lg:block">
            <button
              type="submit"
              disabled={isSubmitting}
              className="flex w-full items-center justify-center gap-2 bg-[#c4633e] px-6 py-4 text-sm font-semibold uppercase tracking-widest text-white transition-colors hover:bg-[#a84f2e] disabled:opacity-60"
            >
              {isSubmitting ? (
                <>
                  <Loader2 size={16} className="animate-spin" />
                  Processing...
                </>
              ) : (
                'Place Order'
              )}
            </button>
          </div>
        </div>

        {/* Right: Order summary */}
        <div className="lg:col-span-1">
          <div className="sticky top-20 border border-[#e8e4df] p-6">
            <button
              type="button"
              onClick={() => setSummaryOpen(!summaryOpen)}
              className="flex w-full items-center justify-between lg:pointer-events-none"
            >
              <h2 className="font-heading text-lg font-bold text-[#1a1a1a]">Order Summary</h2>
              <span className="lg:hidden">
                {summaryOpen ? (
                  <ChevronUp size={16} className="text-[#6b6560]" />
                ) : (
                  <ChevronDown size={16} className="text-[#6b6560]" />
                )}
              </span>
            </button>

            <div className={`${summaryOpen ? 'block' : 'hidden'} lg:block`}>
              {/* Items */}
              <div className="mt-4 divide-y divide-[#e8e4df]">
                {items.map((item) => (
                  <div key={`${item.productId}-${item.size}`} className="flex gap-3 py-3">
                    <div className="relative h-16 w-12 shrink-0 overflow-hidden bg-[#e8e4df]">
                      {item.imageUrl && (
                        <Image
                          src={item.imageUrl}
                          alt={item.productName}
                          fill
                          className="object-cover"
                          sizes="48px"
                        />
                      )}
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-[#1a1a1a]">
                        {item.productName}
                      </p>
                      <p className="text-xs text-[#6b6560]">
                        {item.brandName} · Size {item.size} · Qty {item.quantity}
                      </p>
                    </div>
                    <p className="shrink-0 text-sm font-medium text-[#1a1a1a]">
                      <CurrencyPrice
                        priceKrw={item.priceKrw * item.quantity}
                        priceUsd={item.priceUsd * item.quantity}
                        priceJpy={item.priceJpy * item.quantity}
                      />
                    </p>
                  </div>
                ))}
              </div>

              {/* Totals */}
              <div className="mt-4 space-y-3 border-t border-[#e8e4df] pt-4 text-sm">
                <div className="flex justify-between">
                  <span className="text-[#6b6560]">Subtotal</span>
                  <span className="font-medium text-[#1a1a1a]">
                    {formatPrice(getPrice(subtotal, currency), currency)}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[#6b6560]">Estimated Duty (8%)</span>
                  <span className="font-medium text-[#1a1a1a]">
                    {formatPrice(getPrice(duty, currency), currency)}
                  </span>
                </div>
                <div className="border-t border-[#e8e4df] pt-3">
                  <div className="flex justify-between">
                    <span className="font-semibold text-[#1a1a1a]">Total</span>
                    <span className="font-semibold text-[#1a1a1a]">
                      {formatPrice(getPrice(total, currency), currency)}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Mobile sticky place order */}
        <div className="fixed inset-x-0 bottom-0 z-50 border-t border-[#e8e4df] bg-[#faf9f6] p-3 lg:hidden">
          <button
            type="submit"
            disabled={isSubmitting}
            className="flex w-full items-center justify-center gap-2 bg-[#c4633e] px-6 py-4 text-sm font-semibold uppercase tracking-widest text-white transition-colors hover:bg-[#a84f2e] disabled:opacity-60"
          >
            {isSubmitting ? (
              <>
                <Loader2 size={16} className="animate-spin" />
                Processing...
              </>
            ) : (
              <>Place Order — {formatPrice(getPrice(total, currency), currency)}</>
            )}
          </button>
        </div>
      </form>
    </div>
  );
}
