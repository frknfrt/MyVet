import { Badge } from '../../components/ui/Badge';
import { VaccinationStatus } from '../../api/vaccinationApi';

const LABELS: Record<VaccinationStatus, string> = {
  SCHEDULED: 'Planlandı',
  ADMINISTERED: 'Yapıldı',
  CANCELLED: 'İptal Edildi',
};

export function VaccinationStatusBadge({ status }: { status: VaccinationStatus }) {
  if (status === 'ADMINISTERED') return <Badge tone="success">{LABELS[status]}</Badge>;
  if (status === 'CANCELLED') return <Badge tone="danger">{LABELS[status]}</Badge>;
  return <Badge tone="warning">{LABELS[status]}</Badge>;
}

export const COMMON_VACCINE_NAMES = [
  'Karma (DHPPi)',
  'Kuduz',
  'Bordetella',
  'Lösemi (FeLV)',
  'Panleukopenia',
  'Leptospiroz',
  'Lyme',
  'İç Parazit',
  'Dış Parazit',
];
