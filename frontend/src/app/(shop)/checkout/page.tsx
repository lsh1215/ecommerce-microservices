'use client';

import { useState, useMemo } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Loader2 } from 'lucide-react';
import { useCartStore } from '@/features/cart/store/cart-store';
import { PriceDisplay } from '@/components/shared/PriceDisplay';
import { formatKRW } from '@/utils/currency';
import { useFromStore } from '@/hooks/use-from-store';
import { useAuthStore } from '@/features/auth/store/auth-store';
import { useToastStore } from '@/stores/toast-store';
import { EmptyState } from '@/components/shared/EmptyState';

type PaymentMethod = 'CARD' | 'BANK_TRANSFER' | 'VIRTUAL_ACCOUNT';

const PAYMENT_METHODS: { value: PaymentMethod; label: string }[] = [
  { value: 'CARD', label: 'Credit / Debit Card' },
  { value: 'BANK_TRANSFER', label: 'Bank Transfer' },
  { value: 'VIRTUAL_ACCOUNT', label: 'Virtual Account' },
];

const FREE_SHIPPING_THRESHOLD = 50000;

export default function CheckoutPage() {
  const router = useRouter();
  const items = useFromStore(useCartStore, (s) => s.items);
  const clearCart = useCartStore((s) => s.clearCart);
  const user = useAuthStore((s) => s.user);
  const addToast = useToastStore((s) => s.addToast);

  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CARD');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [form, setForm] = useState({
    recipientName: '',
    phone: '',
    zipCode: '',
    address1: '',
    address2: '',
  });

  const subtotal = useMemo(() => {
    if (!items) return 0;
    return items.reduce((acc, item) => acc + item.price * item.quantity, 0);
  }, [items]);

  const shipping = subtotal >= FREE_SHIPPING_THRESHOLD ? 0 : 3000;
  const total = subtotal + shipping;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!user) {
      addToast('error', 'Please log in to place an order.');
      router.push('/auth?redirect=/checkout');
      return;
    }

    if (!items || items.length === 0) return;

    if (!form.recipientName || !form.phone || !form.zipCode || !form.address1) {
      addToast('error', 'Please fill in all required fields.');
      return;
    }

    setIsSubmitting(true);
    try {
      // API integration will happen in B7. For now simulate success.
      await new Promise((resolve) => setTimeout(resolve, 1000));
      clearCart();
      router.push('/orders/order-001/confirmation');
    } catch {
      addToast('error', 'Something went wrong. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (items === undefined) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-16 md:px-6">
        <div className="animate-pulse space-y-4">
          <div className="h-8 w-40 rounded-md bg-muted" />
          <div className="h-64 rounded-md bg-muted" />
        </div>
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-16 md:px-6">
        <EmptyState
          title="Nothing to check out"
          description="Your cart is empty."
          action={
            <Link href="/products" className="text-sm font-medium text-primary underline underline-offset-4">
              Shop Products
            </Link>
          }
        />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 md:px-6">
      <h1 className="text-3xl font-bold text-foreground">Checkout</h1>

      <form onSubmit={handleSubmit} className="mt-8 grid grid-cols-1 gap-10 lg:grid-cols-3">
        <div className="space-y-8 lg:col-span-2">
          <section>
            <h2 className="text-lg font-bold text-foreground">Shipping Address</h2>
            <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label htmlFor="recipientName" className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Recipient Name *
                </label>
                <input
                  id="recipientName"
                  type="text"
                  required
                  value={form.recipientName}
                  onChange={(e) => setForm((f) => ({ ...f, recipientName: e.target.value }))}
                  className="w-full rounded-md border border-border px-3 py-2.5 text-sm text-foreground focus:border-primary focus:outline-none"
                  placeholder="Hong Gildong"
                />
              </div>

              <div>
                <label htmlFor="phone" className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Phone *
                </label>
                <input
                  id="phone"
                  type="tel"
                  required
                  value={form.phone}
                  onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))}
                  className="w-full rounded-md border border-border px-3 py-2.5 text-sm text-foreground focus:border-primary focus:outline-none"
                  placeholder="010-1234-5678"
                />
              </div>

              <div>
                <label htmlFor="zipCode" className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Zip Code *
                </label>
                <input
                  id="zipCode"
                  type="text"
                  required
                  value={form.zipCode}
                  onChange={(e) => setForm((f) => ({ ...f, zipCode: e.target.value }))}
                  className="w-full rounded-md border border-border px-3 py-2.5 text-sm text-foreground focus:border-primary focus:outline-none"
                  placeholder="06234"
                />
              </div>

              <div className="md:col-span-2">
                <label htmlFor="address1" className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Address *
                </label>
                <input
                  id="address1"
                  type="text"
                  required
                  value={form.address1}
                  onChange={(e) => setForm((f) => ({ ...f, address1: e.target.value }))}
                  className="w-full rounded-md border border-border px-3 py-2.5 text-sm text-foreground focus:border-primary focus:outline-none"
                  placeholder="서울특별시 강남구 강남대로 123"
                />
              </div>

              <div className="md:col-span-2">
                <label htmlFor="address2" className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Apartment / Suite (optional)
                </label>
                <input
                  id="address2"
                  type="text"
                  value={form.address2}
                  onChange={(e) => setForm((f) => ({ ...f, address2: e.target.value }))}
                  className="w-full rounded-md border border-border px-3 py-2.5 text-sm text-foreground focus:border-primary focus:outline-none"
                  placeholder="101호"
                />
              </div>
            </div>
          </section>

          <section className="border-t border-border pt-8">
            <h2 className="text-lg font-bold text-foreground">Payment Method</h2>
            <div className="mt-4 space-y-3">
              {PAYMENT_METHODS.map((method) => (
                <label
                  key={method.value}
                  className={`flex cursor-pointer items-center gap-3 rounded-md border p-4 transition-colors ${
                    paymentMethod === method.value
                      ? 'border-primary bg-primary-light'
                      : 'border-border hover:border-muted-foreground'
                  }`}
                >
                  <input
                    type="radio"
                    name="payment"
                    value={method.value}
                    checked={paymentMethod === method.value}
                    onChange={() => setPaymentMethod(method.value)}
                    className="accent-primary"
                  />
                  <span className="text-sm font-medium text-foreground">{method.label}</span>
                </label>
              ))}
            </div>
          </section>

          <div className="hidden lg:block">
            <button
              type="submit"
              disabled={isSubmitting}
              className="flex w-full items-center justify-center gap-2 rounded-md bg-primary px-6 py-3 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-60"
            >
              {isSubmitting ? (
                <>
                  <Loader2 size={16} className="animate-spin" />
                  Processing...
                </>
              ) : (
                `Place Order — ${formatKRW(total)}`
              )}
            </button>
          </div>
        </div>

        <div className="lg:col-span-1">
          <div className="sticky top-20 rounded-lg border border-border p-6">
            <h2 className="text-lg font-bold text-foreground">Order Summary</h2>
            <div className="mt-4 divide-y divide-border">
              {items.map((item) => (
                <div key={`${item.productId}-${item.variantId}`} className="flex gap-3 py-3">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-foreground">{item.productName}</p>
                    <p className="text-xs text-muted-foreground">
                      {item.brandName} · {item.size} · Qty {item.quantity}
                    </p>
                  </div>
                  <p className="shrink-0 text-sm font-medium text-foreground">
                    <PriceDisplay amount={item.price * item.quantity} />
                  </p>
                </div>
              ))}
            </div>

            <div className="mt-4 space-y-3 border-t border-border pt-4 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Subtotal</span>
                <span className="font-medium text-foreground">{formatKRW(subtotal)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Shipping</span>
                <span className="font-medium text-foreground">
                  {shipping === 0 ? 'Free' : formatKRW(shipping)}
                </span>
              </div>
              <div className="border-t border-border pt-3">
                <div className="flex justify-between">
                  <span className="font-semibold text-foreground">Total</span>
                  <span className="font-semibold text-foreground">{formatKRW(total)}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="fixed inset-x-0 bottom-0 z-50 border-t border-border bg-background p-3 lg:hidden">
          <button
            type="submit"
            disabled={isSubmitting}
            className="flex w-full items-center justify-center gap-2 rounded-md bg-primary px-6 py-3 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-60"
          >
            {isSubmitting ? (
              <>
                <Loader2 size={16} className="animate-spin" />
                Processing...
              </>
            ) : (
              `Place Order — ${formatKRW(total)}`
            )}
          </button>
        </div>
      </form>
    </div>
  );
}
