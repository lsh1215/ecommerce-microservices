import { Suspense } from 'react';
import Link from 'next/link';
import { ProductCard } from '@/components/shared/ProductCard';
import { ProductGridSkeleton } from '@/components/shared/Skeleton';
import { MobileFilterDrawer } from '@/components/shared/MobileFilterDrawer';
import { SortSelect } from '@/components/shared/SortSelect';
import { serverFetch } from '@/lib/server-fetch';
import { mapProductResponse, mapBrandResponse } from '@/lib/mappers';
import type { PageResponse } from '@/types';
import type { Category, Origin } from '@/types';
import type { ProductResponse, BrandResponse } from '@/types/api-responses';

export const metadata = {
  title: 'Products — FOUNDRY',
  description: 'Browse heritage menswear from Outstanding, Warehouse, RRL and more.',
};

interface ProductsPageProps {
  searchParams: Promise<{
    q?: string;
    brand?: string;
    origin?: string;
    category?: string;
    sort?: string;
    page?: string;
  }>;
}

const CATEGORIES: Category[] = ['denim', 'outerwear', 'shirts', 'knitwear', 'pants', 'accessories'];
const ORIGINS: Origin[] = ['Korea', 'Japan', 'USA'];
const SORT_OPTIONS = [
  { value: 'newest', label: 'Newest' },
  { value: 'price_asc', label: 'Price: Low to High' },
  { value: 'price_desc', label: 'Price: High to Low' },
  { value: 'brand_az', label: 'Brand A–Z' },
];
const PAGE_SIZE = 24;

function buildSortParams(sort: string): { sort: string; direction: string } {
  switch (sort) {
    case 'price_asc':
      return { sort: 'basePrice', direction: 'asc' };
    case 'price_desc':
      return { sort: 'basePrice', direction: 'desc' };
    case 'brand_az':
      return { sort: 'brandName', direction: 'asc' };
    case 'newest':
    default:
      return { sort: 'createdAt', direction: 'desc' };
  }
}

