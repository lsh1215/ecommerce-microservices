'use client';

import { useMemo } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { Minus, Plus, Trash2, ShoppingBag } from 'lucide-react';
import { useCartStore } from '@/features/cart/store/cart-store';
import { PriceDisplay } from '@/components/shared/PriceDisplay';
import { formatKRW } from '@/utils/currency';
import { useFromStore } from '@/hooks/use-from-store';
import { EmptyState } from '@/components/shared/EmptyState';

export default function CartPage() {
  const items = useFromStore(useCartStore, (s) => s.items);
  const updateQuantity = useCartStore((s) => s.updateQuantity);
  const removeItem = useCartStore((s) => s.removeItem);

  const subtotal = useMemo(() => {
    if (!items) return 0;
    return items.reduce((acc, item) => acc + item.price * item.quantity, 0);
  }, [items]);

  const FREE_SHIPPING_THRESHOLD = 50000;
  const shipping = subtotal >= FREE_SHIPPING_THRESHOLD ? 0 : 3000;
  const total = subtotal + shipping;

  if (items === undefined) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-16 md:px-6">
        <div className="animate-pulse space-y-4">
          <div className="h-8 w-32 rounded-md bg-muted" />
          <div className="h-24 rounded-md bg-muted" />
          <div className="h-24 rounded-md bg-muted" />
        </div>
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-16 md:px-6">
        <EmptyState
          icon={<ShoppingBag size={48} strokeWidth={1} />}
          title="Your cart is empty"
          description="Browse our collection and find something you love."
          action={
            <Link
              href="/products"
              className="inline-block rounded-md bg-primary px-8 py-3 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
            >
              Shop Products
            </Link>
          }
        />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 md:px-6">
      <h1 className="text-3xl font-bold text-foreground">Cart</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        {items.length} {items.length === 1 ? 'item' : 'items'}
      </p>

      <div className="mt-8 grid grid-cols-1 gap-10 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <div className="divide-y divide-border">
            {items.map((item) => {
              const lowStock = (item.stockAvailable ?? Infinity) <= 3;

              return (
                <div key={`${item.productId}-${item.variantId}`} className="flex gap-4 py-6">
                  <Link
                    href={`/products/${item.productId}`}
                    className="relative h-28 w-20 shrink-0 overflow-hidden rounded-md bg-muted md:h-32 md:w-24"
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

                  <div className="flex min-w-0 flex-1 flex-col justify-between">
                    <div>
                      <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
                        {item.brandName}
                      </p>
                      <Link
                        href={`/products/${item.productId}`}
                        className="mt-0.5 text-sm font-medium text-foreground hover:underline"
                      >
                        {item.productName}
                      </Link>
                      <p className="mt-1 text-xs text-muted-foreground">
                        {item.size} / {item.color}
                      </p>
                      {lowStock && item.stockAvailable && (
                        <p className="mt-1 text-xs font-medium text-warning">
                          Only {item.stockAvailable} left
                        </p>
                      )}
                    </div>

                    <div className="mt-3 flex items-center gap-4">
                      <div className="flex items-center rounded-md border border-border">
                        <button
                          type="button"
                          onClick={() => updateQuantity(item.productId, item.variantId, item.quantity - 1)}
                          className="px-2 py-1 text-muted-foreground transition-colors hover:text-foreground"
                          aria-label="Decrease quantity"
                        >
                          <Minus size={14} />
                        </button>
                        <span className="min-w-[28px] text-center text-sm font-medium text-foreground">
                          {item.quantity}
                        </span>
                        <button
                          type="button"
                          onClick={() => updateQuantity(item.productId, item.variantId, item.quantity + 1)}
                          disabled={
                            item.stockAvailable != null && item.quantity >= item.stockAvailable
                          }
                          className="px-2 py-1 text-muted-foreground transition-colors hover:text-foreground disabled:opacity-40"
                          aria-label="Increase quantity"
                        >
                          <Plus size={14} />
                        </button>
                      </div>
                      <button
                        type="button"
                        onClick={() => removeItem(item.productId, item.variantId)}
                        className="text-muted-foreground transition-colors hover:text-destructive"
                        aria-label="Remove item"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>

                  <div className="shrink-0 text-right">
                    <p className="text-sm font-semibold text-foreground">
                      <PriceDisplay amount={item.price * item.quantity} />
                    </p>
                    {item.quantity > 1 && (
                      <p className="mt-0.5 text-xs text-muted-foreground">
                        <PriceDisplay amount={item.price} /> each
                      </p>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        <div className="lg:col-span-1">
          <div className="sticky top-20 rounded-lg border border-border p-6">
            <h2 className="text-lg font-bold text-foreground">Order Summary</h2>

            <div className="mt-6 space-y-3 text-sm">
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
              {shipping > 0 && (
                <p className="text-xs text-muted-foreground">
                  Free shipping on orders over {formatKRW(FREE_SHIPPING_THRESHOLD)}
                </p>
              )}
              <div className="border-t border-border pt-3">
                <div className="flex justify-between">
                  <span className="font-semibold text-foreground">Total</span>
                  <span className="font-semibold text-foreground">{formatKRW(total)}</span>
                </div>
              </div>
            </div>

            <div className="mt-6">
              <Link
                href="/checkout"
                className="flex w-full items-center justify-center rounded-md bg-primary px-6 py-3 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
              >
                Proceed to Checkout
              </Link>
            </div>

            <Link
              href="/products"
              className="mt-4 block text-center text-xs font-medium text-muted-foreground underline underline-offset-4 hover:text-foreground"
            >
              Continue Shopping
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
