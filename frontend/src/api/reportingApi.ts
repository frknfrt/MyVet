import { apiClient } from './client';
import { InvoiceStatus } from './billingApi';

export interface RevenueReportLine {
  invoiceId: string;
  ownerName: string;
  branchName: string;
  issuedAt: string;
  status: InvoiceStatus;
  totalAmount: number;
  paidAmount: number;
}

export interface ProductSalesLine {
  description: string;
  totalQuantity: number;
  totalRevenue: number;
}

export interface StaffPerformanceLine {
  staffUserId: string;
  staffName: string;
  invoiceCount: number;
  totalRevenue: number;
  avgInvoiceAmount: number;
}

export interface BranchComparisonLine {
  branchId: string;
  branchName: string;
  invoiceCount: number;
  totalRevenue: number;
  paidRevenue: number;
}

export interface ReportFilters {
  from?: string;
  to?: string;
  status?: InvoiceStatus;
}

function buildQuery(filters: ReportFilters): string {
  const params = new URLSearchParams();
  if (filters.from) params.set('from', filters.from);
  if (filters.to) params.set('to', filters.to);
  if (filters.status) params.set('status', filters.status);
  const qs = params.toString();
  return qs ? `?${qs}` : '';
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export const reportingApi = {
  revenueReport: (filters: ReportFilters) =>
    apiClient.get<RevenueReportLine[]>(`/api/v1/invoices/reports/revenue${buildQuery(filters)}`),
  productSalesReport: (filters: ReportFilters) =>
    apiClient.get<ProductSalesLine[]>(`/api/v1/invoices/reports/product-sales${buildQuery(filters)}`),
  revenueExportUrl: (filters: ReportFilters) => `${API_BASE_URL}/api/v1/invoices/reports/revenue/export${buildQuery(filters)}`,
  productSalesExportUrl: (filters: ReportFilters) =>
    `${API_BASE_URL}/api/v1/invoices/reports/product-sales/export${buildQuery(filters)}`,
  staffPerformanceReport: (filters: ReportFilters) =>
    apiClient.get<StaffPerformanceLine[]>(`/api/v1/invoices/reports/staff-performance${buildQuery(filters)}`),
  staffPerformanceExportUrl: (filters: ReportFilters) =>
    `${API_BASE_URL}/api/v1/invoices/reports/staff-performance/export${buildQuery(filters)}`,
  branchComparisonReport: (filters: ReportFilters) =>
    apiClient.get<BranchComparisonLine[]>(`/api/v1/invoices/reports/branch-comparison${buildQuery(filters)}`),
  branchComparisonExportUrl: (filters: ReportFilters) =>
    `${API_BASE_URL}/api/v1/invoices/reports/branch-comparison/export${buildQuery(filters)}`,
  authHeader: () => apiClient.authHeader(),
};
