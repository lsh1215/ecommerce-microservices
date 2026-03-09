import Link from 'next/link';
import Image from 'next/image';
import { Header } from '@/components/shared/Header';
import { Footer } from '@/components/shared/Footer';
import { MobileNav } from '@/components/shared/MobileNav';
import { ProductCard } from '@/components/shared/ProductCard';
import { DropStatusBadge } from '@/components/shared/DropStatusBadge';
import { CountdownTimer } from '@/components/shared/CountdownTimer';
import { serverFetch } from '@/lib/server-fetch';
import { mapProductResponse, mapBrandResponse, mapDropResponse } from '@/lib/mappers';
import type { PageResponse } from '@/types';
import type { ProductResponse, BrandResponse, DropEventResponse } from '@/types/api-responses';

const DEFAULT_DROP_IMAGE =
  'https://images.unsplash.com/photo-1594938298603-c8148c4dae35?w=800&q=80';

export default async function HomePage() {
  const [productsPage, dropsPage, brandsRaw] = await Promise.all([
    serverFetch<PageResponse<ProductResponse>>(
      '/api/products?page=0&size=8&sort=createdAt&direction=desc',
    ),
    serverFetch<PageResponse<DropEventResponse>>('/api/drops?page=0&size=10'),
    serverFetch<BrandResponse[]>('/api/brands'),
  ]);

  const newArrivals = (productsPage?.content ?? []).map(mapProductResponse);
  const allDrops = (dropsPage?.content ?? []).map(mapDropResponse);
  const allBrands = (brandsRaw ?? []).map((b, i) => ({
    ...mapBrandResponse(b),
    featured: i < 3,
  }));

  const liveDrops = allDrops.filter((d) => d.status === 'SELLING' || d.status === 'OPEN');
  const heroDropRaw = liveDrops[0] ?? allDrops.find((d) => d.status === 'ANNOUNCED');
  const heroDrop = heroDropRaw ?? allDrops[0];

  const featuredBrands = allBrands.filter((b) => b.featured);

  return (
    <>
      <Header />
      <main className="min-h-screen pb-16 md:pb-0">
        {/* Hero */}
        {heroDrop ? (
          <section className="relative h-[85vh] min-h-[520px] max-h-[800px] overflow-hidden bg-[#1a1a1a]">
            <Image
              src={heroDrop.heroImageUrl || DEFAULT_DROP_IMAGE}
              alt={heroDrop.name}
              fill
              priority
              className="object-cover opacity-70"
              sizes="100vw"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-[#1a1a1a]/80 via-transparent to-transparent" />

            <div className="absolute inset-0 flex flex-col justify-end px-6 py-12 md:px-12 md:py-16">
              <div className="max-w-2xl">
                <DropStatusBadge status={heroDrop.status} className="mb-4" />
                <h1 className="font-heading text-4xl font-bold leading-tight text-white md:text-6xl">
                  {heroDrop.name}
                </h1>
                <p className="mt-4 text-sm font-medium text-[#e8e4df]">
                  {heroDrop.name}
                </p>

                {(heroDrop.status === 'SELLING' || heroDrop.status === 'OPEN') && (
                  <p className="mt-3 flex items-center gap-2 text-sm text-[#e8e4df]">
                    <span>Closes in</span>
                    <CountdownTimer
                      targetDate={heroDrop.closesAt}
                      className="font-semibold text-white"
                    />
                  </p>
                )}

                {heroDrop.status === 'ANNOUNCED' && (
                  <p className="mt-3 flex items-center gap-2 text-sm text-[#e8e4df]">
                    <span>Opens in</span>
                    <CountdownTimer
                      targetDate={heroDrop.opensAt}
                      className="font-semibold text-white"
                    />
                  </p>
                )}

                <Link
                  href={`/drops/${heroDrop.id}`}
                  className="mt-8 inline-block bg-white px-8 py-3 text-sm font-semibold uppercase tracking-widest text-[#1a1a1a] transition-opacity hover:opacity-90"
                >
                  Shop Drop
                </Link>
              </div>
            </div>
          </section>
        ) : (
          <section className="relative h-[85vh] min-h-[520px] max-h-[800px] overflow-hidden bg-[#1a1a1a]">
            <div className="absolute inset-0 flex items-center justify-center">
              <h1 className="font-heading text-4xl font-bold text-white md:text-6xl">FOUNDRY</h1>
            </div>
          </section>
        )}

        {/* Live Drops Strip */}
        {liveDrops.length > 0 && (
          <section className="border-b border-[#e8e4df] bg-[#1a1a1a] px-4 py-6 md:px-6">
            <div className="mx-auto max-w-7xl">
              <div className="mb-4 flex items-center gap-3">
                <span className="relative flex h-2 w-2">
                  <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-red-400 opacity-75" />
                  <span className="relative inline-flex h-2 w-2 rounded-full bg-red-500" />
                </span>
                <p className="text-xs font-semibold uppercase tracking-widest text-[#a39e93]">
                  Live Now
                </p>
              </div>

              <div className="flex gap-4 overflow-x-auto pb-2">
                {liveDrops.map((drop) => (
                  <Link
                    key={drop.id}
                    href={`/drops/${drop.id}`}
                    className="group flex min-w-[240px] flex-col gap-2 border border-[#333] p-4 transition-colors hover:border-[#c4633e]"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <p className="text-xs font-medium uppercase tracking-wide text-[#a39e93]">
                        {drop.name}
                      </p>
                      <DropStatusBadge status={drop.status} />
                    </div>
                    <p className="text-sm font-medium leading-snug text-white">{drop.name}</p>
                    <p className="text-xs text-[#6b6560]">
                      Closes in{' '}
                      <CountdownTimer
                        targetDate={drop.closesAt}
                        className="font-semibold text-[#e8e4df]"
                      />
                    </p>
                  </Link>
                ))}
              </div>
            </div>
          </section>
        )}

        {/* Featured Brands */}
        <section className="mx-auto max-w-7xl px-4 py-16 md:px-6 md:py-20">
          <div className="mb-10 flex items-end justify-between">
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
                Featured Brands
              </p>
              <h2 className="font-heading text-3xl font-bold text-[#1a1a1a]">Curated Heritage</h2>
            </div>
            <Link
              href="/brands"
              className="hidden text-sm font-medium text-[#c4633e] underline underline-offset-4 md:block"
            >
              All Brands
            </Link>
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            {featuredBrands.map((brand) => (
              <Link
                key={brand.id}
                href={`/brands/${brand.slug}`}
                className="group relative aspect-[4/3] overflow-hidden bg-[#e8e4df]"
              >
                <Image
                  src={brand.imageUrl || DEFAULT_DROP_IMAGE}
                  alt={brand.name}
                  fill
                  className="object-cover transition-transform duration-500 group-hover:scale-105"
                  sizes="(max-width: 768px) 100vw, 33vw"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-[#1a1a1a]/70 via-transparent" />
                <div className="absolute bottom-0 left-0 p-5">
                  <p className="text-xs font-medium uppercase tracking-widest text-[#a39e93]">
                    {brand.origin}
                  </p>
                  <p className="font-heading text-xl font-bold text-white">{brand.name}</p>
                  <p className="mt-1 text-xs text-[#e8e4df]">{brand.description}</p>
                </div>
              </Link>
            ))}
          </div>
        </section>

        {/* New Arrivals */}
        <section className="border-t border-[#e8e4df] bg-[#f3f0eb] px-4 py-16 md:px-6 md:py-20">
          <div className="mx-auto max-w-7xl">
            <div className="mb-10 flex items-end justify-between">
              <div>
                <p className="mb-2 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
                  New Arrivals
                </p>
                <h2 className="font-heading text-3xl font-bold text-[#1a1a1a]">Just In</h2>
              </div>
              <Link
                href="/products?sort=newest"
                className="hidden text-sm font-medium text-[#c4633e] underline underline-offset-4 md:block"
              >
                View All
              </Link>
            </div>

            <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
              {newArrivals.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>

            <div className="mt-10 text-center md:hidden">
              <Link
                href="/products"
                className="inline-block border border-[#1a1a1a] px-8 py-3 text-sm font-semibold uppercase tracking-widest text-[#1a1a1a]"
              >
                View All Products
              </Link>
            </div>
          </div>
        </section>

        {/* Editorial Block */}
        <section className="mx-auto max-w-7xl px-4 py-16 md:px-6 md:py-24">
          <div className="grid grid-cols-1 gap-12 md:grid-cols-2 md:items-center">
            <div>
              <p className="mb-4 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
                The FOUNDRY Edit
              </p>
              <h2 className="font-heading text-4xl font-bold leading-tight text-[#1a1a1a] md:text-5xl">
                Heritage wear should not be a treasure hunt.
              </h2>
              <p className="mt-6 text-base leading-relaxed text-[#6b6560]">
                Outstanding. Warehouse. RRL. Three brands separated by oceans, united by obsession
                with fabric and function. FOUNDRY exists so you never need a proxy service, a
                translator, or a flight to Tokyo.
              </p>
              <p className="mt-4 text-base leading-relaxed text-[#6b6560]">
                Every drop is final sale. No restocks. That is the point.
              </p>
              <Link
                href="/about"
                className="mt-8 inline-block border border-[#1a1a1a] px-8 py-3 text-sm font-semibold uppercase tracking-widest text-[#1a1a1a] transition-colors hover:bg-[#1a1a1a] hover:text-white"
              >
                About FOUNDRY
              </Link>
            </div>
            <div className="relative aspect-square overflow-hidden bg-[#e8e4df]">
              <Image
                src="https://images.unsplash.com/photo-1604644401890-0bd678c83788?w=800&q=80"
                alt="Heritage craft"
                fill
                className="object-cover"
                sizes="(max-width: 768px) 100vw, 50vw"
              />
            </div>
          </div>
        </section>
      </main>
      <Footer />
      <MobileNav />
    </>
  );
}