export default async function ProductsPage({ searchParams }: ProductsPageProps) {
  const params = await searchParams;
  const { q, brand, origin, category, sort = 'newest' } = params;
  const currentPage = Math.max(1, parseInt(params.page ?? '1', 10) || 1);
  const backendPage = currentPage - 1;

  const brandsRaw = await serverFetch<BrandResponse[]>('/api/brands');
  const allBrands = (brandsRaw ?? []).map(mapBrandResponse);

  let productsPage: PageResponse<ProductResponse> | null = null;

  if (q) {
    productsPage = await serverFetch<PageResponse<ProductResponse>>(
      `/api/products/search?q=${encodeURIComponent(q)}&page=${backendPage}&size=${PAGE_SIZE}`,
    );
  } else {
    const queryParts: string[] = [];
    queryParts.push(`page=${backendPage}`);
    queryParts.push(`size=${PAGE_SIZE}`);

    if (brand) {
      const matchedBrand = allBrands.find((b) => b.slug === brand);
      if (matchedBrand) {
        queryParts.push(`brandId=${matchedBrand.id}`);
      }
    }

    if (category) {
      queryParts.push(`category=${category.toUpperCase()}`);
    }

    const { sort: sortField, direction } = buildSortParams(sort);
    queryParts.push(`sort=${sortField}`);
    queryParts.push(`direction=${direction}`);

    productsPage = await serverFetch<PageResponse<ProductResponse>>(
      `/api/products?${queryParts.join('&')}`,
    );
  }

  let products = (productsPage?.content ?? []).map(mapProductResponse);

  if (origin) {
    products = products.filter((p) => p.origin === (origin as Origin));
  }

  const totalProducts = origin ? products.length : (productsPage?.totalElements ?? products.length);
  const totalPages = origin
    ? Math.max(1, Math.ceil(products.length / PAGE_SIZE))
    : Math.max(1, productsPage?.totalPages ?? 1);
  const safePage = Math.min(currentPage, totalPages);

  const paged = origin ? products : products;

  const activeFiltersCount = [q, brand, origin, category].filter(Boolean).length;

  function buildFilterUrl(overrides: Record<string, string | undefined>): string {
    const base: Record<string, string | undefined> = {
      ...(q && { q }),
      ...(brand && { brand }),
      ...(origin && { origin }),
      ...(category && { category }),
      ...(sort !== 'newest' && { sort }),
    };
    const merged = { ...base, ...overrides };
    const clean = Object.fromEntries(
      Object.entries(merged).filter(([k, v]) => v !== undefined && k !== 'page'),
    ) as Record<string, string>;
    const qs = new URLSearchParams(clean).toString();
    return `/products${qs ? `?${qs}` : ''}`;
  }

  function buildPageUrl(page: number): string {
    const base = {
      ...(q && { q }),
      ...(brand && { brand }),
      ...(origin && { origin }),
      ...(category && { category }),
      ...(sort !== 'newest' && { sort }),
      ...(page > 1 && { page: String(page) }),
    };
    const qs = new URLSearchParams(base).toString();
    return `/products${qs ? `?${qs}` : ''}`;
  }

  const filterContent = (
    <>
      {/* Search */}
      <div>
        <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-[#6b6560]">Search</p>
        <form action="/products" method="get">
          <input
            name="q"
            type="search"
            defaultValue={q ?? ''}
            placeholder="Brand, fabric, name..."
            className="w-full border border-[#e8e4df] bg-white px-3 py-2 text-sm text-[#1a1a1a] placeholder:text-[#a39e93] focus:border-[#1a1a1a] focus:outline-none"
          />
        </form>
      </div>

      {/* Brand */}
      <div>
        <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-[#6b6560]">Brand</p>
        <div className="flex flex-col gap-2">
          {allBrands.map((b) => (
            <a
              key={b.id}
              href={buildFilterUrl({ brand: brand === b.slug ? undefined : b.slug })}
              className={`text-sm ${
                brand === b.slug
                  ? 'font-semibold text-[#c4633e]'
                  : 'text-[#1a1a1a] hover:text-[#c4633e]'
              }`}
            >
              {b.name}
            </a>
          ))}
        </div>
      </div>

      {/* Origin */}
      <div>
        <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-[#6b6560]">Origin</p>
        <div className="flex flex-col gap-2">
          {ORIGINS.map((o) => (
            <a
              key={o}
              href={buildFilterUrl({ origin: origin === o ? undefined : o })}
              className={`text-sm ${
                origin === o
                  ? 'font-semibold text-[#c4633e]'
                  : 'text-[#1a1a1a] hover:text-[#c4633e]'
              }`}
            >
              {o}
            </a>
          ))}
        </div>
      </div>

      {/* Category */}
      <div>
        <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-[#6b6560]">
          Category
        </p>
        <div className="flex flex-col gap-2">
          {CATEGORIES.map((cat) => (
            <a
              key={cat}
              href={buildFilterUrl({ category: category === cat ? undefined : cat })}
              className={`text-sm capitalize ${
                category === cat
                  ? 'font-semibold text-[#c4633e]'
                  : 'text-[#1a1a1a] hover:text-[#c4633e]'
              }`}
            >
              {cat}
            </a>
          ))}
        </div>
      </div>

      {activeFiltersCount > 0 && (
        <Link
          href="/products"
          className="text-xs font-medium text-[#c4633e] underline underline-offset-4"
        >
          Clear all filters
        </Link>
      )}
    </>
  );

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 md:px-6">
      {/* Page header */}
      <div className="mb-8">
        <h1 className="font-heading text-3xl font-bold text-[#1a1a1a]">
          {q ? `Results for "${q}"` : 'All Products'}
        </h1>
        <p className="mt-1 text-sm text-[#6b6560]">
          {totalProducts} {totalProducts === 1 ? 'product' : 'products'}
        </p>
      </div>

      <div className="flex gap-8">
        {/* Desktop filter sidebar */}
        <aside className="hidden w-52 shrink-0 md:block">
          <div className="flex flex-col gap-8">{filterContent}</div>
        </aside>

        {/* Main content */}
        <div className="flex-1 min-w-0">
          {/* Sort + active filters bar */}
          <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap items-center gap-2">
              <MobileFilterDrawer resultCount={totalProducts}>{filterContent}</MobileFilterDrawer>
              {q && (
                <a
                  href={buildFilterUrl({ q: undefined })}
                  className="flex items-center gap-1 border border-[#1a1a1a] px-2 py-1 text-xs font-medium"
                >
                  &ldquo;{q}&rdquo; &times;
                </a>
              )}
              {brand && (
                <a
                  href={buildFilterUrl({ brand: undefined })}
                  className="flex items-center gap-1 border border-[#1a1a1a] px-2 py-1 text-xs font-medium"
                >
                  {allBrands.find((b) => b.slug === brand)?.name ?? brand} &times;
                </a>
              )}
              {origin && (
                <a
                  href={buildFilterUrl({ origin: undefined })}
                  className="flex items-center gap-1 border border-[#1a1a1a] px-2 py-1 text-xs font-medium"
                >
                  {origin} &times;
                </a>
              )}
              {category && (
                <a
                  href={buildFilterUrl({ category: undefined })}
                  className="flex items-center gap-1 border border-[#1a1a1a] px-2 py-1 text-xs font-medium capitalize"
                >
                  {category} &times;
                </a>
              )}
            </div>

            <SortSelect currentSort={sort} options={SORT_OPTIONS} />
          </div>

          <Suspense fallback={<ProductGridSkeleton />}>
            {paged.length > 0 ? (
              <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
                {paged.map((product) => (
                  <ProductCard key={product.id} product={product} />
                ))}
              </div>
            ) : (
              <div className="py-20 text-center">
                <p className="font-heading text-2xl font-bold text-[#1a1a1a]">No products found</p>
                <p className="mt-3 text-sm text-[#6b6560]">
                  Try adjusting your filters or{' '}
                  <Link href="/products" className="text-[#c4633e] underline">
                    browse all products
                  </Link>
                </p>
              </div>
            )}
          </Suspense>

          {/* Pagination */}
          {totalPages > 1 && (
            <nav className="mt-10 flex items-center justify-center gap-2" aria-label="Pagination">
              {safePage > 1 ? (
                <a
                  href={buildPageUrl(safePage - 1)}
                  className="border border-[#e8e4df] px-4 py-2 text-sm font-medium text-[#1a1a1a] hover:border-[#1a1a1a]"
                >
                  Previous
                </a>
              ) : (
                <span className="border border-[#e8e4df] px-4 py-2 text-sm font-medium text-[#a39e93]">
                  Previous
                </span>
              )}

              <span className="px-3 text-sm text-[#6b6560]">
                Page {safePage} of {totalPages}
              </span>

              {safePage < totalPages ? (
                <a
                  href={buildPageUrl(safePage + 1)}
                  className="border border-[#e8e4df] px-4 py-2 text-sm font-medium text-[#1a1a1a] hover:border-[#1a1a1a]"
                >
                  Next
                </a>
              ) : (
                <span className="border border-[#e8e4df] px-4 py-2 text-sm font-medium text-[#a39e93]">
                  Next
                </span>
              )}
            </nav>
          )}
        </div>
      </div>
    </div>
  );
}
