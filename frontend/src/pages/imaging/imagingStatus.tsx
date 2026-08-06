import { Badge } from '../../components/ui/Badge';
import { ImagingModality, ImagingRecordStatus } from '../../api/imagingApi';

const STATUS_LABELS: Record<ImagingRecordStatus, string> = {
  PENDING: 'Bekliyor',
  COMPLETED: 'Tamamlandı',
  CANCELLED: 'İptal Edildi',
};

export function ImagingStatusBadge({ status }: { status: ImagingRecordStatus }) {
  if (status === 'COMPLETED') return <Badge tone="success">{STATUS_LABELS[status]}</Badge>;
  if (status === 'CANCELLED') return <Badge tone="danger">{STATUS_LABELS[status]}</Badge>;
  return <Badge tone="warning">{STATUS_LABELS[status]}</Badge>;
}

export const MODALITY_LABELS: Record<ImagingModality, string> = {
  XRAY: 'Röntgen',
  ULTRASOUND: 'Ultrason',
  DICOM: 'DICOM',
  OTHER: 'Diğer',
};
