import Link from 'next/link';

export default function NotFound() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center px-4">
      <div className="text-center">
        <p className="mb-4 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">
          Page Not Found
        </p>
        <h1 className="font-heading text-7xl font-bold text-[#1a1a1a]">404</h1>
        <p className="mt-6 max-w-md text-base leading-relaxed text-[#6b6560]">
          The page you are looking for does not exist or has been moved. It may have been part of a
          drop that has ended.
        </p>
        <div className="mt-10 flex flex-col items-center gap-3 sm:flex-row sm:justify-center">
          <Link
            href="/"
            className="inline-block bg-[#1a1a1a] px-8 py-3 text-sm font-semibold uppercase tracking-widest text-white transition-opacity hover:opacity-90"
          >
            Back to Home
          </Link>
          <Link
            href="/drops"
            className="inline-block border border-[#1a1a1a] px-8 py-3 text-sm font-semibold uppercase tracking-widest text-[#1a1a1a] transition-colors hover:bg-[#1a1a1a] hover:text-white"
          >
            View Drops
          </Link>
        </div>
      </div>
    </main>
  );
}
