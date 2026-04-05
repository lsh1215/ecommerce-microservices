'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { useState, useTransition } from 'react';
import { Filter, X } from 'lucide-react';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet';
import type { Brand, Category } from '@/types';

const CATEGORIES: { value: Category; label: string }[] = [
  { value: 'tops', label: 'Tops' },
  { value: 'bottoms', label: 'Bottoms' },
  { value: 'outerwear', label: 'Outerwear' },
  { value: 'shoes', label: 'Shoes' },
  { value: 'accessories', label: 'Accessories' },
];

interface FilterPanelProps {
  brands: Brand[];
  selectedBrandIds: string[];
  selectedCategories: string[];
  minPrice: string;
  maxPrice: string;
}

function buildFilterUrl(
  base: Record<string, string | string[]>,
  overrides: Record<string, string | string[] | undefined>,
): string {
  const merged: Record<string, string | string[]> = { ...base };

  for (const [k, v] of Object.entries(overrides)) {
    if (v === undefined || (Array.isArray(v) && v.length === 0)) {
      delete merged[k];
    } else {
      merged[k] = v;
    }
  }

  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(merged)) {
    if (Array.isArray(v)) {
      v.forEach((val) => params.append(k, val));
    } else {
      params.set(k, v);
    }
  }

  const qs = params.toString();
  return `/products${qs ? `?${qs}` : ''}`;
}

function FiltersContent({
  brands,
  selectedBrandIds,
  selectedCategories,
  minPrice,
  maxPrice,
  onClose,
}: FilterPanelProps & { onClose?: () => void }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [, startTransition] = useTransition();

  const [localMin, setLocalMin] = useState(minPrice);
  const [localMax, setLocalMax] = useState(maxPrice);

  function currentBase(): Record<string, string | string[]> {
    const base: Record<string, string | string[]> = {};
    const q = searchParams.get('q');
    const sort = searchParams.get('sort');
    if (q) base['q'] = q;
    if (sort) base['sort'] = sort;
    if (selectedBrandIds.length) base['brandId'] = selectedBrandIds;
    if (selectedCategories.length) base['category'] = selectedCategories;
    if (minPrice) base['minPrice'] = minPrice;
    if (maxPrice) base['maxPrice'] = maxPrice;
    return base;
  }

  function navigate(url: string) {
    startTransition(() => {
      router.push(url, { scroll: false });
      onClose?.();
    });
  }

  function toggleBrand(brandId: string) {
    const next = selectedBrandIds.includes(brandId)
      ? selectedBrandIds.filter((id) => id !== brandId)
      : [...selectedBrandIds, brandId];
    const url = buildFilterUrl(currentBase(), { brandId: next, page: undefined });
    navigate(url);
  }

  function toggleCategory(cat: string) {
    const next = selectedCategories.includes(cat)
      ? selectedCategories.filter((c) => c !== cat)
      : [...selectedCategories, cat];
    const url = buildFilterUrl(currentBase(), { category: next, page: undefined });
    navigate(url);
  }

  function applyPriceRange() {
    const url = buildFilterUrl(currentBase(), {
      minPrice: localMin || undefined,
      maxPrice: localMax || undefined,
      page: undefined,
    });
    navigate(url);
  }

  function clearAll() {
    const q = searchParams.get('q');
    const sort = searchParams.get('sort');
    const base: Record<string, string> = {};
    if (q) base['q'] = q;
    if (sort) base['sort'] = sort;
    const qs = new URLSearchParams(base).toString();
    navigate(`/products${qs ? `?${qs}` : ''}`);
  }

  const hasActiveFilters =
    selectedBrandIds.length > 0 || selectedCategories.length > 0 || minPrice || maxPrice;

  return (
    <div className="flex flex-col gap-8">
      <div>
        <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          Brand
        </p>
        <div className="flex flex-col gap-2">
          {brands.map((brand) => {
            const checked = selectedBrandIds.includes(brand.id);
            return (
              <label
                key={brand.id}
                className="flex cursor-pointer items-center gap-2 text-sm"
              >
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() => toggleBrand(brand.id)}
                  className="h-4 w-4 rounded border-border accent-primary"
                />
                <span className={checked ? 'font-medium text-foreground' : 'text-foreground'}>
                  {brand.name}
                </span>
              </label>
            );
          })}
        </div>
      </div>

      <div>
        <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          Category
        </p>
        <div className="flex flex-col gap-2">
          {CATEGORIES.map((cat) => {
            const checked = selectedCategories.includes(cat.value);
            return (
              <label
                key={cat.value}
                className="flex cursor-pointer items-center gap-2 text-sm"
              >
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() => toggleCategory(cat.value)}
                  className="h-4 w-4 rounded border-border accent-primary"
                />
                <span className={checked ? 'font-medium text-foreground' : 'text-foreground'}>
                  {cat.label}
                </span>
              </label>
            );
          })}
        </div>
      </div>

      <div>
        <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          Price Range (KRW)
        </p>
        <div className="flex items-center gap-2">
          <input
            type="number"
            placeholder="Min"
            value={localMin}
            onChange={(e) => setLocalMin(e.target.value)}
            min={0}
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm text-foreground placeholder:text-muted-foreground focus:border-primary focus:outline-none"
          />
          <span className="text-muted-foreground">–</span>
          <input
            type="number"
            placeholder="Max"
            value={localMax}
            onChange={(e) => setLocalMax(e.target.value)}
            min={0}
            className="w-full rounded-md border border-border bg-background px-2 py-1.5 text-sm text-foreground placeholder:text-muted-foreground focus:border-primary focus:outline-none"
          />
        </div>
        <button
          type="button"
          onClick={applyPriceRange}
          className="mt-2 w-full rounded-md border border-border px-3 py-1.5 text-xs font-medium text-foreground transition-colors hover:bg-muted"
        >
          Apply
        </button>
      </div>

      {hasActiveFilters && (
        <button
          type="button"
          onClick={clearAll}
          className="flex items-center gap-1 text-xs font-medium text-primary underline underline-offset-4"
        >
          <X size={12} />
          Clear all filters
        </button>
      )}
    </div>
  );
}

export function FilterPanel(props: FilterPanelProps) {
  const [open, setOpen] = useState(false);

  return (
    <>
      {/* Mobile trigger */}
      <div className="md:hidden">
        <Sheet open={open} onOpenChange={setOpen}>
          <SheetTrigger
            className="flex items-center gap-2 rounded-md border border-border px-3 py-2 text-sm font-medium text-foreground transition-colors hover:bg-muted"
            aria-label="Open filters"
          >
            <Filter size={16} />
            Filters
            {(props.selectedBrandIds.length > 0 ||
              props.selectedCategories.length > 0 ||
              props.minPrice ||
              props.maxPrice) && (
              <span className="flex h-5 w-5 items-center justify-center rounded-full bg-primary text-[10px] font-bold text-primary-foreground">
                {props.selectedBrandIds.length +
                  props.selectedCategories.length +
                  (props.minPrice || props.maxPrice ? 1 : 0)}
              </span>
            )}
          </SheetTrigger>
          <SheetContent side="left" className="overflow-y-auto p-6">
            <SheetHeader className="mb-6 p-0">
              <SheetTitle>Filters</SheetTitle>
            </SheetHeader>
            <FiltersContent {...props} onClose={() => setOpen(false)} />
          </SheetContent>
        </Sheet>
      </div>

      {/* Desktop sidebar */}
      <aside className="hidden w-52 shrink-0 md:block">
        <FiltersContent {...props} />
      </aside>
    </>
  );
}
