import { notFound } from 'next/navigation';
import Link from 'next/link';
import { Landmark, Package } from 'lucide-react';
import { PriceDisplay } from '@/components/shared/PriceDisplay';
import { serverFetch } from '@/lib/server-fetch';
import { mapOrderResponse } from '@/lib/mappers';
import type { OrderResponse } from '@/types/api-responses';
import { CopyButton } from './CopyButton';

interface ConfirmationPageProps {
  params: Promise<{ id: string }>;
}

function formatDeadline(iso: string | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default async function OrderConfirmationPage({ params }: ConfirmationPageProps) {
  const { id } = await params;

  const orderData = await serverFetch<OrderResponse>('order', `/api/orders/${id}`);
  if (!orderData) return notFound();
  const order = mapOrderResponse(orderData);

  const va = order.virtualAccount;
  const deadlineIso = va?.expiresAt ?? order.expiresAt;
  const deadlineLabel = formatDeadline(deadlineIso);
  const isExpired =
    !!deadlineIso && order.status === 'PENDING' && new Date(deadlineIso).getTime() < Date.now();

  return (
    <div className="mx-auto max-w-2xl px-4 py-16 md:px-6">
      <div className="flex flex-col items-center text-center">
        <div className="flex h-16 w-16 items-center justify-center rounded-full bg-primary/10">
          <Landmark size={32} strokeWidth={1.75} className="text-primary" />
        </div>

        <h1 className="mt-6 text-3xl font-bold text-foreground">
          {isExpired ? 'Deposit Deadline Expired' : 'Awaiting Deposit'}
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">{order.orderNumber}</p>
        {!isExpired && (
          <p className="mt-3 max-w-md text-sm text-muted-foreground">
            Please transfer the exact amount to the virtual account below. Your order will be
            confirmed once the deposit is received.
          </p>
        )}
        {isExpired && (
          <p className="mt-3 max-w-md text-sm text-destructive">
            The deposit window for this virtual account has closed. Please place a new order to
            receive a fresh account.
          </p>
        )}
      </div>

      {va && (
        <div
          className={`mt-10 rounded-lg border p-6 ${
            isExpired ? 'border-destructive/30 bg-destructive/5' : 'border-primary/30 bg-primary/5'
          }`}
        >
          <h2 className="text-lg font-bold text-foreground">Deposit Instructions</h2>

          <div className="mt-4 space-y-3 text-sm">
            <div className="flex items-center justify-between gap-4">
              <span className="text-muted-foreground">Bank</span>
              <span className="font-medium text-foreground">{va.bank}</span>
            </div>

            <div className="flex items-center justify-between gap-4">
              <span className="text-muted-foreground">Account Number</span>
              <span className="flex items-center gap-2">
                <span className="font-mono text-sm font-semibold text-foreground">
                  {va.accountNumber}
                </span>
                <CopyButton value={va.accountNumber} label="Copy account number" />
              </span>
            </div>

            <div className="flex items-center justify-between gap-4">
              <span className="text-muted-foreground">Account Holder</span>
              <span className="font-medium text-foreground">{va.holderName}</span>
            </div>

            <div className="flex items-center justify-between gap-4">
              <span className="text-muted-foreground">Amount</span>
              <span className="text-base font-semibold text-foreground">
                <PriceDisplay amount={va.amount} />
              </span>
            </div>

            {deadlineLabel && (
              <div className="flex items-center justify-between gap-4 border-t border-border pt-3">
                <span className="text-muted-foreground">Deposit By</span>
                <span
                  className={`font-medium ${isExpired ? 'text-destructive' : 'text-foreground'}`}
                >
                  {deadlineLabel}
                </span>
              </div>
            )}
          </div>
        </div>
      )}

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
            Once your deposit is received, the order is confirmed and shipped within 1-3 business
            days. You will receive a shipping confirmation with tracking information by email.
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
