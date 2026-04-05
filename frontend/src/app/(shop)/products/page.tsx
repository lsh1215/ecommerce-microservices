import { Suspense } from 'react';
import Link from 'next/link';
import { ProductCard } from '@/components/shared/ProductCard';
import { ProductGridSkeleton } from '@/components/shared/Skeleton';
import { EmptyState } from '@/components/shared/EmptyState';
import { FilterPanel } from '@/features/products/components/FilterPanel';
import { SortControl } from '@/features/products/components/SortControl';
import { mockProducts } from '@/mocks/products';
import { mockBrands } from '@/mocks/brands';
import type { Category } from '@/types';

export const metadata = {
  title: 'Products',
  description: 'Browse all products.',
};

const PAGE_SIZE = 12;

const SORT_OPTIONS = [
  { value: 'newest', label: 'Newest' },
  { value: 'price_asc', label: 'Price: Low to High' },
  { value: 'price_desc', label: 'Price: High to Low' },
  { value: 'name_az', label: 'Name: A–Z' },
];

interface ProductsPageProps {
  searchParams: Promise<{
    q?: string;
    brandId?: string | string[];
    category?: string | string[];
    minPrice?: string;
    maxPrice?: string;
    sort?: string;
    page?: string;
  }>;
}

export default async function ProductsPage({ searchParams }: ProductsPageProps) {
  const params = await searchParams;

  const q = params.q ?? '';
  const sort = params.sort ?? 'newest';
  const page = Math.max(1, parseInt(params.page ?? '1', 10));

  const brandIds = Array.isArray(params.brandId)
    ? params.brandId
    : params.brandId
      ? [params.brandId]
      : [];

  const categories = Array.isArray(params.category)
    ? params.category
    : params.category
      ? [params.category]
      : [];

  const minPrice = params.minPrice ? parseInt(params.minPrice, 10) : undefined;
  const maxPrice = params.maxPrice ? parseInt(params.maxPrice, 10) : undefined;

  let products = [...mockProducts];

  if (q) {
    const lower = q.toLowerCase();
    products = products.filter(
      (p) =>
        p.name.toLowerCase().includes(lower) ||
        p.brand.name.toLowerCase().includes(lower) ||
        p.description.toLowerCase().includes(lower),
    );
  }

  if (brandIds.length > 0) {
    products = products.filter((p) => brandIds.includes(p.brand.id));
  }

  if (categories.length > 0) {
    products = products.filter((p) => categories.includes(p.category));
  }

  if (minPrice !== undefined) {
    products = products.filter((p) => p.price >= minPrice);
  }

  if (maxPrice !== undefined) {
    products = products.filter((p) => p.price <= maxPrice);
  }

  if (sort === 'price_asc') {
    products = products.sort((a, b) => a.price - b.price);
  } else if (sort === 'price_desc') {
    products = products.sort((a, b) => b.price - a.price);
  } else if (sort === 'name_az') {
    products = products.sort((a, b) => a.name.localeCompare(b.name));
  } else {
    products = products.sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    );
  }

  const totalCount = products.length;
  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const paginatedProducts = products.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  function buildPageUrl(targetPage: number): string {
    const p = new URLSearchParams();
    if (q) p.set('q', q);
    if (sort !== 'newest') p.set('sort', sort);
    brandIds.forEach((id) => p.append('brandId', id));
    categories.forEach((c) => p.append('category', c as Category));
    if (params.minPrice) p.set('minPrice', params.minPrice);
    if (params.maxPrice) p.set('maxPrice', params.maxPrice);
    if (targetPage > 1) p.set('page', String(targetPage));
    const qs = p.toString();
    return `/products${qs ? `?${qs}` : ''}`;
  }

  const activeFilterCount =
    brandIds.length +
    categories.length +
    (minPrice !== undefined || maxPrice !== undefined ? 1 : 0);

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 md:px-6">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-foreground">
          {q ? `Results for "${q}"` : 'All Products'}
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {totalCount} {totalCount === 1 ? 'product' : 'products'}
        </p>
      </div>

      <div className="flex gap-8">
        <Suspense fallback={null}>
          <FilterPanel
            brands={mockBrands}
            selectedBrandIds={brandIds}
            selectedCategories={categories}
            minPrice={params.minPrice ?? ''}
            maxPrice={params.maxPrice ?? ''}
          />
        </Suspense>

        <div className="min-w-0 flex-1">
          <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap items-center gap-2">
              {q && (
                <Link
                  href={(() => {
                    const p = new URLSearchParams();
                    if (sort !== 'newest') p.set('sort', sort);
                    brandIds.forEach((id) => p.append('brandId', id));
                    categories.forEach((c) => p.append('category', c as Category));
                    if (params.minPrice) p.set('minPrice', params.minPrice);
                    if (params.maxPrice) p.set('maxPrice', params.maxPrice);
                    const qs = p.toString();
                    return `/products${qs ? `?${qs}` : ''}`;
                  })()}
                  className="flex items-center gap-1 rounded-full border border-border px-3 py-1 text-xs font-medium hover:border-foreground"
                >
                  &ldquo;{q}&rdquo; &times;
                </Link>
              )}

              {brandIds.map((id) => {
                const brand = mockBrands.find((b) => b.id === id);
                const nextIds = brandIds.filter((b) => b !== id);
                const p = new URLSearchParams();
                if (q) p.set('q', q);
                if (sort !== 'newest') p.set('sort', sort);
                nextIds.forEach((bid) => p.append('brandId', bid));
                categories.forEach((c) => p.append('category', c as Category));
                if (params.minPrice) p.set('minPrice', params.minPrice);
                if (params.maxPrice) p.set('maxPrice', params.maxPrice);
                const qs = p.toString();
                return (
                  <Link
                    key={id}
                    href={`/products${qs ? `?${qs}` : ''}`}
                    className="flex items-center gap-1 rounded-full border border-border px-3 py-1 text-xs font-medium hover:border-foreground"
                  >
                    {brand?.name ?? id} &times;
                  </Link>
                );
              })}

              {categories.map((cat) => {
                const nextCats = categories.filter((c) => c !== cat);
                const p = new URLSearchParams();
                if (q) p.set('q', q);
                if (sort !== 'newest') p.set('sort', sort);
                brandIds.forEach((id) => p.append('brandId', id));
                nextCats.forEach((c) => p.append('category', c as Category));
                if (params.minPrice) p.set('minPrice', params.minPrice);
                if (params.maxPrice) p.set('maxPrice', params.maxPrice);
                const qs = p.toString();
                return (
                  <Link
                    key={cat}
                    href={`/products${qs ? `?${qs}` : ''}`}
                    className="flex items-center gap-1 rounded-full border border-border px-3 py-1 text-xs font-medium capitalize hover:border-foreground"
                  >
                    {cat} &times;
                  </Link>
                );
              })}

              {(minPrice !== undefined || maxPrice !== undefined) && (
                <Link
                  href={(() => {
                    const p = new URLSearchParams();
                    if (q) p.set('q', q);
                    if (sort !== 'newest') p.set('sort', sort);
                    brandIds.forEach((id) => p.append('brandId', id));
                    categories.forEach((c) => p.append('category', c as Category));
                    const qs = p.toString();
                    return `/products${qs ? `?${qs}` : ''}`;
                  })()}
                  className="flex items-center gap-1 rounded-full border border-border px-3 py-1 text-xs font-medium hover:border-foreground"
                >
                  Price filter &times;
                </Link>
              )}

              {activeFilterCount > 0 && (
                <Link
                  href={(() => {
                    const p = new URLSearchParams();
                    if (q) p.set('q', q);
                    if (sort !== 'newest') p.set('sort', sort);
                    const qs = p.toString();
                    return `/products${qs ? `?${qs}` : ''}`;
                  })()}
                  className="text-xs font-medium text-primary underline underline-offset-4"
                >
                  Clear all
                </Link>
              )}
            </div>

            <Suspense fallback={null}>
              <SortControl sortOptions={SORT_OPTIONS} currentSort={sort} />
            </Suspense>
          </div>

          <Suspense fallback={<ProductGridSkeleton />}>
            {paginatedProducts.length > 0 ? (
              <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4">
                {paginatedProducts.map((product) => (
                  <ProductCard key={product.id} product={product} />
                ))}
              </div>
            ) : (
              <EmptyState
                title="No products found"
                description="Try adjusting your filters or search term."
                action={
                  <Link
                    href="/products"
                    className="text-sm font-medium text-primary underline underline-offset-4"
                  >
                    Browse all products
                  </Link>
                }
              />
            )}
          </Suspense>

          {totalPages > 1 && (
            <div className="mt-10">
              <PaginationNav
                currentPage={currentPage}
                totalPages={totalPages}
                buildPageUrl={buildPageUrl}
              />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function PaginationNav({
  currentPage,
  totalPages,
  buildPageUrl,
}: {
  currentPage: number;
  totalPages: number;
  buildPageUrl: (page: number) => string;
}) {
  const pages = buildPageRange(currentPage, totalPages);

  return (
    <nav className="flex items-center justify-center gap-1" aria-label="Pagination">
      <Link
        href={buildPageUrl(currentPage - 1)}
        aria-disabled={currentPage <= 1}
        className={`flex h-9 w-9 items-center justify-center rounded-md border border-border text-sm transition-colors ${
          currentPage <= 1
            ? 'pointer-events-none opacity-40'
            : 'text-foreground hover:bg-muted'
        }`}
        aria-label="Previous page"
      >
        ‹
      </Link>

      {pages.map((p, idx) =>
        p === '...' ? (
          <span
            key={`ellipsis-${idx}`}
            className="flex h-9 w-9 items-center justify-center text-sm text-muted-foreground"
          >
            &hellip;
          </span>
        ) : (
          <Link
            key={p}
            href={buildPageUrl(p as number)}
            aria-current={p === currentPage ? 'page' : undefined}
            className={`flex h-9 w-9 items-center justify-center rounded-md text-sm transition-colors ${
              p === currentPage
                ? 'bg-primary font-semibold text-primary-foreground'
                : 'border border-border text-foreground hover:bg-muted'
            }`}
          >
            {p}
          </Link>
        ),
      )}

      <Link
        href={buildPageUrl(currentPage + 1)}
        aria-disabled={currentPage >= totalPages}
        className={`flex h-9 w-9 items-center justify-center rounded-md border border-border text-sm transition-colors ${
          currentPage >= totalPages
            ? 'pointer-events-none opacity-40'
            : 'text-foreground hover:bg-muted'
        }`}
        aria-label="Next page"
      >
        ›
      </Link>
    </nav>
  );
}

function buildPageRange(current: number, total: number): (number | '...')[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);

  const pages: (number | '...')[] = [1];
  if (current > 3) pages.push('...');

  const start = Math.max(2, current - 1);
  const end = Math.min(total - 1, current + 1);
  for (let i = start; i <= end; i++) pages.push(i);

  if (current < total - 2) pages.push('...');
  pages.push(total);

  return pages;
}
