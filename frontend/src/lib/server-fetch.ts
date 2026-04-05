import { SERVICE_BASE_URLS, type ServiceName } from './constants';
import type { BackendApiResponse } from '@/types';

/**
 * Server component helper that fetches from a named service and unwraps
 * the backend ApiResponse envelope. Returns null on any failure so pages
 * can render fallbacks without bubbling exceptions.
 */
export async function serverFetch<T>(
  service: ServiceName,
  path: string,
  init?: RequestInit & { next?: { revalidate?: number | false; tags?: string[] } },
): Promise<T | null> {
  try {
    const res = await fetch(`${SERVICE_BASE_URLS[service]}${path}`, {
      cache: 'no-store',
      ...init,
      headers: {
        Accept: 'application/json',
        ...init?.headers,
      },
    });
    if (!res.ok) return null;
    const json = (await res.json()) as BackendApiResponse<T>;
    return json.success ? json.data : null;
  } catch {
    return null;
  }
}
