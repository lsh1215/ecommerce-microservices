import { customerClient } from '@/lib/api-client';
import type { CustomerResponse, LoginResponse } from '@/types/api-responses';
import type { LoginRequest, RegisterRequest, UpdateProfileRequest } from '../types/auth.types';

export const AuthAPI = {
  register: (req: RegisterRequest) =>
    customerClient.post<CustomerResponse>('/api/customers/register', req),

  login: (req: LoginRequest) => customerClient.post<LoginResponse>('/api/customers/login', req),

  getProfile: (customerId: string | number) =>
    customerClient.get<CustomerResponse>(`/api/customers/${customerId}`),

  updateProfile: (customerId: string | number, req: UpdateProfileRequest) =>
    customerClient.put<CustomerResponse>(`/api/customers/${customerId}`, req),
};

export const CustomerAPI = AuthAPI;
