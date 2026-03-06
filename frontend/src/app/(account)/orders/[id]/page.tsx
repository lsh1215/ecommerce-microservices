import Link from 'next/link';
import Image from 'next/image';
import { notFound } from 'next/navigation';
import { getOrderById } from '@/mocks/orders';
import { CurrencyPrice } from '@/components/shared/CurrencyPrice';
import { OrderCancelButton } from './OrderCancelButton';
import type { OrderStatus } from '@/types';

interface OrderDetailPageProps {
  params: Promise<{ id: string }>;
}

const STATUS_CONFIG: Record<OrderStatus, { label: string; classes: string }> = {
  PENDING: { label: 'Pending', classes: 'bg-yellow-50 text-yellow-700 border border-yellow-200' },
  CONFIRMED: { label: 'Confirmed', classes: 'bg-blue-50 text-blue-700 border border-blue-200' },
  SHIPPED: { label: 'Shipped', classes: 'bg-purple-50 text-purple-700 border border-purple-200' },
  DELIVERED: { label: 'Delivered', classes: 'bg-green-50 text-green-700 border border-green-200' },
  CANCELLED: { label: 'Cancelled', classes: 'bg-gray-100 text-gray-500 border border-gray-200' },
};

const STATUS_TIMELINE: OrderStatus[] = ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED'];

export async function generateMetadata({ params }: OrderDetailPageProps) {
  const { id } = await params;
  const order = getOrderById(id);
  if (!order) return { title: 'Order Not Found — FOUNDRY' };
  return { title: `Order ${order.id} — FOUNDRY` };
}

