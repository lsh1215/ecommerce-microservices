import Link from 'next/link';
import Image from 'next/image';
import { notFound } from 'next/navigation';
import { getBrandBySlug } from '@/mocks/brands';
import { getProductsByBrand } from '@/mocks/products';
import { mockDrops } from '@/mocks';
import { ProductCard } from '@/components/shared/ProductCard';
import { DropStatusBadge } from '@/components/shared/DropStatusBadge';
import { CountdownTimer } from '@/components/shared/CountdownTimer';
import type { Origin } from '@/types';

const ORIGIN_FLAGS: Record<Origin, string> = {
  Korea: '🇰🇷',
  Japan: '🇯🇵',
  USA: '🇺🇸',
};

interface BrandDetailPageProps {
  params: Promise<{ slug: string }>;
}

export async function generateMetadata({ params }: BrandDetailPageProps) {
  const { slug } = await params;
  const brand = getBrandBySlug(slug);
  if (!brand) return { title: 'Brand Not Found — FOUNDRY' };
  return {
    title: `${brand.name} — FOUNDRY`,
    description: brand.description,
  };
}

export default async function BrandDetailPage({ params }: BrandDetailPageProps) {
  const { slug } = await params;
  const brand = getBrandBySlug(slug);

  if (!brand) notFound();

  const products = getProductsByBrand(slug);
  const activeDrops = mockDrops.filter(
    (d) =>
      d.brand.slug === slug &&
      (d.status === 'SELLING' || d.status === 'OPEN' || d.status === 'ANNOUNCED'),
  );

  return (
    <div>
      {/* Hero */}
      <section className="relative h-[50vh] min-h-[320px] max-h-[500px] overflow-hidden bg-[#1a1a1a]">
        <Image
          src={brand.imageUrl}
          alt={brand.name}
          fill
          priority
          className="object-cover opacity-60"
          sizes="100vw"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-[#1a1a1a]/80 via-transparent to-transparent" />
        <div className="absolute inset-0 flex flex-col justify-end px-6 py-10 md:px-12">
          <div className="mx-auto w-full max-w-7xl">
            <div className="flex items-center gap-3 mb-3">
              <span className="text-2xl">{ORIGIN_FLAGS[brand.origin]}</span>
              <span className="text-xs font-semibold uppercase tracking-widest text-[#a39e93]">
                {brand.origin}
              </span>
              {brand.styleCategory && (
                <>
                  <span className="text-[#6b6560]">·</span>
                  <span className="text-xs font-medium text-[#a39e93]">{brand.styleCategory}</span>
                </>
              )}
              {brand.foundedYear && (
                <>
                  <span className="text-[#6b6560]">·</span>
                  <span className="text-xs font-medium text-[#a39e93]">
                    Est. {brand.foundedYear}
                  </span>
                </>
              )}
            </div>
            <h1 className="font-heading text-4xl font-bold text-white md:text-5xl">{brand.name}</h1>
            {brand.fullDescription ? (
              <p className="mt-4 max-w-2xl text-sm leading-relaxed text-[#e8e4df]">
                {brand.fullDescription}
              </p>
            ) : (
              <p className="mt-4 max-w-2xl text-sm leading-relaxed text-[#e8e4df]">
                {brand.description}
              </p>
            )}
          </div>
        </div>
      </section>

      <div className="mx-auto max-w-7xl px-4 py-12 md:px-6">
        {/* Active Drops */}
        {activeDrops.length > 0 && (
          <section className="mb-16">
            <h2 className="font-heading mb-6 text-2xl font-bold text-[#1a1a1a]">Active Drops</h2>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              {activeDrops.map((drop) => (
                <Link
                  key={drop.id}
                  href={`/drops/${drop.id}`}
                  className="group relative aspect-[3/2] overflow-hidden bg-[#e8e4df]"
                >
                  <Image
                    src={drop.heroImageUrl}
                    alt={drop.name}
                    fill
                    className="object-cover transition-transform duration-500 group-hover:scale-105"
                    sizes="(max-width: 768px) 100vw, 50vw"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-[#1a1a1a]/80 via-transparent" />
                  <div className="absolute inset-0 flex flex-col justify-end p-5">
                    <DropStatusBadge status={drop.status} className="mb-2" />
                    <h3 className="font-heading text-lg font-bold text-white">{drop.name}</h3>
                    <p className="mt-1.5 flex items-center gap-1.5 text-xs text-[#e8e4df]">
                      {drop.status === 'ANNOUNCED' ? 'Opens in ' : 'Closes in '}
                      <CountdownTimer
                        targetDate={drop.status === 'ANNOUNCED' ? drop.opensAt : drop.closesAt}
                        className="font-semibold text-white"
                      />
                    </p>
                  </div>
                </Link>
              ))}
            </div>
          </section>
        )}

        {/* Products */}
        <section>
          <div className="mb-6 flex items-end justify-between">
            <h2 className="font-heading text-2xl font-bold text-[#1a1a1a]">Products</h2>
            <Link
              href={`/products?brand=${slug}`}
              className="text-sm font-medium text-[#c4633e] underline underline-offset-4"
            >
              View all with filters
            </Link>
          </div>

          {products.length > 0 ? (
            <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
              {products.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>
          ) : (
            <p className="py-12 text-center text-sm text-[#6b6560]">
              No products available yet. Check back soon.
            </p>
          )}
        </section>
      </div>
    </div>
  );
}
