export type AddressLabel = 'HOME' | 'WORK' | 'OTHER';

export interface CreateAddressRequest {
  label: AddressLabel;
  recipientName: string;
  phone: string;
  zipCode: string;
  address1: string;
  address2?: string;
  isDefault?: boolean;
}

export type UpdateAddressRequest = CreateAddressRequest;
