import { apiClient } from './client';

export type StockMovementType = 'IN' | 'OUT' | 'ADJUSTMENT';
export type StockReferenceType = 'ENCOUNTER' | 'PURCHASE_ORDER' | 'MANUAL';

export interface InventoryItem {
  id: string;
  name: string;
  category: string | null;
  skuBarcode: string | null;
  quantityOnHand: number;
  reorderThreshold: number;
  belowReorderThreshold: boolean;
  expiryDate: string | null;
  lotNumber: string | null;
  unitCost: number | null;
  /** TARBİL ürün adı; doluysa kalem TARBİL düşümünü bekler (spec 2026-10-09). */
  tarbilProductName: string | null;
}

export type PendingDeductionReference = 'ENCOUNTER' | 'VACCINATION' | 'PRESCRIPTION';

export interface PendingDeduction {
  id: string;
  inventoryItemId: string;
  itemName: string;
  lotNumber: string | null;
  quantity: number;
  referenceType: PendingDeductionReference;
  referenceId: string;
  createdAt: string;
}

export interface CreateInventoryItemPayload {
  name: string;
  category?: string;
  skuBarcode?: string;
  initialQuantity: number;
  reorderThreshold: number;
  expiryDate?: string;
  lotNumber?: string;
  unitCost?: number;
}

export interface StockMovement {
  id: string;
  movementType: StockMovementType;
  quantity: number;
  referenceType: StockReferenceType;
  referenceId: string | null;
  createdAt: string;
}

export const inventoryApi = {
  list: () => apiClient.get<InventoryItem[]>('/api/v1/inventory-items'),
  create: (payload: CreateInventoryItemPayload) => apiClient.postForId('/api/v1/inventory-items', payload),
  recordMovement: (id: string, payload: { movementType: StockMovementType; quantity: number }) =>
    apiClient.post<void>(`/api/v1/inventory-items/${id}/movements`, payload),
  listMovements: (id: string) => apiClient.get<StockMovement[]>(`/api/v1/inventory-items/${id}/movements`),
  listPendingDeductions: () => apiClient.get<PendingDeduction[]>('/api/v1/inventory/pending-deductions'),
  settleVetlyOnly: (referenceType: PendingDeductionReference, referenceId: string) =>
    apiClient.post<void>(`/api/v1/inventory/pending-deductions/${referenceType}/${referenceId}/settle-vetly-only`),
};
