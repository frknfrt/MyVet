import { Badge, BadgeTone } from '../../components/ui/Badge';
import { EInvoiceDocumentType, EInvoiceSubmissionStatus } from '../../api/efaturaApi';

const STATUS_CONFIG: Record<EInvoiceSubmissionStatus, { label: string; tone: BadgeTone }> = {
  PENDING: { label: 'Bekliyor', tone: 'neutral' },
  PROCESSING: { label: 'GİB Resmileştiriyor', tone: 'warning' },
  SUBMITTED: { label: 'Gönderildi', tone: 'success' },
  FAILED: { label: 'Başarısız', tone: 'danger' },
};

const TYPE_LABELS: Record<EInvoiceDocumentType, string> = {
  E_ARSIV: 'e-Arşiv',
  E_FATURA: 'e-Fatura',
};

export function EInvoiceSubmissionStatusBadge({ status }: { status: EInvoiceSubmissionStatus }) {
  const { label, tone } = STATUS_CONFIG[status];
  return <Badge tone={tone}>{label}</Badge>;
}

export function eInvoiceDocumentTypeLabel(type: EInvoiceDocumentType) {
  return TYPE_LABELS[type];
}
