import { apiClient } from './client';

export type InvoiceStatus = 'DRAFT' | 'ISSUED' | 'PARTIALLY_PAID' | 'PAID' | 'VOID';
export type InvoiceLineSource = 'AUTO_CHARGE_CAPTURE' | 'MANUAL';
export type PaymentMethod = 'CARD' | 'CASH' | 'TEXT_TO_PAY' | 'INSTALLMENT';
export type CashRegisterStatus = 'OPEN' | 'CLOSED';

export interface InvoiceSummary {
  id: string;
  ownerId: string;
  ownerName: string;
  totalAmount: number;
  status: InvoiceStatus;
  issuedAt: string | null;
}

export interface InvoiceLine {
  id: string;
  description: string;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
  vatRate: number;
  vatAmount: number;
  lineTotal: number;
  source: InvoiceLineSource;
}

export interface InvoicePayment {
  id: string;
  method: PaymentMethod;
  amount: number;
  paidAt: string;
}

export interface InvoiceDetail extends InvoiceSummary {
  encounterId: string | null;
  eInvoiceRef: string | null;
  taxAmount: number;
  paidAmount: number;
  lines: InvoiceLine[];
  payments: InvoicePayment[];
}

export interface OwnerBalance {
  ownerId: string;
  ownerName: string;
  ownerPhone: string;
  outstandingBalance: number;
  smsConsent: boolean;
  whatsappConsent: boolean;
}

export interface MonthlyRevenue {
  month: string;
  revenue: number;
}

export interface BranchRevenue {
  branchId: string;
  branchName: string;
  revenue: number;
}

export interface RevenueSummary {
  monthlyTrend: MonthlyRevenue[];
  branchBreakdown: BranchRevenue[];
  currentMonthRevenue: number;
  previousMonthRevenue: number;
}

export interface CashRegisterSession {
  id: string;
  branchId: string;
  openedByStaffId: string;
  openingBalance: number;
  openedAt: string;
  closedByStaffId: string | null;
  closingBalance: number | null;
  closedAt: string | null;
  status: CashRegisterStatus;
  notes: string | null;
}

export interface QuickSaleLine {
  inventoryItemId?: string;
  description: string;
  quantity: number;
  unitPrice: number;
  vatRate: number;
}

export interface TodaySalesSummary {
  totalAmount: number;
  saleCount: number;
}

export const billingApi = {
  listInvoices: () => apiClient.get<InvoiceSummary[]>('/api/v1/invoices'),
  getInvoice: (id: string) => apiClient.get<InvoiceDetail>(`/api/v1/invoices/${id}`),
  createInvoice: (ownerId: string) => apiClient.postForId('/api/v1/invoices', { ownerId }),
  addLine: (
    id: string,
    payload: {
      description: string;
      quantity: number;
      unitPrice: number;
      discountAmount?: number;
      vatRate?: number;
      serviceTypeId?: string;
    }
  ) => apiClient.post<void>(`/api/v1/invoices/${id}/lines`, payload),
  issueInvoice: (id: string) => apiClient.post<void>(`/api/v1/invoices/${id}/issue`),
  voidInvoice: (id: string) => apiClient.post<void>(`/api/v1/invoices/${id}/void`),
  recordPayment: (id: string, payload: { method: PaymentMethod; amount: number; pspRef?: string }) =>
    apiClient.post<void>(`/api/v1/invoices/${id}/payments`, payload),
  revenueSummary: () => apiClient.get<RevenueSummary>('/api/v1/invoices/revenue-summary'),
  listOwnerBalances: () => apiClient.get<OwnerBalance[]>('/api/v1/owner-balances'),
  getCurrentCashRegister: () => apiClient.get<CashRegisterSession | null>('/api/v1/cash-register/current'),
  openCashRegister: (payload: { openingBalance: number; notes?: string }) =>
    apiClient.postForId('/api/v1/cash-register/open', payload),
  closeCashRegister: (id: string, payload: { closingBalance: number; notes?: string }) =>
    apiClient.post<void>(`/api/v1/cash-register/${id}/close`, payload),
  cashRegisterHistory: () => apiClient.get<CashRegisterSession[]>('/api/v1/cash-register/history'),
  quickSale: (payload: { ownerId?: string; lines: QuickSaleLine[]; paymentMethod: PaymentMethod }) =>
    apiClient.postForId('/api/v1/invoices/quick-sale', payload),
  todaySalesSummary: () => apiClient.get<TodaySalesSummary>('/api/v1/invoices/today-summary'),
};
