import { Badge, BadgeTone } from '../../components/ui/Badge';
import { InvoiceStatus } from '../../api/billingApi';

const CONFIG: Record<InvoiceStatus, { label: string; tone: BadgeTone }> = {
  DRAFT: { label: 'Taslak', tone: 'neutral' },
  ISSUED: { label: 'Kesildi', tone: 'warning' },
  PARTIALLY_PAID: { label: 'Kısmi Ödendi', tone: 'warning' },
  PAID: { label: 'Ödendi', tone: 'success' },
  VOID: { label: 'İptal', tone: 'danger' },
};

export function InvoiceStatusBadge({ status }: { status: InvoiceStatus }) {
  const { label, tone } = CONFIG[status];
  return <Badge tone={tone}>{label}</Badge>;
}
