'use client';

import { useRouter, useSearchParams } from 'next/navigation';

interface SortOption {
  value: string;
  label: string;
}

interface SortControlProps {
  sortOptions: SortOption[];
  currentSort: string;
}

export function SortControl({ sortOptions, currentSort }: SortControlProps) {
  const router = useRouter();
  const searchParams = useSearchParams();

  function handleChange(value: string) {
    const params = new URLSearchParams(searchParams.toString());
    if (value === 'newest') {
      params.delete('sort');
    } else {
      params.set('sort', value);
    }
    params.delete('page');
    const qs = params.toString();
    router.push(`/products${qs ? `?${qs}` : ''}`, { scroll: false });
  }

  return (
    <select
      value={currentSort}
      onChange={(e) => handleChange(e.target.value)}
      className="rounded-md border border-border bg-background px-3 py-1.5 text-sm text-foreground focus:outline-none"
      aria-label="Sort products"
    >
      {sortOptions.map((opt) => (
        <option key={opt.value} value={opt.value}>
          {opt.label}
        </option>
      ))}
    </select>
  );
}