export default async function OrderDetailPage({ params }: OrderDetailPageProps) {
  const { id } = await params;
  const order = getOrderById(id);

  if (!order) notFound();

  const statusCfg = STATUS_CONFIG[order.status];
  const isCancelled = order.status === 'CANCELLED';
  const currentStepIdx = isCancelled ? -1 : STATUS_TIMELINE.indexOf(order.status);
  const canCancel = order.status === 'PENDING' || order.status === 'CONFIRMED';

  return (
    <div>
      {/* Header */}
      <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <Link
            href="/orders"
            className="mb-2 inline-block text-xs font-medium text-[#c4633e] underline underline-offset-4"
          >
            &larr; All Orders
          </Link>
          <h1 className="font-heading text-2xl font-bold text-[#1a1a1a] md:text-3xl">
            Order {order.id}
          </h1>
          <p className="mt-1 text-sm text-[#6b6560]">
            Placed{' '}
            {new Date(order.createdAt).toLocaleDateString('en-US', {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
            })}
          </p>
        </div>
        <span
          className={`inline-flex px-3 py-1 text-xs font-medium uppercase tracking-wide ${statusCfg.classes}`}
        >
          {statusCfg.label}
        </span>
      </div>

      {/* Status Timeline */}
      {!isCancelled && (
        <section className="mb-10">
          <h2 className="mb-4 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
            Order Status
          </h2>
          <div className="flex items-center gap-0">
            {STATUS_TIMELINE.map((step, idx) => {
              const isCompleted = idx <= currentStepIdx;
              const isCurrent = idx === currentStepIdx;
              const cfg = STATUS_CONFIG[step];

              return (
                <div key={step} className="flex flex-1 items-center">
                  <div className="flex flex-col items-center gap-1.5">
                    <div
                      className={`flex h-8 w-8 items-center justify-center text-xs font-bold ${
                        isCompleted
                          ? 'bg-[#1a1a1a] text-white'
                          : 'border border-[#e8e4df] text-[#a39e93]'
                      } ${isCurrent ? 'ring-2 ring-[#c4633e] ring-offset-2' : ''}`}
                    >
                      {idx + 1}
                    </div>
                    <p
                      className={`text-xs ${
                        isCompleted ? 'font-medium text-[#1a1a1a]' : 'text-[#a39e93]'
                      }`}
                    >
                      {cfg.label}
                    </p>
                  </div>
                  {idx < STATUS_TIMELINE.length - 1 && (
                    <div
                      className={`mx-1 h-0.5 flex-1 ${
                        idx < currentStepIdx ? 'bg-[#1a1a1a]' : 'bg-[#e8e4df]'
                      }`}
                    />
                  )}
                </div>
              );
            })}
          </div>
        </section>
      )}

      {isCancelled && (
        <section className="mb-10 border border-gray-200 bg-gray-50 p-5">
          <p className="text-sm font-medium text-gray-600">
            This order was cancelled on{' '}
            {new Date(order.updatedAt).toLocaleDateString('en-US', {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
            })}
            .
          </p>
        </section>
      )}

      {/* Items */}
      <section className="mb-10">
        <h2 className="mb-4 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
          Items
        </h2>
        <div className="divide-y divide-[#e8e4df] border border-[#e8e4df]">
          {order.items.map((item) => (
            <div key={`${item.productId}-${item.size}`} className="flex gap-4 p-4">
              <div className="relative h-20 w-20 shrink-0 overflow-hidden bg-[#e8e4df]">
                <Image
                  src={item.imageUrl}
                  alt={item.productName}
                  fill
                  className="object-cover"
                  sizes="80px"
                />
              </div>
              <div className="flex flex-1 flex-col justify-center gap-1">
                <p className="text-xs font-medium uppercase tracking-wider text-[#6b6560]">
                  {item.brandName}
                </p>
                <p className="text-sm font-medium text-[#1a1a1a]">{item.productName}</p>
                <p className="text-xs text-[#6b6560]">
                  Size: {item.size} · Qty: {item.quantity}
                </p>
              </div>
              <div className="flex items-center">
                <p className="text-sm font-semibold text-[#1a1a1a]">
                  <CurrencyPrice
                    priceKrw={item.priceKrw * item.quantity}
                    priceUsd={item.priceUsd * item.quantity}
                    priceJpy={item.priceJpy * item.quantity}
                  />
                </p>
              </div>
            </div>
          ))}
        </div>
      </section>

      <div className="grid grid-cols-1 gap-8 md:grid-cols-2">
        {/* Shipping */}
        <section>
          <h2 className="mb-4 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
            Shipping Address
          </h2>
          <div className="border border-[#e8e4df] p-5">
            <p className="text-sm font-medium text-[#1a1a1a]">
              {order.shippingAddress.name}
            </p>
            <p className="mt-1 text-sm text-[#6b6560]">{order.shippingAddress.phone}</p>
            <p className="mt-2 text-sm text-[#6b6560]">{order.shippingAddress.address}</p>
            <p className="text-sm text-[#6b6560]">
              {order.shippingAddress.city}, {order.shippingAddress.country}{' '}
              {order.shippingAddress.postalCode}
            </p>
          </div>
        </section>

        {/* Payment Summary */}
        <section>
          <h2 className="mb-4 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
            Payment Summary
          </h2>
          <div className="border border-[#e8e4df] p-5">
            <div className="flex flex-col gap-2">
              <div className="flex justify-between text-sm">
                <span className="text-[#6b6560]">Subtotal</span>
                <span className="text-[#1a1a1a]">
                  <CurrencyPrice
                    priceKrw={order.subtotalKrw}
                    priceUsd={order.subtotalUsd}
                    priceJpy={order.subtotalJpy}
                  />
                </span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-[#6b6560]">Estimated Duty</span>
                <span className="text-[#1a1a1a]">
                  <CurrencyPrice
                    priceKrw={order.dutyKrw}
                    priceUsd={order.dutyUsd}
                    priceJpy={order.dutyJpy}
                  />
                </span>
              </div>
              <div className="mt-2 flex justify-between border-t border-[#e8e4df] pt-3 text-sm font-semibold">
                <span className="text-[#1a1a1a]">Total</span>
                <span className="text-[#1a1a1a]">
                  <CurrencyPrice
                    priceKrw={order.totalKrw}
                    priceUsd={order.totalUsd}
                    priceJpy={order.totalJpy}
                  />
                </span>
              </div>
            </div>
          </div>
        </section>
      </div>

      {/* Cancel action */}
      {canCancel && (
        <div className="mt-10">
          <OrderCancelButton orderId={order.id} />
        </div>
      )}
    </div>
  );
}
