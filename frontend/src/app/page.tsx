export default function Home() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center p-24">
      <div className="text-center">
        <h1 className="mb-4 text-4xl font-bold tracking-tight text-gray-900">
          E-Commerce Platform
        </h1>
        <p className="mb-8 text-lg text-gray-600">
          Project scaffold initialized. Phase 1 domain implementation starting soon.
        </p>
        <a
          href="http://localhost:8080/actuator/health"
          target="_blank"
          rel="noopener noreferrer"
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          API Health Check
        </a>
      </div>
    </main>
  );
}
