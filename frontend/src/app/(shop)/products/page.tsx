import { Suspense } from 'react';
import Link from 'next/link';
import { ProductCard } from '@/components/shared/ProductCard';
import { ProductGridSkeleton } from '@/components/shared/Skeleton';
import { EmptyState } from '@/components/shared/EmptyState';
import { mockProducts } from '@/mocks/products';
import type { Category } from '@/types';

export const metadata = {
  title: 'Products',
  description: 'Browse all products.',
};

const CATEGORIES: { value: Category; label: string }[] = [
  { value: 'tops', label: 'Tops' },
  { value: 'bottoms', label: 'Bottoms' },
  { value: 'outerwear', label: 'Outerwear' },
  { value: 'shoes', label: 'Shoes' },
  { value: 'accessories', label: 'Accessories' },
];

const SORT_OPTIONS = [
  { value: 'newest', label: 'Newest' },
  { value: 'price_asc', label: 'Price: Low to High' },
  { value: 'price_desc', label: 'Price: High to Low' },
];

interface ProductsPageProps {
  searchParams: Promise<{
    q?: string;
    category?: string;
    sort?: string;
    page?: string;
  }>;
}

export default async function ProductsPage({ searchParams }: ProductsPageProps) {
  const params = await searchParams;
  const { q, category, sort = 'newest' } = params;

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

  if (category) {
    products = products.filter((p) => p.category === category);
  }

  if (sort === 'price_asc') {
    products = products.sort((a, b) => a.price - b.price);
  } else if (sort === 'price_desc') {
    products = products.sort((a, b) => b.price - a.price);
  } else {
    products = products.sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    );
  }

  function buildFilterUrl(overrides: Record<string, string | undefined>): string {
    const base: Record<string, string | undefined> = {
      ...(q && { q }),
      ...(category && { category }),
      ...(sort !== 'newest' && { sort }),
    };
    const merged = { ...base, ...overrides };
    const clean = Object.fromEntries(
      Object.entries(merged).filter(([, v]) => v !== undefined),
    ) as Record<string, string>;
    const qs = new URLSearchParams(clean).toString();
    return `/products${qs ? `?${qs}` : ''}`;
  }

  const activeFiltersCount = [q, category].filter(Boolean).length;

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 md:px-6">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-foreground">
          {q ? `Results for "${q}"` : 'All Products'}
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {products.length} {products.length === 1 ? 'product' : 'products'}
        </p>
      </div>

      <div className="flex gap-8">
        <aside className="hidden w-48 shrink-0 md:block">
          <div className="flex flex-col gap-8">
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                Search
              </p>
              <form action="/products" method="get">
                <input
                  name="q"
                  type="search"
                  defaultValue={q ?? ''}
                  placeholder="Search products..."
                  className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:border-primary focus:outline-none"
                />
              </form>
            </div>

            <div>
              <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                Category
              </p>
              <div className="flex flex-col gap-2">
                {CATEGORIES.map((cat) => (
                  <a
                    key={cat.value}
                    href={buildFilterUrl({
                      category: category === cat.value ? undefined : cat.value,
                    })}
                    className={`text-sm transition-colors hover:text-primary ${
                      category === cat.value
                        ? 'font-semibold text-primary'
                        : 'text-foreground'
                    }`}
                  >
                    {cat.label}
                  </a>
                ))}
              </div>
            </div>

            {activeFiltersCount > 0 && (
              <Link
                href="/products"
                className="text-xs font-medium text-primary underline underline-offset-4"
              >
                Clear all filters
              </Link>
            )}
          </div>
        </aside>

        <div className="min-w-0 flex-1">
          <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap items-center gap-2">
              {q && (
                <a
                  href={buildFilterUrl({ q: undefined })}
                  className="flex items-center gap-1 rounded-full border border-border px-3 py-1 text-xs font-medium hover:border-foreground"
                >
                  &ldquo;{q}&rdquo; &times;
                </a>
              )}
              {category && (
                <a
                  href={buildFilterUrl({ category: undefined })}
                  className="flex items-center gap-1 rounded-full border border-border px-3 py-1 text-xs font-medium capitalize hover:border-foreground"
                >
                  {category} &times;
                </a>
              )}
            </div>

            <select
              name="sort"
              defaultValue={sort}
              onChange={(e) => {
                const url = buildFilterUrl({ sort: e.target.value === 'newest' ? undefined : e.target.value });
                window.location.href = url;
              }}
              className="rounded-md border border-border bg-background px-3 py-1.5 text-sm text-foreground focus:outline-none"
            >
              {SORT_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>

          <Suspense fallback={<ProductGridSkeleton />}>
            {products.length > 0 ? (
              <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4">
                {products.map((product) => (
                  <ProductCard key={product.id} product={product} />
                ))}
              </div>
            ) : (
              <EmptyState
                title="No products found"
                description="Try adjusting your filters."
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
        </div>
      </div>
    </div>
  );
}
