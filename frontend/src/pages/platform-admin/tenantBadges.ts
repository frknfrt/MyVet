import { BadgeTone } from '../../components/ui/Badge';
import { BillingStatus, TenantStatus } from '../../api/platformAdminApi';

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

export function formatDate(value: string | null): string {
  if (!value) return '—';
  return new Date(value).toLocaleDateString('tr-TR');
}
