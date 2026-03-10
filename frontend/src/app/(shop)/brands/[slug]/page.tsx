import Link from 'next/link';
import Image from 'next/image';
import { notFound } from 'next/navigation';
import { serverFetch } from '@/lib/server-fetch';
import { mapBrandResponse, mapProductResponse } from '@/lib/mappers';
import { ProductCard } from '@/components/shared/ProductCard';
import type { BrandResponse, ProductResponse } from '@/types/api-responses';
import type { PageResponse, Origin } from '@/types';

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
  const brandRes = await serverFetch<BrandResponse>(`/api/brands/${slug}`);
  if (!brandRes) return { title: 'Brand Not Found — FOUNDRY' };
  const brand = mapBrandResponse(brandRes);
  return {
    title: `${brand.name} — FOUNDRY`,
    description: brand.description,
  };
}

export default async function BrandDetailPage({ params }: BrandDetailPageProps) {
  const { slug } = await params;
  const brandRes = await serverFetch<BrandResponse>(`/api/brands/${slug}`);

  if (!brandRes) notFound();

  const brand = mapBrandResponse(brandRes);

  const productPage = await serverFetch<PageResponse<ProductResponse>>(
    `/api/products?brandId=${brandRes.id}&page=0&size=20`,
  );
  const products = (productPage?.content ?? []).map(mapProductResponse);

  return (
    <div>
      {/* Hero */}
      <section className="relative h-[50vh] min-h-[320px] max-h-[500px] overflow-hidden bg-[#1a1a1a]">
        <Image
          src={brand.imageUrl || '/placeholder-brand.jpg'}
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
