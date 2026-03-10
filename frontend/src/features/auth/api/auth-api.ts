import { apiClient } from '@/lib/api-client';
import type { LoginResponse, CustomerResponse } from '@/types/api-responses';
import type { LoginRequest, RegisterRequest } from '../types/auth.types';

export const AuthAPI = {
  register: (req: RegisterRequest) => apiClient.post<CustomerResponse>('/api/customers', req),

  login: (req: LoginRequest) => apiClient.post<LoginResponse>('/api/customers/login', req),

  getProfile: (publicId: string) => apiClient.get<CustomerResponse>(`/api/customers/${publicId}`),
};
