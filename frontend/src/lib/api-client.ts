import type { ApiResponse, BackendApiResponse } from '@/types';
import { SERVICE_BASE_URLS, type ServiceName } from './constants';

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  /** Next.js fetch cache control. Defaults to no-store for dynamic data. */
  cache?: RequestCache;
  next?: { revalidate?: number | false; tags?: string[] };
}

function normalize<T>(json: unknown, httpStatus: number, statusText: string): ApiResponse<T> {
  if (json && typeof json === 'object' && 'success' in json) {
    const body = json as BackendApiResponse<T>;
    if (body.success) {
      return { success: true, data: body.data, error: null };
    }
    return {
      success: false,
      data: null,
      error: {
        code: String(httpStatus),
        message: body.message ?? statusText ?? 'Request failed',
      },
    };
  }

  return {
    success: false,
    data: null,
    error: {
      code: String(httpStatus),
      message: statusText || 'Unexpected response shape',
    },
  };
}

async function request<T>(
  service: ServiceName,
  path: string,
  { body, headers, cache, next, method = 'GET', ...rest }: RequestOptions = {},
): Promise<ApiResponse<T>> {
  const url = `${SERVICE_BASE_URLS[service]}${path}`;

  try {
    const response = await fetch(url, {
      method,
      ...rest,
      cache: cache ?? (next ? undefined : 'no-store'),
      next,
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });

    let json: unknown = null;
    const text = await response.text();
    if (text) {
      try {
        json = JSON.parse(text);
      } catch {
        return {
          success: false,
          data: null,
          error: {
            code: String(response.status),
            message: 'Invalid JSON response',
          },
        };
      }
    }

    return normalize<T>(json, response.status, response.statusText);
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Network error';
    return {
      success: false,
      data: null,
      error: { code: 'NETWORK_ERROR', message },
    };
  }
}

function makeServiceClient(service: ServiceName) {
  return {
    get<T>(path: string, options?: RequestOptions): Promise<ApiResponse<T>> {
      return request<T>(service, path, { ...options, method: 'GET' });
    },
    post<T>(path: string, body?: unknown, options?: RequestOptions): Promise<ApiResponse<T>> {
      return request<T>(service, path, { ...options, method: 'POST', body });
    },
    put<T>(path: string, body?: unknown, options?: RequestOptions): Promise<ApiResponse<T>> {
      return request<T>(service, path, { ...options, method: 'PUT', body });
    },
    patch<T>(path: string, body?: unknown, options?: RequestOptions): Promise<ApiResponse<T>> {
      return request<T>(service, path, { ...options, method: 'PATCH', body });
    },
    delete<T>(path: string, options?: RequestOptions): Promise<ApiResponse<T>> {
      return request<T>(service, path, { ...options, method: 'DELETE' });
    },
  };
}

export const productClient = makeServiceClient('product');
export const orderClient = makeServiceClient('order');
export const paymentClient = makeServiceClient('payment');
export const customerClient = makeServiceClient('customer');

/**
 * Legacy default client — routes to the product service. Prefer the
 * service-specific clients above when adding new call sites.
 */
export const apiClient = productClient;

export class ApiError extends Error {
  readonly code: string;
  constructor(code: string, message: string) {
    super(message);
    this.code = code;
    this.name = 'ApiError';
  }
}

/** Unwraps an ApiResponse, throwing ApiError on failure. */
export function unwrap<T>(res: ApiResponse<T>): T {
  if (!res.success || res.data == null) {
    throw new ApiError(res.error?.code ?? 'UNKNOWN', res.error?.message ?? 'Request failed');
  }
  return res.data;
}
