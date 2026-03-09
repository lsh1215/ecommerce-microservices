import Link from 'next/link';
import Image from 'next/image';
import { serverFetch } from '@/lib/server-fetch';
import { mapBrandResponse } from '@/lib/mappers';
import type { BrandResponse } from '@/types/api-responses';
import type { Origin } from '@/types';

export const metadata = {
  title: 'Brands — FOUNDRY',
  description: 'Heritage menswear brands from Korea, Japan, and the USA.',
};

const ORIGIN_FLAGS: Record<Origin, string> = {
  Korea: '🇰🇷',
  Japan: '🇯🇵',
  USA: '🇺🇸',
};

const ORIGINS: (Origin | 'All')[] = ['All', 'Korea', 'Japan', 'USA'];

interface BrandsPageProps {
  searchParams: Promise<{ origin?: string }>;
}

export default async function BrandsPage({ searchParams }: BrandsPageProps) {
  const params = await searchParams;
  const selectedOrigin = params.origin as Origin | undefined;

  const brandResponses = await serverFetch<BrandResponse[]>('/api/brands');
  const allBrands = (brandResponses ?? []).map(mapBrandResponse);

  const filtered = selectedOrigin
    ? allBrands.filter((b) => b.origin === selectedOrigin)
    : allBrands;

  return (
    <div className="mx-auto max-w-7xl px-4 py-12 md:px-6">
      <div className="mb-10">
        <h1 className="font-heading text-3xl font-bold text-[#1a1a1a]">Brands</h1>
        <p className="mt-2 text-sm text-[#6b6560]">
          Heritage menswear from Korea, Japan, and the American frontier.
        </p>
      </div>

      {/* Origin filter */}
      <div className="mb-8 flex flex-wrap gap-2">
        {ORIGINS.map((origin) => {
          const isActive = (origin === 'All' && !selectedOrigin) || origin === selectedOrigin;
          const href = origin === 'All' ? '/brands' : `/brands?origin=${origin}`;

          return (
            <a
              key={origin}
              href={href}
              className={`px-4 py-2 text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-[#1a1a1a] text-white'
                  : 'border border-[#e8e4df] text-[#1a1a1a] hover:border-[#1a1a1a]'
              }`}
            >
              {origin === 'All' ? 'All' : `${ORIGIN_FLAGS[origin]} ${origin}`}
            </a>
          );
        })}
      </div>

      {/* Brand grid */}
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        {filtered.map((brand) => (
          <Link
            key={brand.id}
            href={`/brands/${brand.slug}`}
            className="group flex gap-5 border border-[#e8e4df] p-0 transition-colors hover:border-[#1a1a1a]"
          >
            <div className="relative h-auto w-40 shrink-0 overflow-hidden bg-[#e8e4df]">
              <Image
                src={brand.imageUrl || '/placeholder-brand.jpg'}
                alt={brand.name}
                fill
                className="object-cover transition-transform duration-500 group-hover:scale-105"
                sizes="160px"
              />
            </div>
            <div className="flex flex-col justify-center gap-2 py-5 pr-5">
              <div className="flex items-center gap-2">
                <span className="text-lg">{ORIGIN_FLAGS[brand.origin]}</span>
                <p className="text-xs font-medium uppercase tracking-wider text-[#6b6560]">
                  {brand.origin}
                </p>
              </div>
              <h2 className="font-heading text-xl font-bold text-[#1a1a1a]">{brand.name}</h2>
              <div className="flex flex-wrap gap-3 text-xs text-[#6b6560]">
                {brand.styleCategory && <span>{brand.styleCategory}</span>}
                {brand.foundedYear && <span>Est. {brand.foundedYear}</span>}
              </div>
              <p className="mt-1 text-sm leading-relaxed text-[#6b6560] line-clamp-2">
                {brand.description}
              </p>
            </div>
          </Link>
        ))}
      </div>

      {filtered.length === 0 && (
        <div className="py-20 text-center">
          <p className="font-heading text-2xl font-bold text-[#1a1a1a]">No brands found</p>
          <p className="mt-3 text-sm text-[#6b6560]">
            <Link href="/brands" className="text-[#c4633e] underline">
              View all brands
            </Link>
          </p>
        </div>
      )}
    </div>
  );
}
