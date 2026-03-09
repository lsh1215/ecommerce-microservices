import { API_BASE_URL } from './constants';
import type { ApiResponse } from '@/types';

export async function serverFetch<T>(path: string): Promise<T | null> {
  try {
    const res = await fetch(`${API_BASE_URL}${path}`, { cache: 'no-store' });
    const json: ApiResponse<T> = await res.json();
    return json.success ? json.data : null;
  } catch {
    return null;
  }
}
