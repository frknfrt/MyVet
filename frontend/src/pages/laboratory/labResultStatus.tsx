import { Badge } from '../../components/ui/Badge';
import { LabResultStatus } from '../../api/labApi';

const LABELS: Record<LabResultStatus, string> = {
  PENDING: 'Bekliyor',
  COMPLETED: 'Tamamlandı',
  CANCELLED: 'İptal Edildi',
};

export function LabResultStatusBadge({ status }: { status: LabResultStatus }) {
  if (status === 'COMPLETED') return <Badge tone="success">{LABELS[status]}</Badge>;
  if (status === 'CANCELLED') return <Badge tone="danger">{LABELS[status]}</Badge>;
  return <Badge tone="warning">{LABELS[status]}</Badge>;
}
