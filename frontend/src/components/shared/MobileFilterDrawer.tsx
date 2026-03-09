'use client';

import { useState, type ReactNode } from 'react';
import { SlidersHorizontal, X } from 'lucide-react';

interface MobileFilterDrawerProps {
  children: ReactNode;
  resultCount: number;
}

export function MobileFilterDrawer({ children, resultCount }: MobileFilterDrawerProps) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="flex items-center gap-2 border border-[#e8e4df] px-3 py-2 text-xs font-medium text-[#1a1a1a] md:hidden"
      >
        <SlidersHorizontal size={14} strokeWidth={1.5} />
        Filter
      </button>

      {open && (
        <div className="fixed inset-0 z-50 md:hidden">
          <div className="absolute inset-0 bg-black/40" onClick={() => setOpen(false)} />
          <div className="absolute inset-x-0 bottom-0 max-h-[85vh] overflow-y-auto bg-[#faf9f6] px-4 pb-6 pt-4 animate-in">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-sm font-semibold uppercase tracking-wider text-[#1a1a1a]">
                Filters
              </h2>
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="flex h-8 w-8 items-center justify-center text-[#6b6560]"
                aria-label="Close filters"
              >
                <X size={18} />
              </button>
            </div>

            <div className="flex flex-col gap-6">{children}</div>

            <button
              type="button"
              onClick={() => setOpen(false)}
              className="mt-6 w-full bg-[#1a1a1a] px-6 py-3.5 text-sm font-semibold uppercase tracking-widest text-white"
            >
              Show {resultCount} {resultCount === 1 ? 'Result' : 'Results'}
            </button>
          </div>
        </div>
      )}
    </>
  );
}
