import Link from 'next/link';
import Image from 'next/image';
import { mockOrders } from '@/mocks';
import { CurrencyPrice } from '@/components/shared/CurrencyPrice';
import type { OrderStatus } from '@/types';

export const metadata = {
  title: 'Orders — FOUNDRY',
  description: 'Your order history.',
};

const STATUS_CONFIG: Record<OrderStatus, { label: string; classes: string }> = {
  PENDING: { label: 'Pending', classes: 'bg-yellow-50 text-yellow-700 border border-yellow-200' },
  CONFIRMED: { label: 'Confirmed', classes: 'bg-blue-50 text-blue-700 border border-blue-200' },
  SHIPPED: { label: 'Shipped', classes: 'bg-purple-50 text-purple-700 border border-purple-200' },
  DELIVERED: { label: 'Delivered', classes: 'bg-green-50 text-green-700 border border-green-200' },
  CANCELLED: { label: 'Cancelled', classes: 'bg-gray-100 text-gray-500 border border-gray-200' },
};

export default function OrdersPage() {
  if (mockOrders.length === 0) {
    return (
      <div>
        <h1 className="font-heading mb-8 text-3xl font-bold text-[#1a1a1a]">Orders</h1>
        <div className="py-20 text-center">
          <p className="font-heading text-2xl font-bold text-[#1a1a1a]">No orders yet</p>
          <p className="mt-3 text-sm text-[#6b6560]">
            You have not placed any orders yet.{' '}
            <Link href="/products" className="text-[#c4633e] underline">
              Start browsing
            </Link>
          </p>
        </div>
      </div>
    );
  }

  return (
    <div>
      <h1 className="font-heading mb-8 text-3xl font-bold text-[#1a1a1a]">Orders</h1>

      <div className="flex flex-col gap-4">
        {mockOrders.map((order) => {
          const statusCfg = STATUS_CONFIG[order.status];
          const itemCount = order.items.reduce((sum, i) => sum + i.quantity, 0);

          return (
            <Link
              key={order.id}
              href={`/orders/${order.id}`}
              className="group border border-[#e8e4df] p-5 transition-colors hover:border-[#1a1a1a]"
            >
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div className="flex items-start gap-4">
                  {/* First item image */}
                  <div className="relative h-16 w-16 shrink-0 overflow-hidden bg-[#e8e4df]">
                    <Image
                      src={order.items[0]!.imageUrl}
                      alt={order.items[0]!.productName}
                      fill
                      className="object-cover"
                      sizes="64px"
                    />
                    {order.items.length > 1 && (
                      <div className="absolute inset-0 flex items-center justify-center bg-[#1a1a1a]/50">
                        <span className="text-xs font-bold text-white">
                          +{order.items.length - 1}
                        </span>
                      </div>
                    )}
                  </div>

                  <div>
                    <p className="text-xs font-medium text-[#6b6560]">
                      {new Date(order.createdAt).toLocaleDateString('en-US', {
                        year: 'numeric',
                        month: 'short',
                        day: 'numeric',
                      })}
                    </p>
                    <p className="mt-0.5 text-sm font-medium text-[#1a1a1a]">
                      Order {order.id}
                    </p>
                    <p className="mt-0.5 text-xs text-[#6b6560]">
                      {itemCount} {itemCount === 1 ? 'item' : 'items'}
                      {order.dropName && (
                        <span className="text-[#a39e93]"> · {order.dropName}</span>
                      )}
                    </p>
                  </div>
                </div>

                <div className="flex flex-col items-end gap-2">
                  <span
                    className={`inline-flex px-2 py-0.5 text-xs font-medium uppercase tracking-wide ${statusCfg.classes}`}
                  >
                    {statusCfg.label}
                  </span>
                  <p className="text-sm font-semibold text-[#1a1a1a]">
                    <CurrencyPrice
                      priceKrw={order.totalKrw}
                      priceUsd={order.totalUsd}
                      priceJpy={order.totalJpy}
                    />
                  </p>
                </div>
              </div>
            </Link>
          );
        })}
      </div>
    </div>
  );
}
