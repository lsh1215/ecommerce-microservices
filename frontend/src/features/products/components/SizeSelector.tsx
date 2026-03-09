'use client';

import { useState, useEffect } from 'react';
import type { ProductSize } from '@/types';

interface SizeSelectorProps {
  sizes: ProductSize[];
  onSizeChange: (size: string | null) => void;
}

export function SizeSelector({ sizes, onSizeChange }: SizeSelectorProps) {
  const [selected, setSelected] = useState<string | null>(null);

  useEffect(() => {
    onSizeChange(selected);
  }, [selected, onSizeChange]);

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <p className="text-xs font-semibold uppercase tracking-wider text-[#6b6560]">Size</p>
        {selected && (
          <p className="text-xs text-[#6b6560]">
            {sizes.find((s) => s.label === selected)?.stock === 0
              ? 'Sold Out'
              : sizes.find((s) => s.label === selected)!.stock <= 3
                ? `Only ${sizes.find((s) => s.label === selected)!.stock} left`
                : 'In Stock'}
          </p>
        )}
      </div>
      <div className="flex flex-wrap gap-2">
        {sizes.map((size) => {
          const isSoldOut = size.stock === 0;
          const isSelected = selected === size.label;

          return (
            <button
              key={size.label}
              type="button"
              disabled={isSoldOut}
              onClick={() => setSelected(isSelected ? null : size.label)}
              className={`min-w-[44px] px-3 py-2.5 text-sm font-medium transition-colors ${
                isSoldOut
                  ? 'cursor-not-allowed border border-[#e8e4df] text-[#a39e93] line-through'
                  : isSelected
                    ? 'border border-[#1a1a1a] bg-[#1a1a1a] text-white'
                    : 'border border-[#e8e4df] text-[#1a1a1a] hover:border-[#1a1a1a]'
              }`}
            >
              {size.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}
