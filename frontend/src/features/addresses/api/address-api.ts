import { customerClient } from '@/lib/api-client';
import type { AddressResponse } from '@/types/api-responses';
import type { CreateAddressRequest, UpdateAddressRequest } from '../types/address.types';

export const AddressAPI = {
  list: (customerId: string | number) =>
    customerClient.get<AddressResponse[]>(`/api/customers/${customerId}/addresses`),

  create: (customerId: string | number, req: CreateAddressRequest) =>
    customerClient.post<AddressResponse>(`/api/customers/${customerId}/addresses`, req),

  update: (customerId: string | number, addressId: string | number, req: UpdateAddressRequest) =>
    customerClient.put<AddressResponse>(
      `/api/customers/${customerId}/addresses/${addressId}`,
      req,
    ),

  remove: (customerId: string | number, addressId: string | number) =>
    customerClient.delete<void>(`/api/customers/${customerId}/addresses/${addressId}`),
};
