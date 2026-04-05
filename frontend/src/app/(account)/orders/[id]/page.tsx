'use client';

import { use } from 'react';
import Link from 'next/link';
import { notFound, redirect } from 'next/navigation';
import { useAuthStore } from '@/features/auth/store/auth-store';
import { useFromStore } from '@/hooks/use-from-store';
import { PriceDisplay } from '@/components/shared/PriceDisplay';
import { Skeleton } from '@/components/shared/Skeleton';
import { OrderCancelButton } from './OrderCancelButton';
import { useOrder } from '@/hooks/queries/use-orders';
import type { OrderStatus } from '@/types';

interface OrderDetailPageProps {
  params: Promise<{ id: string }>;
}

const STATUS_CONFIG: Record<OrderStatus, { label: string; classes: string }> = {
  PENDING: { label: 'Pending', classes: 'bg-yellow-50 text-yellow-700 border border-yellow-200' },
  CONFIRMED: { label: 'Confirmed', classes: 'bg-blue-50 text-blue-700 border border-blue-200' },
  PAID: { label: 'Paid', classes: 'bg-blue-50 text-blue-700 border border-blue-200' },
  SHIPPING: { label: 'Shipping', classes: 'bg-purple-50 text-purple-700 border border-purple-200' },
  DELIVERED: { label: 'Delivered', classes: 'bg-green-50 text-green-700 border border-green-200' },
  CANCELLED: { label: 'Cancelled', classes: 'bg-gray-100 text-gray-500 border border-gray-200' },
};

const STATUS_TIMELINE: OrderStatus[] = ['PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED'];

export default function OrderDetailPage({ params }: OrderDetailPageProps) {
  const { id } = use(params);
  const user = useFromStore(useAuthStore, (s) => s.user);
  const query = useOrder(id);

  if (user === undefined) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }

  if (user === null) {
    redirect('/auth?redirect=/account/orders');
  }

  if (query.isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }

  if (query.isError || !query.data) return notFound();

  const order = query.data;
  const statusCfg = STATUS_CONFIG[order.status];
  const isCancelled = order.status === 'CANCELLED';
  const currentStepIdx = isCancelled ? -1 : STATUS_TIMELINE.indexOf(order.status);
  const canCancel = order.status === 'PENDING' || order.status === 'CONFIRMED';

  return (
    <div>
      <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <Link
            href="/account/orders"
            className="mb-2 inline-block text-xs font-medium text-primary underline underline-offset-4"
          >
            &larr; All Orders
          </Link>
          <h1 className="text-2xl font-bold text-foreground md:text-3xl">{order.orderNumber}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Placed{' '}
            {new Date(order.createdAt).toLocaleDateString('ko-KR', {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
            })}
          </p>
        </div>
        <span
          className={`inline-flex rounded-full px-3 py-1 text-xs font-medium ${statusCfg.classes}`}
        >
          {statusCfg.label}
        </span>
      </div>

      {!isCancelled && (
        <section className="mb-10">
          <h2 className="mb-4 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            Order Status
          </h2>
          <div className="flex items-center">
            {STATUS_TIMELINE.map((step, idx) => {
              const isCompleted = idx <= currentStepIdx;
              const isCurrent = idx === currentStepIdx;
              const cfg = STATUS_CONFIG[step];

              return (
                <div key={step} className="flex flex-1 items-center">
                  <div className="flex flex-col items-center gap-1.5">
                    <div
                      className={`flex h-8 w-8 items-center justify-center rounded-full text-xs font-bold transition-colors ${
                        isCompleted
                          ? 'bg-primary text-primary-foreground'
                          : 'border border-border text-muted-foreground'
                      } ${isCurrent ? 'ring-2 ring-primary ring-offset-2' : ''}`}
                    >
                      {idx + 1}
                    </div>
                    <p
                      className={`text-xs ${
                        isCompleted ? 'font-medium text-foreground' : 'text-muted-foreground'
                      }`}
                    >
                      {cfg.label}
                    </p>
                  </div>
                  {idx < STATUS_TIMELINE.length - 1 && (
                    <div
                      className={`mx-1 h-0.5 flex-1 ${
                        idx < currentStepIdx ? 'bg-primary' : 'bg-border'
                      }`}
                    />
                  )}
                </div>
              );
            })}
          </div>
        </section>
      )}

      <section className="mb-10">
        <h2 className="mb-4 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          Items
        </h2>
        <div className="divide-y divide-border rounded-lg border border-border">
          {order.items.map((item, idx) => (
            <div key={`${item.productId}-${idx}`} className="flex gap-4 p-4">
              <div className="flex min-w-0 flex-1 flex-col justify-center gap-1">
                {item.brandName && (
                  <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
                    {item.brandName}
                  </p>
                )}
                <p className="text-sm font-medium text-foreground">{item.productName}</p>
                <p className="text-xs text-muted-foreground">
                  {[item.size, item.color].filter(Boolean).join(' / ')} · Qty: {item.quantity}
                </p>
              </div>
              <div className="flex items-center">
                <p className="text-sm font-semibold text-foreground">
                  <PriceDisplay amount={item.totalPrice} />
                </p>
              </div>
            </div>
          ))}
        </div>
      </section>

      <div className="grid grid-cols-1 gap-8 md:grid-cols-2">
        <section>
          <h2 className="mb-4 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            Shipping Address
          </h2>
          <div className="rounded-lg border border-border p-5">
            <p className="text-sm font-medium text-foreground">
              {order.shippingAddress.recipientName}
            </p>
            <p className="mt-1 text-sm text-muted-foreground">{order.shippingAddress.phone}</p>
            <p className="mt-2 text-sm text-muted-foreground">
              ({order.shippingAddress.zipCode}) {order.shippingAddress.address1}
              {order.shippingAddress.address2 && `, ${order.shippingAddress.address2}`}
            </p>
          </div>
        </section>

        <section>
          <h2 className="mb-4 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            Payment Summary
          </h2>
          <div className="rounded-lg border border-border p-5">
            <div className="flex flex-col gap-2">
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">Subtotal</span>
                <span className="text-foreground">
                  <PriceDisplay amount={order.totalAmount} />
                </span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">Shipping</span>
                <span className="text-foreground">Free</span>
              </div>
              <div className="mt-2 flex justify-between border-t border-border pt-3 text-sm font-semibold">
                <span className="text-foreground">Total</span>
                <span className="text-foreground">
                  <PriceDisplay amount={order.totalAmount} />
                </span>
              </div>
            </div>
          </div>
        </section>
      </div>

      {canCancel && (
        <div className="mt-10">
          <OrderCancelButton orderId={order.id} />
        </div>
      )}
    </div>
  );
}
