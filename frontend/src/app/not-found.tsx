import Link from 'next/link';

export default function NotFound() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center p-24">
      <div className="text-center">
        <h1 className="mb-2 text-6xl font-bold text-gray-900">404</h1>
        <h2 className="mb-4 text-2xl font-semibold text-gray-700">Page Not Found</h2>
        <p className="mb-8 text-gray-500">
          The page you are looking for does not exist or has been moved.
        </p>
        <Link
          href="/"
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          Go Home
        </Link>
      </div>
    </main>
  );
}
