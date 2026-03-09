'use client';

import Link from 'next/link';

interface ErrorPageProps {
  error: Error & { digest?: string };
  reset: () => void;
}

export default function ErrorPage({ reset }: ErrorPageProps) {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center px-4">
      <div className="text-center">
        <p className="mb-4 text-xs font-semibold uppercase tracking-widest text-[#6b6560]">Error</p>
        <h1 className="font-heading text-5xl font-bold text-[#1a1a1a]">Something went wrong</h1>
        <p className="mt-6 max-w-md text-base leading-relaxed text-[#6b6560]">
          We encountered an unexpected error. Please try again or return to the home page.
        </p>
        <div className="mt-10 flex flex-col items-center gap-3 sm:flex-row sm:justify-center">
          <button
            type="button"
            onClick={reset}
            className="inline-block bg-[#c4633e] px-8 py-3 text-sm font-semibold uppercase tracking-widest text-white transition-opacity hover:opacity-90"
          >
            Try Again
          </button>
          <Link
            href="/"
            className="inline-block border border-[#1a1a1a] px-8 py-3 text-sm font-semibold uppercase tracking-widest text-[#1a1a1a] transition-colors hover:bg-[#1a1a1a] hover:text-white"
          >
            Back to Home
          </Link>
        </div>
      </div>
    </main>
  );
}
