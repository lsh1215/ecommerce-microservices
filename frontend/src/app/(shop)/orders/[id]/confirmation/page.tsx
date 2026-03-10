'use client';

import { use } from 'react';
import { notFound } from 'next/navigation';
import Image from 'next/image';
import Link from 'next/link';
import { Check, Package } from 'lucide-react';
import { CurrencyPrice } from '@/components/shared/CurrencyPrice';
import { useOrder } from '@/hooks/queries';

interface ConfirmationPageProps {
  params: Promise<{ id: string }>;
}

export default function OrderConfirmationPage({ params }: ConfirmationPageProps) {
  const { id } = use(params);
  const { data: order, isLoading, error } = useOrder(id);

  if (isLoading) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-16 md:px-6">
        <div className="flex flex-col items-center">
          <div className="h-16 w-16 animate-pulse bg-[#e8e4df]" />
          <div className="mt-6 h-8 w-64 animate-pulse bg-[#e8e4df]" />
          <div className="mt-4 h-4 w-48 animate-pulse bg-[#e8e4df]" />
        </div>
        <div className="mt-10 animate-pulse space-y-4">
          <div className="h-6 w-40 bg-[#e8e4df]" />
          <div className="h-24 bg-[#e8e4df]" />
        </div>
      </div>
    );
  }

  if (error || !order) {
    notFound();
  }

  const isDropPurchase = !!order.dropId;

  return (
    <div className="mx-auto max-w-2xl px-4 py-16 md:px-6">
      {/* Success icon */}
      <div className="flex flex-col items-center text-center">
        <div className="flex h-16 w-16 items-center justify-center bg-[#c4633e]">
          <Check size={32} strokeWidth={2} className="text-white" />
        </div>

        <h1 className="font-heading mt-6 text-3xl font-bold text-[#1a1a1a]">
          Order Placed Successfully
        </h1>

        {isDropPurchase && (
          <p className="mt-3 text-sm text-[#6b6560]">
            You secured{' '}
            <span className="font-semibold text-[#1a1a1a]">{order.items[0]?.productName}</span> from{' '}
            <span className="font-semibold text-[#1a1a1a]">{order.dropName}</span>
          </p>
        )}

        <p className="mt-2 text-sm text-[#6b6560]">Order #{order.id}</p>
      </div>

      {/* Order summary */}
      <div className="mt-10 border border-[#e8e4df] p-6">
        <h2 className="font-heading text-lg font-bold text-[#1a1a1a]">Order Summary</h2>

        <div className="mt-4 divide-y divide-[#e8e4df]">
          {order.items.map((item) => (
            <div key={`${item.productId}-${item.size}`} className="flex gap-4 py-4">
              <div className="relative h-20 w-16 shrink-0 overflow-hidden bg-[#e8e4df]">
                {item.imageUrl && (
                  <Image
                    src={item.imageUrl}
                    alt={item.productName}
                    fill
                    className="object-cover"
                    sizes="64px"
                  />
                )}
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-xs font-medium uppercase tracking-wider text-[#6b6560]">
                  {item.brandName}
                </p>
                <p className="mt-0.5 text-sm font-medium text-[#1a1a1a]">{item.productName}</p>
                <p className="mt-1 text-xs text-[#6b6560]">
                  Size {item.size} · Qty {item.quantity}
                </p>
              </div>
              <p className="shrink-0 text-sm font-semibold text-[#1a1a1a]">
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
        <div className="mt-4 space-y-2 border-t border-[#e8e4df] pt-4 text-sm">
          <div className="flex justify-between">
            <span className="text-[#6b6560]">Subtotal</span>
            <span className="font-medium text-[#1a1a1a]">
              <CurrencyPrice
                priceKrw={order.subtotalKrw}
                priceUsd={order.subtotalUsd}
                priceJpy={order.subtotalJpy}
              />
            </span>
          </div>
          {(order.dutyKrw > 0 || order.dutyUsd > 0) && (
            <div className="flex justify-between">
              <span className="text-[#6b6560]">Duty</span>
              <span className="font-medium text-[#1a1a1a]">
                <CurrencyPrice
                  priceKrw={order.dutyKrw}
                  priceUsd={order.dutyUsd}
                  priceJpy={order.dutyJpy}
                />
              </span>
            </div>
          )}
          <div className="border-t border-[#e8e4df] pt-2">
            <div className="flex justify-between">
              <span className="font-semibold text-[#1a1a1a]">Total</span>
              <span className="font-semibold text-[#1a1a1a]">
                <CurrencyPrice
                  priceKrw={order.totalKrw}
                  priceUsd={order.totalUsd}
                  priceJpy={order.totalJpy}
                />
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Processing info */}
      <div className="mt-6 flex items-start gap-3 border border-[#e8e4df] bg-[#f3f0eb] p-4">
        <Package size={18} strokeWidth={1.5} className="mt-0.5 shrink-0 text-[#6b6560]" />
        <div>
          <p className="text-sm font-medium text-[#1a1a1a]">Estimated Processing</p>
          <p className="mt-0.5 text-xs text-[#6b6560]">
            Your order will be processed within 3-5 business days. You will receive a shipping
            confirmation email with tracking information.
          </p>
        </div>
      </div>

      {/* CTAs */}
      <div className="mt-8 flex flex-col gap-3 sm:flex-row">
        <Link
          href={`/orders`}
          className="flex flex-1 items-center justify-center border border-[#1a1a1a] px-6 py-3 text-sm font-semibold uppercase tracking-widest text-[#1a1a1a] transition-colors hover:bg-[#1a1a1a] hover:text-white"
        >
          View Orders
        </Link>
        <Link
          href="/"
          className="flex flex-1 items-center justify-center bg-[#c4633e] px-6 py-3 text-sm font-semibold uppercase tracking-widest text-white transition-colors hover:bg-[#a84f2e]"
        >
          Continue Shopping
        </Link>
      </div>
    </div>
  );
}
