import { ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';

interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  className?: string;
}

export function Pagination({ page, totalPages, onPageChange, className }: PaginationProps) {
  if (totalPages <= 1) return null;

  const pages = buildPageRange(page, totalPages);

  return (
    <nav
      className={cn('flex items-center justify-center gap-1', className)}
      aria-label="Pagination"
    >
      <button
        onClick={() => onPageChange(page - 1)}
        disabled={page <= 1}
        className="flex h-9 w-9 items-center justify-center rounded-md border border-border text-sm text-foreground transition-colors hover:bg-muted disabled:pointer-events-none disabled:opacity-40"
        aria-label="Previous page"
      >
        <ChevronLeft size={16} />
      </button>

      {pages.map((p, idx) =>
        p === '...' ? (
          <span key={`ellipsis-${idx}`} className="flex h-9 w-9 items-center justify-center text-sm text-muted-foreground">
            &hellip;
          </span>
        ) : (
          <button
            key={p}
            onClick={() => onPageChange(p as number)}
            aria-current={p === page ? 'page' : undefined}
            className={cn(
              'flex h-9 w-9 items-center justify-center rounded-md text-sm transition-colors',
              p === page
                ? 'bg-primary font-semibold text-primary-foreground'
                : 'border border-border text-foreground hover:bg-muted',
            )}
          >
            {p}
          </button>
        ),
      )}

      <button
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages}
        className="flex h-9 w-9 items-center justify-center rounded-md border border-border text-sm text-foreground transition-colors hover:bg-muted disabled:pointer-events-none disabled:opacity-40"
        aria-label="Next page"
      >
        <ChevronRight size={16} />
      </button>
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
