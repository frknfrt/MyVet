import { Badge, BadgeTone } from '../../components/ui/Badge';
import { AppointmentStatus } from '../../api/appointmentApi';

const CONFIG: Record<AppointmentStatus, { label: string; tone: BadgeTone }> = {
  REQUESTED: { label: 'Talep Edildi', tone: 'neutral' },
  CONFIRMED: { label: 'Onaylandı', tone: 'ai' },
  CHECKED_IN: { label: 'Check-in', tone: 'warning' },
  IN_PROGRESS: { label: 'Muayenede', tone: 'warning' },
  COMPLETED: { label: 'Tamamlandı', tone: 'success' },
  NO_SHOW: { label: 'Gelmedi', tone: 'danger' },
  CANCELLED: { label: 'İptal', tone: 'neutral' },
};

export function AppointmentStatusBadge({ status }: { status: AppointmentStatus }) {
  const { label, tone } = CONFIG[status];
  return <Badge tone={tone}>{label}</Badge>;
}
