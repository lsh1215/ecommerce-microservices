import { notFound } from 'next/navigation';
import Link from 'next/link';
import { CreditCard, Package } from 'lucide-react';
import { PriceDisplay } from '@/components/shared/PriceDisplay';
import { serverFetch } from '@/lib/server-fetch';
import { mapOrderResponse } from '@/lib/mappers';
import type { OrderResponse } from '@/types/api-responses';

interface ConfirmationPageProps {
  params: Promise<{ id: string }>;
}

export default async function OrderConfirmationPage({ params }: ConfirmationPageProps) {
  const { id } = await params;

  const orderData = await serverFetch<OrderResponse>('order', `/api/orders/${id}`);
  if (!orderData) return notFound();
  const order = mapOrderResponse(orderData);

  return (
    <div className="mx-auto max-w-2xl px-4 py-16 md:px-6">
      <div className="flex flex-col items-center text-center">
        <div className="flex h-16 w-16 items-center justify-center rounded-full bg-primary/10">
          <CreditCard size={32} strokeWidth={1.75} className="text-primary" />
        </div>

        <h1 className="mt-6 text-3xl font-bold text-foreground">Order Received</h1>
        <p className="mt-2 text-sm text-muted-foreground">{order.orderNumber}</p>
        <p className="mt-3 max-w-md text-sm text-muted-foreground">
          Payment authorization has been requested through the PG workflow. We will update the order
          status after the payment processor records the result.
        </p>
      </div>

      <div className="mt-10 rounded-lg border border-primary/30 bg-primary/5 p-6">
        <h2 className="text-lg font-bold text-foreground">Payment Processing</h2>

        <div className="mt-4 space-y-3 text-sm">
          <div className="flex items-center justify-between gap-4">
            <span className="text-muted-foreground">Method</span>
            <span className="font-medium text-foreground">Card / PG Authorization</span>
          </div>

          <div className="flex items-center justify-between gap-4">
            <span className="text-muted-foreground">Order Status</span>
            <span className="font-medium text-foreground">{order.status}</span>
          </div>

          <div className="flex items-center justify-between gap-4 border-t border-border pt-3">
            <span className="text-muted-foreground">Amount</span>
            <span className="text-base font-semibold text-foreground">
              <PriceDisplay amount={order.totalAmount} />
            </span>
          </div>
        </div>
      </div>

      <div className="mt-6 rounded-lg border border-border p-6">
        <h2 className="text-lg font-bold text-foreground">Order Summary</h2>

        <div className="mt-4 divide-y divide-border">
          {order.items.map((item, idx) => (
            <div key={`${item.productId}-${idx}`} className="flex gap-4 py-4">
              <div className="min-w-0 flex-1">
                {item.brandName && (
                  <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
                    {item.brandName}
                  </p>
                )}
                <p className="mt-0.5 text-sm font-medium text-foreground">{item.productName}</p>
                <p className="mt-1 text-xs text-muted-foreground">
                  {[item.size, item.color].filter(Boolean).join(' / ')} · Qty {item.quantity}
                </p>
              </div>
              <p className="shrink-0 text-sm font-semibold text-foreground">
                <PriceDisplay amount={item.totalPrice} />
              </p>
            </div>
          ))}
        </div>

        <div className="mt-4 space-y-2 border-t border-border pt-4 text-sm">
          <div className="flex justify-between">
            <span className="text-muted-foreground">Subtotal</span>
            <span className="font-medium text-foreground">
              <PriceDisplay amount={order.totalAmount} />
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Shipping</span>
            <span className="font-medium text-foreground">Free</span>
          </div>
          <div className="border-t border-border pt-2">
            <div className="flex justify-between">
              <span className="font-semibold text-foreground">Total</span>
              <span className="font-semibold text-foreground">
                <PriceDisplay amount={order.totalAmount} />
              </span>
            </div>
          </div>
        </div>
      </div>

      <div className="mt-6 flex items-start gap-3 rounded-lg border border-border bg-surface p-4">
        <Package size={18} strokeWidth={1.5} className="mt-0.5 shrink-0 text-muted-foreground" />
        <div>
          <p className="text-sm font-medium text-foreground">What happens next?</p>
          <p className="mt-0.5 text-xs text-muted-foreground">
            Once payment authorization completes, the order is confirmed and prepared for shipment.
            You will receive a shipping confirmation with tracking information by email.
          </p>
        </div>
      </div>

      <div className="mt-8 flex flex-col gap-3 sm:flex-row">
        <Link
          href="/account/orders"
          className="flex flex-1 items-center justify-center rounded-md border border-foreground px-6 py-3 text-sm font-semibold text-foreground transition-colors hover:bg-foreground hover:text-background"
        >
          View Orders
        </Link>
        <Link
          href="/"
          className="flex flex-1 items-center justify-center rounded-md bg-primary px-6 py-3 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
        >
          Continue Shopping
        </Link>
      </div>
    </div>
  );
}
