'use client';

import { Minus, Plus } from 'lucide-react';

interface QuantitySelectorProps {
  quantity: number;
  max?: number;
  onChange: (quantity: number) => void;
}

export function QuantitySelector({ quantity, max = 99, onChange }: QuantitySelectorProps) {
  return (
    <div className="flex items-center gap-0">
      <button
        type="button"
        onClick={() => onChange(Math.max(1, quantity - 1))}
        disabled={quantity <= 1}
        className="flex h-10 w-10 items-center justify-center rounded-l-md border border-border text-foreground transition-colors hover:bg-muted disabled:pointer-events-none disabled:opacity-40"
        aria-label="Decrease quantity"
      >
        <Minus size={14} />
      </button>

      <div className="flex h-10 w-12 items-center justify-center border-y border-border text-sm font-medium text-foreground">
        {quantity}
      </div>

      <button
        type="button"
        onClick={() => onChange(Math.min(max, quantity + 1))}
        disabled={quantity >= max}
        className="flex h-10 w-10 items-center justify-center rounded-r-md border border-border text-foreground transition-colors hover:bg-muted disabled:pointer-events-none disabled:opacity-40"
        aria-label="Increase quantity"
      >
        <Plus size={14} />
      </button>
    </div>
  );
}
