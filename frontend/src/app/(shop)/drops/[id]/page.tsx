import { notFound } from 'next/navigation';
import Image from 'next/image';
import { Package, Truck } from 'lucide-react';
import { DropStatusBadge } from '@/components/shared/DropStatusBadge';
import { CountdownTimer } from '@/components/shared/CountdownTimer';
import { DropProductCard } from '@/components/shared/DropProductCard';
import { getDropById } from '@/mocks/drops';
import { getProductsByDrop } from '@/mocks/products';

interface DropDetailPageProps {
  params: Promise<{ id: string }>;
}

export async function generateMetadata({ params }: DropDetailPageProps) {
  const { id } = await params;
  const drop = getDropById(id);
  if (!drop) return { title: 'Drop Not Found — FOUNDRY' };
  return { title: `${drop.name} — FOUNDRY` };
}

export default async function DropDetailPage({ params }: DropDetailPageProps) {
  const { id } = await params;
  const drop = getDropById(id);
  if (!drop) notFound();

  const products = getProductsByDrop(drop.id);
  const inStockProducts = products.filter((p) =>
    p.sizes.some((s) => s.stock > 0),
  );
  const soldOutProducts = products.filter((p) =>
    p.sizes.every((s) => s.stock === 0),
  );

  const isLive = drop.status === 'SELLING' || drop.status === 'OPEN';
  const isAnnounced = drop.status === 'ANNOUNCED';

  return (
    <div>
      {/* Sticky countdown bar */}
      {(isLive || isAnnounced) && (
        <div className="sticky top-14 z-40 border-b border-[#e8e4df] bg-[#1a1a1a] px-4 py-2.5">
          <div className="mx-auto flex max-w-7xl items-center justify-between gap-4">
            <p className="text-xs font-medium text-[#a39e93]">
              {isLive ? 'Drop closes in' : 'Drop opens in'}
            </p>
            <CountdownTimer
              targetDate={isLive ? drop.closesAt : drop.opensAt}
              className="font-semibold text-white"
            />
          </div>
        </div>
      )}

      {/* Hero */}
      <section className="relative aspect-[16/7] max-h-[480px] overflow-hidden bg-[#1a1a1a]">
        <Image
          src={drop.heroImageUrl}
          alt={drop.name}
          fill
          priority
          className="object-cover opacity-60"
          sizes="100vw"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-[#1a1a1a]/70 via-transparent" />
        <div className="absolute inset-0 flex flex-col justify-end px-6 py-10 md:px-12">
          <div className="max-w-3xl">
            <DropStatusBadge status={drop.status} className="mb-3" />
            <p className="text-sm font-medium uppercase tracking-wider text-[#a39e93]">
              {drop.brand.name} · {drop.brand.origin}
            </p>
            <h1 className="font-heading mt-2 text-3xl font-bold text-white md:text-5xl">
              {drop.name}
            </h1>
            {drop.nameKo && (
              <p className="mt-1 text-sm text-[#a39e93]">{drop.nameKo}</p>
            )}
            <p className="mt-4 max-w-xl text-sm leading-relaxed text-[#e8e4df]">
              {drop.description}
            </p>
          </div>
        </div>
      </section>

      <div className="mx-auto max-w-7xl px-4 py-12 md:px-6">
        {/* Products */}
        {inStockProducts.length > 0 && (
          <section className="mb-12">
            <h2 className="font-heading mb-6 text-xl font-bold text-[#1a1a1a]">
              Drop Products
            </h2>
            <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4">
              {inStockProducts.map((product) => (
                <DropProductCard
                  key={product.id}
                  product={product}
                  dropStatus={drop.status}
                />
              ))}
            </div>
          </section>
        )}

        {soldOutProducts.length > 0 && (
          <section className="mb-12">
            <h2 className="font-heading mb-6 text-lg font-bold text-[#6b6560]">Sold Out</h2>
            <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4 opacity-50">
              {soldOutProducts.map((product) => (
                <DropProductCard
                  key={product.id}
                  product={product}
                  dropStatus={drop.status}
                />
              ))}
            </div>
          </section>
        )}

        {/* Drop Info */}
        <section className="border-t border-[#e8e4df] pt-10">
          <div className="grid grid-cols-1 gap-8 md:grid-cols-2">
            {drop.returnPolicy && (
              <div className="flex gap-4">
                <Package size={20} strokeWidth={1.5} className="mt-0.5 shrink-0 text-[#6b6560]" />
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wider text-[#6b6560]">
                    Return Policy
                  </p>
                  <p className="mt-1 text-sm text-[#1a1a1a]">{drop.returnPolicy}</p>
                </div>
              </div>
            )}
            {drop.shippingTimeline && (
              <div className="flex gap-4">
                <Truck size={20} strokeWidth={1.5} className="mt-0.5 shrink-0 text-[#6b6560]" />
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wider text-[#6b6560]">
                    Shipping
                  </p>
                  <p className="mt-1 text-sm text-[#1a1a1a]">{drop.shippingTimeline}</p>
                </div>
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
