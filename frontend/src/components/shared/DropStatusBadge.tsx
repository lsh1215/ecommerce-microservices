import type { DropStatus } from '@/types';

interface DropStatusBadgeProps {
  status: DropStatus;
  className?: string;
}

const config: Record<DropStatus, { label: string; classes: string; pulse?: boolean }> = {
  ANNOUNCED: {
    label: 'Coming Soon',
    classes: 'bg-blue-100 text-blue-700 border border-blue-200',
  },
  OPEN: {
    label: 'Open',
    classes: 'bg-red-100 text-red-700 border border-red-200',
    pulse: true,
  },
  SELLING: {
    label: 'Live Now',
    classes: 'bg-red-100 text-red-700 border border-red-200',
    pulse: true,
  },
  SOLD_OUT: {
    label: 'Sold Out',
    classes: 'bg-gray-100 text-gray-500 border border-gray-200',
  },
  CLOSED: {
    label: 'Ended',
    classes: 'bg-gray-100 text-gray-500 border border-gray-200',
  },
};

export function DropStatusBadge({ status, className = '' }: DropStatusBadgeProps) {
  const { label, classes, pulse } = config[status];

  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2 py-0.5 text-xs font-medium tracking-wide uppercase ${classes} ${className}`}
    >
      {pulse && (
        <span className="relative flex h-1.5 w-1.5">
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-red-400 opacity-75" />
          <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-red-500" />
        </span>
      )}
      {label}
    </span>
  );
}
