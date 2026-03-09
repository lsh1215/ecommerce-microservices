import Link from 'next/link';
import Image from 'next/image';
import { CountdownTimer } from '@/components/shared/CountdownTimer';
import { DropStatusBadge } from '@/components/shared/DropStatusBadge';
import { getLiveDrops, getUpcomingDrops, getPastDrops } from '@/mocks/drops';

export const metadata = {
  title: 'Drops — FOUNDRY',
  description: 'Live, upcoming, and past drops from FOUNDRY heritage brands.',
};

export default function DropsPage() {
  const liveDrops = getLiveDrops();
  const upcomingDrops = getUpcomingDrops();
  const pastDrops = getPastDrops();

  return (
    <div className="mx-auto max-w-7xl px-4 py-12 md:px-6">
      {/* Live Now */}
      {liveDrops.length > 0 && (
        <section className="mb-16">
          <div className="mb-8 flex items-center gap-3">
            <span className="relative flex h-2.5 w-2.5">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-red-400 opacity-75" />
              <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-red-500" />
            </span>
            <h2 className="font-heading text-2xl font-bold text-[#1a1a1a]">Live Now</h2>
          </div>

          <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
            {liveDrops.map((drop) => (
              <Link
                key={drop.id}
                href={`/drops/${drop.id}`}
                className="group relative aspect-[3/2] overflow-hidden bg-[#e8e4df]"
              >
                <Image
                  src={drop.heroImageUrl}
                  alt={drop.name}
                  fill
                  className="object-cover transition-transform duration-500 group-hover:scale-105"
                  sizes="(max-width: 768px) 100vw, 50vw"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-[#1a1a1a]/80 via-transparent" />
                <div className="absolute inset-0 flex flex-col justify-end p-6">
                  <div className="mb-2">
                    <DropStatusBadge status={drop.status} />
                  </div>
                  <p className="text-xs font-medium uppercase tracking-wide text-[#a39e93]">
                    {drop.brand.name} · {drop.brand.origin}
                  </p>
                  <h3 className="font-heading mt-1 text-xl font-bold text-white md:text-2xl">
                    {drop.name}
                  </h3>
                  <p className="mt-2 flex items-center gap-1.5 text-sm text-[#e8e4df]">
                    Closes in{' '}
                    <CountdownTimer
                      targetDate={drop.closesAt}
                      className="font-semibold text-white"
                    />
                  </p>
                  <span className="mt-4 inline-block bg-white px-6 py-2 text-xs font-semibold uppercase tracking-widest text-[#1a1a1a] transition-opacity group-hover:opacity-90">
                    Shop Now
                  </span>
                </div>
              </Link>
            ))}
          </div>
        </section>
      )}

      {/* Coming Soon */}
      {upcomingDrops.length > 0 && (
        <section className="mb-16">
          <h2 className="font-heading mb-8 text-2xl font-bold text-[#1a1a1a]">Coming Soon</h2>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            {upcomingDrops.map((drop) => (
              <div
                key={drop.id}
                className="group relative aspect-[4/3] overflow-hidden bg-[#e8e4df]"
              >
                <Image
                  src={drop.heroImageUrl}
                  alt={drop.name}
                  fill
                  className="object-cover opacity-80 transition-transform duration-500 group-hover:scale-105"
                  sizes="(max-width: 768px) 100vw, 33vw"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-[#1a1a1a]/70 via-transparent" />
                <div className="absolute inset-0 flex flex-col justify-end p-5">
                  <DropStatusBadge status={drop.status} className="mb-2" />
                  <p className="text-xs font-medium uppercase tracking-wide text-[#a39e93]">
                    {drop.brand.name}
                  </p>
                  <h3 className="font-heading mt-1 text-lg font-bold text-white">{drop.name}</h3>
                  <p className="mt-2 flex items-center gap-1.5 text-xs text-[#e8e4df]">
                    Opens in{' '}
                    <CountdownTimer
                      targetDate={drop.opensAt}
                      className="font-semibold text-white"
                    />
                  </p>
                  <button
                    type="button"
                    className="mt-4 w-full border border-white py-2 text-xs font-semibold uppercase tracking-widest text-white transition-colors hover:bg-white hover:text-[#1a1a1a]"
                  >
                    Notify Me
                  </button>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Past Drops */}
      {pastDrops.length > 0 && (
        <section>
          <h2 className="font-heading mb-8 text-2xl font-bold text-[#1a1a1a]">Archive</h2>
          <div className="divide-y divide-[#e8e4df]">
            {pastDrops.map((drop) => (
              <div key={drop.id} className="flex items-center justify-between py-4 gap-4">
                <div className="flex items-center gap-4">
                  <div className="relative h-14 w-20 shrink-0 overflow-hidden bg-[#e8e4df]">
                    <Image
                      src={drop.heroImageUrl}
                      alt={drop.name}
                      fill
                      className="object-cover opacity-60"
                      sizes="80px"
                    />
                  </div>
                  <div>
                    <p className="text-xs font-medium uppercase tracking-wide text-[#6b6560]">
                      {drop.brand.name}
                    </p>
                    <p className="text-sm font-medium text-[#1a1a1a]">{drop.name}</p>
                    <p className="text-xs text-[#a39e93]">
                      {new Date(drop.closesAt).toLocaleDateString('en-US', {
                        month: 'short',
                        day: 'numeric',
                        year: 'numeric',
                      })}
                    </p>
                  </div>
                </div>
                <DropStatusBadge status={drop.status} />
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
