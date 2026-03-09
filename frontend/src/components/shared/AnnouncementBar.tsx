'use client';

import Link from 'next/link';
import { CountdownTimer } from './CountdownTimer';
import { getLiveDrops } from '@/mocks/drops';

export function AnnouncementBar() {
  const liveDrops = getLiveDrops();
  const activeDrop = liveDrops[0];

  if (!activeDrop) return null;

  return (
    <div className="bg-[#c4633e] px-4 py-2">
      <div className="mx-auto flex max-w-7xl items-center justify-center gap-3 text-white">
        <span className="relative flex h-2 w-2 shrink-0">
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-white opacity-75" />
          <span className="relative inline-flex h-2 w-2 rounded-full bg-white" />
        </span>
        <Link
          href={`/drops/${activeDrop.id}`}
          className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider hover:underline"
        >
          <span>LIVE NOW: {activeDrop.name}</span>
          <span className="hidden sm:inline">—</span>
          <CountdownTimer
            targetDate={activeDrop.closesAt}
            className="hidden text-xs font-semibold text-white sm:inline"
          />
        </Link>
      </div>
    </div>
  );
}
