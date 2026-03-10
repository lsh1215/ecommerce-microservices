'use client';

import { useMemo } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { Minus, Plus, Trash2, ShoppingBag, AlertTriangle } from 'lucide-react';
import { useCartStore } from '@/features/cart/store/cart-store';
import { CurrencyPrice } from '@/components/shared/CurrencyPrice';
import { useCurrencyStore } from '@/stores/currency-store';
import { formatPrice, getPrice } from '@/utils/currency';
import { useFromStore } from '@/hooks/use-from-store';

const DUTY_RATE = 0.08;

export default function CartPage() {
  const items = useFromStore(useCartStore, (s) => s.items);
  const updateQuantity = useCartStore((s) => s.updateQuantity);
  const removeItem = useCartStore((s) => s.removeItem);
  const currency = useCurrencyStore((s) => s.currency);

  const enrichedItems = useMemo(() => {
    if (!items) return [];
    return items.map((item) => {
      const currentStock = item.stockAvailable ?? Infinity;
      const soldOut = currentStock === 0;
      const lowStock = !soldOut && currentStock <= 3 && currentStock < Infinity;
      return { ...item, currentStock, soldOut, lowStock };
    });
  }, [items]);

  const hasSoldOutItems = enrichedItems.some((i) => i.soldOut);

  const subtotal = useMemo(() => {
    return enrichedItems.reduce(
      (acc, item) => ({
        priceKrw: acc.priceKrw + item.priceKrw * item.quantity,
        priceUsd: acc.priceUsd + item.priceUsd * item.quantity,
        priceJpy: acc.priceJpy + item.priceJpy * item.quantity,
      }),
      { priceKrw: 0, priceUsd: 0, priceJpy: 0 },
    );
  }, [enrichedItems]);

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

  if (items === undefined) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-16 md:px-6">
        <div className="animate-pulse space-y-4">
          <div className="h-8 w-32 bg-[#e8e4df]" />
          <div className="h-24 bg-[#e8e4df]" />
          <div className="h-24 bg-[#e8e4df]" />
        </div>
      </div>
    );
  }

  if (enrichedItems.length === 0) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-24 text-center md:px-6">
        <ShoppingBag size={48} strokeWidth={1} className="mx-auto text-[#a39e93]" />
        <h1 className="font-heading mt-6 text-2xl font-bold text-[#1a1a1a]">Your cart is empty</h1>
        <p className="mt-2 text-sm text-[#6b6560]">
          Browse our collection and add some heritage pieces.
        </p>
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
      <h1 className="font-heading text-3xl font-bold text-[#1a1a1a]">Cart</h1>
      <p className="mt-1 text-sm text-[#6b6560]">
        {enrichedItems.length} {enrichedItems.length === 1 ? 'item' : 'items'}
      </p>

      {/* Drop timers */}
      {enrichedItems.some((i) => i.dropName) && (
        <div className="mt-6 space-y-2">
          <p className="flex items-center gap-2 text-xs text-[#6b6560]">
            <AlertTriangle size={14} className="text-[#c4633e]" />
            Items are not reserved until checkout is complete.
          </p>
        </div>
      )}

      <div className="mt-8 grid grid-cols-1 gap-10 lg:grid-cols-3">
        {/* Cart items */}
        <div className="lg:col-span-2">
          <div className="divide-y divide-[#e8e4df]">
            {enrichedItems.map((item) => (
              <div
                key={`${item.productId}-${item.size}`}
                className={`flex gap-4 py-6 ${item.soldOut ? 'opacity-60' : ''}`}
              >
                {/* Image */}
                <Link
                  href={`/products/${item.productId}`}
                  className="relative h-28 w-20 shrink-0 overflow-hidden bg-[#e8e4df] md:h-32 md:w-24"
                >
                  {item.imageUrl && (
                    <Image
                      src={item.imageUrl}
                      alt={item.productName}
                      fill
                      className="object-cover"
                      sizes="96px"
                    />
                  )}
                </Link>

                {/* Info */}
                <div className="flex min-w-0 flex-1 flex-col justify-between">
                  <div>
                    <p className="text-xs font-medium uppercase tracking-wider text-[#6b6560]">
                      {item.brandName}
                    </p>
                    <Link
                      href={`/products/${item.productId}`}
                      className="mt-0.5 text-sm font-medium text-[#1a1a1a] hover:underline"
                    >
                      {item.productName}
                    </Link>
                    <p className="mt-1 text-xs text-[#6b6560]">Size: {item.size}</p>

                    {/* Stock warnings */}
                    {item.soldOut && (
                      <p className="mt-2 text-xs font-medium text-red-600">
                        Sold Out — remove from cart
                      </p>
                    )}
                    {item.lowStock && (
                      <p className="mt-2 text-xs font-medium text-[#c4633e]">
                        Only {item.currentStock} left
                      </p>
                    )}
                  </div>

                  {/* Qty + remove */}
                  <div className="mt-3 flex items-center gap-4">
                    {!item.soldOut && (
                      <div className="flex items-center border border-[#e8e4df]">
                        <button
                          type="button"
                          onClick={() =>
                            updateQuantity(item.productId, item.size, item.quantity - 1)
                          }
                          className="px-2 py-1 text-[#6b6560] hover:text-[#1a1a1a]"
                          aria-label="Decrease quantity"
                        >
                          <Minus size={14} />
                        </button>
                        <span className="min-w-[28px] text-center text-sm font-medium text-[#1a1a1a]">
                          {item.quantity}
                        </span>
                        <button
                          type="button"
                          onClick={() =>
                            updateQuantity(item.productId, item.size, item.quantity + 1)
                          }
                          disabled={
                            item.currentStock < Infinity && item.quantity >= item.currentStock
                          }
                          className="px-2 py-1 text-[#6b6560] hover:text-[#1a1a1a] disabled:opacity-40"
                          aria-label="Increase quantity"
                        >
                          <Plus size={14} />
                        </button>
                      </div>
                    )}
                    <button
                      type="button"
                      onClick={() => removeItem(item.productId, item.size)}
                      className="text-xs text-[#6b6560] hover:text-red-600"
                      aria-label="Remove item"
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                </div>

                {/* Price */}
                <div className="shrink-0 text-right">
                  <p className="text-sm font-semibold text-[#1a1a1a]">
                    <CurrencyPrice
                      priceKrw={item.priceKrw * item.quantity}
                      priceUsd={item.priceUsd * item.quantity}
                      priceJpy={item.priceJpy * item.quantity}
                    />
                  </p>
                  {item.quantity > 1 && (
                    <p className="mt-0.5 text-xs text-[#6b6560]">
                      <CurrencyPrice
                        priceKrw={item.priceKrw}
                        priceUsd={item.priceUsd}
                        priceJpy={item.priceJpy}
                      />{' '}
                      each
                    </p>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Order summary */}
        <div className="lg:col-span-1">
          <div className="sticky top-20 border border-[#e8e4df] p-6">
            <h2 className="font-heading text-lg font-bold text-[#1a1a1a]">Order Summary</h2>

            <div className="mt-6 space-y-3 text-sm">
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

            {/* Checkout CTA */}
            <div className="mt-6">
              {hasSoldOutItems ? (
                <button
                  type="button"
                  disabled
                  className="flex w-full items-center justify-center gap-2 bg-[#e8e4df] px-6 py-4 text-sm font-semibold uppercase tracking-widest text-[#6b6560]"
                >
                  Remove Sold Out Items
                </button>
              ) : (
                <Link
                  href="/checkout"
                  className="flex w-full items-center justify-center gap-2 bg-[#c4633e] px-6 py-4 text-sm font-semibold uppercase tracking-widest text-white transition-colors hover:bg-[#a84f2e]"
                >
                  Proceed to Checkout
                </Link>
              )}
            </div>

            <Link
              href="/products"
              className="mt-4 block text-center text-xs font-medium text-[#6b6560] underline underline-offset-4 hover:text-[#1a1a1a]"
            >
              Continue Shopping
            </Link>
          </div>
        </div>
      </div>

      {/* Mobile sticky checkout */}
      <div className="fixed inset-x-0 bottom-0 z-50 border-t border-[#e8e4df] bg-[#faf9f6] p-3 lg:hidden">
        <div className="flex items-center justify-between gap-4">
          <div>
            <p className="text-xs text-[#6b6560]">Total</p>
            <p className="text-lg font-semibold text-[#1a1a1a]">
              {formatPrice(getPrice(total, currency), currency)}
            </p>
          </div>
          {hasSoldOutItems ? (
            <button
              type="button"
              disabled
              className="bg-[#e8e4df] px-6 py-3 text-sm font-semibold uppercase tracking-widest text-[#6b6560]"
            >
              Remove Sold Out Items
            </button>
          ) : (
            <Link
              href="/checkout"
              className="bg-[#c4633e] px-6 py-3 text-sm font-semibold uppercase tracking-widest text-white transition-colors hover:bg-[#a84f2e]"
            >
              Checkout
            </Link>
          )}
        </div>
      </div>
    </div>
  );
}
