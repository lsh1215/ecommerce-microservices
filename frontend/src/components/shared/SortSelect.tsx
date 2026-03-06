'use client';

import { useRouter, useSearchParams } from 'next/navigation';

interface SortSelectProps {
  currentSort: string;
  options: { value: string; label: string }[];
}

export function SortSelect({ currentSort, options }: SortSelectProps) {
  const router = useRouter();
  const searchParams = useSearchParams();

  function handleChange(value: string) {
    const params = new URLSearchParams(searchParams.toString());
    params.delete('page');
    if (value === 'newest') {
      params.delete('sort');
    } else {
      params.set('sort', value);
    }
    const qs = params.toString();
    router.push(`/products${qs ? `?${qs}` : ''}`);
  }

  return (
    <div className="flex items-center gap-2">
      <label htmlFor="sort-select" className="text-xs text-[#6b6560]">
        Sort:
      </label>
      <select
        id="sort-select"
        value={currentSort}
        onChange={(e) => handleChange(e.target.value)}
        className="border border-[#e8e4df] bg-white px-2 py-1.5 text-xs text-[#1a1a1a] focus:outline-none"
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </div>
  );
}
