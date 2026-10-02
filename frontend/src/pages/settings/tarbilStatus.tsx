import { Badge, BadgeTone } from '../../components/ui/Badge';
import { TarbilConfirmationMethod, TarbilSyncStatus } from '../../api/tarbilApi';

const STATUS_CONFIG: Record<TarbilSyncStatus, { label: string; tone: BadgeTone }> = {
  PENDING: { label: 'Bekliyor', tone: 'warning' },
  SUBMITTED: { label: 'Gönderildi', tone: 'success' },
  DISMISSED: { label: 'Bildirilmeyecek', tone: 'neutral' },
};

const METHOD_LABELS: Record<TarbilConfirmationMethod, string> = {
  AUTO: 'Eklenti doğruladı',
  MANUAL: 'Elle işaretlendi',
};

export function TarbilSyncStatusBadge({ status }: { status: TarbilSyncStatus }) {
  const { label, tone } = STATUS_CONFIG[status];
  return <Badge tone={tone}>{label}</Badge>;
}

export function confirmationMethodLabel(method: TarbilConfirmationMethod | null) {
  return method ? METHOD_LABELS[method] : '';
}
