'use client';

import { redirect, useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { useAuthStore } from '@/features/auth/store/auth-store';
import { useFromStore } from '@/hooks/use-from-store';
import { PriceDisplay } from '@/components/shared/PriceDisplay';
import { Skeleton } from '@/components/shared/Skeleton';
import { EmptyState } from '@/components/shared/EmptyState';
import { useMyOrders } from '@/hooks/queries/use-orders';
import type { OrderStatus } from '@/types';
import { Package } from 'lucide-react';

const STATUS_CONFIG: Record<OrderStatus, { label: string; classes: string }> = {
  PENDING: { label: 'Pending', classes: 'bg-yellow-50 text-yellow-700 border border-yellow-200' },
  CONFIRMED: { label: 'Confirmed', classes: 'bg-blue-50 text-blue-700 border border-blue-200' },
  PAID: { label: 'Paid', classes: 'bg-blue-50 text-blue-700 border border-blue-200' },
  SHIPPING: { label: 'Shipping', classes: 'bg-purple-50 text-purple-700 border border-purple-200' },
  DELIVERED: { label: 'Delivered', classes: 'bg-green-50 text-green-700 border border-green-200' },
  CANCELLED: { label: 'Cancelled', classes: 'bg-gray-100 text-gray-500 border border-gray-200' },
};

const PAGE_SIZE = 10;

function OrderListSkeleton() {
  return (
    <div className="flex flex-col gap-4">
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="rounded-lg border border-border p-5">
          <div className="flex items-start gap-4">
            <div className="flex-1 space-y-2">
              <Skeleton className="h-3 w-24" />
              <Skeleton className="h-4 w-40" />
              <Skeleton className="h-3 w-20" />
            </div>
            <div className="space-y-2">
              <Skeleton className="h-5 w-20" />
              <Skeleton className="h-4 w-16" />
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

export default function OrdersPage() {
  const user = useFromStore(useAuthStore, (s) => s.user);
  const router = useRouter();
  const searchParams = useSearchParams();
  const currentPage = Math.max(0, parseInt(searchParams.get('page') ?? '0', 10));

  const customerId = user ? Number(user.id) : null;
  const query = useMyOrders(
    customerId != null && Number.isFinite(customerId)
      ? { customerId, page: currentPage, size: PAGE_SIZE }
      : { customerId: -1, page: 0, size: PAGE_SIZE },
  );

  if (user === undefined) {
    return (
      <div>
        <h1 className="mb-8 text-3xl font-bold text-foreground">Orders</h1>
        <OrderListSkeleton />
      </div>
    );
  }

  if (user === null) {
    redirect('/auth?redirect=/account/orders');
  }

  if (query.isLoading) {
    return (
      <div>
        <h1 className="mb-8 text-3xl font-bold text-foreground">Orders</h1>
        <OrderListSkeleton />
      </div>
    );
  }

  if (query.isError) {
    return (
      <div>
        <h1 className="mb-8 text-3xl font-bold text-foreground">Orders</h1>
        <div className="rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          Failed to load orders. Please try again.
        </div>
      </div>
    );
  }

  const pageData = query.data;
  const orders = pageData?.content ?? [];
  const totalPages = pageData?.totalPages ?? 1;

  if (orders.length === 0) {
    return (
      <div>
        <h1 className="mb-8 text-3xl font-bold text-foreground">Orders</h1>
        <EmptyState
          icon={<Package size={40} />}
          title="No orders yet"
          description="You have not placed any orders yet."
          action={
            <Link
              href="/products"
              className="text-sm font-medium text-primary underline underline-offset-4"
            >
              Start browsing
            </Link>
          }
        />
      </div>
    );
  }

  return (
    <div>
      <h1 className="mb-8 text-3xl font-bold text-foreground">Orders</h1>

      <div className="flex flex-col gap-4">
        {orders.map((order) => {
          const statusCfg = STATUS_CONFIG[order.status];
          const itemCount = order.items.reduce((sum, i) => sum + i.quantity, 0);

          return (
            <Link
              key={order.id}
              href={`/account/orders/${order.id}`}
              className="group rounded-lg border border-border p-5 transition-colors hover:border-primary"
            >
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <p className="text-xs font-medium text-muted-foreground">
                    {new Date(order.createdAt).toLocaleDateString('ko-KR', {
                      year: 'numeric',
                      month: 'long',
                      day: 'numeric',
                    })}
                  </p>
                  <p className="mt-0.5 text-sm font-medium text-foreground">{order.orderNumber}</p>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    {itemCount} {itemCount === 1 ? 'item' : 'items'}
                  </p>
                </div>

                <div className="flex flex-col items-end gap-2">
                  <span
                    className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${statusCfg.classes}`}
                  >
                    {statusCfg.label}
                  </span>
                  <p className="text-sm font-semibold text-foreground">
                    <PriceDisplay amount={order.totalAmount} />
                  </p>
                </div>
              </div>
            </Link>
          );
        })}
      </div>

      {totalPages > 1 && (
        <div className="mt-8 flex items-center justify-center gap-2">
          <button
            type="button"
            disabled={currentPage === 0}
            onClick={() => router.push(`/account/orders?page=${currentPage - 1}`)}
            className="rounded-md border border-border px-3 py-1.5 text-sm font-medium text-foreground transition-colors hover:bg-muted disabled:opacity-40"
          >
            Previous
          </button>
          <span className="text-sm text-muted-foreground">
            Page {currentPage + 1} of {totalPages}
          </span>
          <button
            type="button"
            disabled={currentPage >= totalPages - 1}
            onClick={() => router.push(`/account/orders?page=${currentPage + 1}`)}
            className="rounded-md border border-border px-3 py-1.5 text-sm font-medium text-foreground transition-colors hover:bg-muted disabled:opacity-40"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}
