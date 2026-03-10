import { apiClient } from '@/lib/api-client';
import type { InventoryResponse } from '@/types/api-responses';

export const InventoryAPI = {
  getByVariantId: (variantId: number) =>
    apiClient.get<InventoryResponse>(`/api/inventory/variants/${variantId}`),
};
