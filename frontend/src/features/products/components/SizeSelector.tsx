'use client';

import { useState, useEffect } from 'react';
import type { ProductVariant } from '@/types';

interface SizeSelectorProps {
  variants: ProductVariant[];
  onVariantChange: (variant: ProductVariant | null) => void;
}

export function SizeSelector({ variants, onVariantChange }: SizeSelectorProps) {
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const uniqueSizes = variants.reduce<ProductVariant[]>((acc, v) => {
    if (!acc.find((u) => u.size === v.size)) acc.push(v);
    return acc;
  }, []);

  useEffect(() => {
    const found = variants.find((v) => v.id === selectedId) ?? null;
    onVariantChange(found);
  }, [selectedId, variants, onVariantChange]);

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Size</p>
        {selectedId &&
          (() => {
            const selected = variants.find((v) => v.id === selectedId);
            if (!selected) return null;
            return (
              <p className="text-xs text-muted-foreground">
                {selected.stockQuantity === 0
                  ? 'Sold Out'
                  : selected.stockQuantity <= 3
                    ? `Only ${selected.stockQuantity} left`
                    : 'In Stock'}
              </p>
            );
          })()}
      </div>
      <div className="flex flex-wrap gap-2">
        {uniqueSizes.map((variant) => {
          const isSoldOut = variant.stockQuantity === 0;
          const isSelected = selectedId === variant.id;

          return (
            <button
              key={variant.id}
              type="button"
              disabled={isSoldOut}
              onClick={() => setSelectedId(isSelected ? null : variant.id)}
              className={`min-w-[44px] rounded-md px-3 py-2 text-sm font-medium transition-colors ${
                isSoldOut
                  ? 'cursor-not-allowed border border-border text-muted-foreground line-through'
                  : isSelected
                    ? 'border border-foreground bg-foreground text-background'
                    : 'border border-border text-foreground hover:border-foreground'
              }`}
            >
              {variant.size}
            </button>
          );
        })}
      </div>
    </div>
  );
}
