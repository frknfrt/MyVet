import { Badge, BadgeTone } from '../../components/ui/Badge';
import { TarbilSyncStatus, TarbilSyncType } from '../../api/tarbilApi';

const STATUS_CONFIG: Record<TarbilSyncStatus, { label: string; tone: BadgeTone }> = {
  PENDING: { label: 'Bekliyor', tone: 'neutral' },
  SYNCED: { label: 'Senkronize', tone: 'success' },
  FAILED: { label: 'Başarısız', tone: 'danger' },
};

const TYPE_LABELS: Record<TarbilSyncType, string> = {
  VACCINATION: 'Aşı',
  IDENTIFICATION: 'Kimliklendirme',
  TREATMENT: 'Tedavi',
};

export function TarbilSyncStatusBadge({ status }: { status: TarbilSyncStatus }) {
  const { label, tone } = STATUS_CONFIG[status];
  return <Badge tone={tone}>{label}</Badge>;
}

export function tarbilTypeLabel(type: TarbilSyncType) {
  return TYPE_LABELS[type];
}
