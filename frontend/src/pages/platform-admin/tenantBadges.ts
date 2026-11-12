import { BadgeTone } from '../../components/ui/Badge';
import { BillingStatus, PlatformInvoiceStatus, TenantStatus, TenantSuspensionReason } from '../../api/platformAdminApi';

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

export const TENANT_SUSPENSION_REASON_LABELS: Record<TenantSuspensionReason, string> = {
  BILLING_OVERDUE: 'Ödeme Gecikmesi (Otomatik)',
  SELF_CANCELLED: 'Üyelik İptali (Otomatik)',
  PRICE: 'Fiyat',
  COMPETITOR: 'Rakibe Geçti',
  NOT_USING: 'Kullanmıyor',
  DISSATISFIED: 'Memnuniyetsizlik',
  CLOSED_BUSINESS: 'İşyeri Kapandı',
  OTHER: 'Diğer',
};

/** plans tablosunda satırı olmayan, sistemin ismen tanıdığı plan kodları. */
export const INTERNAL_PLAN_CODE = 'INTERNAL';
export const TRIAL_PLAN_CODE = 'TRIAL';

export function isInternalPlan(planCode: string): boolean {
  return planCode === INTERNAL_PLAN_CODE;
}

/** Ayrılmış plan kodlarını okunur etikete çevirir; gerçek paket kodları olduğu gibi kalır. */
export function planCodeLabel(planCode: string): string {
  if (planCode === INTERNAL_PLAN_CODE) return 'Test Hesabı (süresiz)';
  if (planCode === TRIAL_PLAN_CODE) return 'Deneme';
  return planCode;
}
