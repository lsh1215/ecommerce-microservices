export interface ErrorDetail {
  code: string;
  message: string;
}

/**
 * Wire format from backend matches `{ success, message, data }`.
 * We normalize it in the API client so callers can also rely on `error`
 * being populated when `success === false`.
 */
export interface BackendApiResponse<T> {
  success: boolean;
  message?: string | null;
  data: T | null;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  error: ErrorDetail | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}
