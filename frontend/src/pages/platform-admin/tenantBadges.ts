import { BadgeTone } from '../../components/ui/Badge';
import { BillingStatus, PlatformInvoiceStatus, TenantStatus } from '../../api/platformAdminApi';

export const TENANT_STATUS_LABELS: Record<TenantStatus, string> = {
  ACTIVE: 'Aktif',
  SUSPENDED: 'Askıya Alındı',
  TRIAL: 'Deneme',
};

export const TENANT_STATUS_TONES: Record<TenantStatus, BadgeTone> = {
  ACTIVE: 'success',
  SUSPENDED: 'danger',
  TRIAL: 'neutral',
};

export const BILLING_STATUS_LABELS: Record<BillingStatus, string> = {
  TRIAL: 'Deneme Sürümü',
  ACTIVE: 'Aktif',
  PAST_DUE: 'Ödeme Gecikti',
  CANCELED: 'İptal Edildi',
};

export const BILLING_STATUS_TONES: Record<BillingStatus, BadgeTone> = {
  TRIAL: 'neutral',
  ACTIVE: 'success',
  PAST_DUE: 'warning',
  CANCELED: 'danger',
};

export const PLATFORM_INVOICE_STATUS_LABELS: Record<PlatformInvoiceStatus, string> = {
  ISSUED: 'Kesildi',
  PAID: 'Ödendi',
  OVERDUE: 'Gecikti',
  VOID: 'İptal',
};

export const PLATFORM_INVOICE_STATUS_TONES: Record<PlatformInvoiceStatus, BadgeTone> = {
  ISSUED: 'neutral',
  PAID: 'success',
  OVERDUE: 'danger',
  VOID: 'neutral',
};

export function formatDate(value: string | null): string {
  if (!value) return '—';
  return new Date(value).toLocaleDateString('tr-TR');
}
