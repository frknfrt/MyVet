import { Badge } from '../../components/ui/Badge';
import { VaccinationStatus } from '../../api/vaccinationApi';

const LABELS: Record<VaccinationStatus, string> = {
  SCHEDULED: 'Planlandı',
  ADMINISTERED: 'Yapıldı',
  CANCELLED: 'İptal Edildi',
};

// REVIZE (2026-11-01, canli QA bulgusu): ozet karttaki "Gecikmis" sayaci ile
// liste satirlarindaki durum etiketi tutarsizdi -- 5 asi "Gecikmis" olarak
// sayiliyordu ama satirlarda hala notr "Planlandi" etiketi gorunuyordu, hekim
// listeye bakarak hangilerinin gecikmis oldugunu ayirt edemiyordu. `overdue`
// true geldiginde SCHEDULED durumu artik "Gecikmis" etiketiyle ve danger
// tonuyla gosterilir.
export function VaccinationStatusBadge({ status, overdue = false }: { status: VaccinationStatus; overdue?: boolean }) {
  if (status === 'ADMINISTERED') return <Badge tone="success">{LABELS[status]}</Badge>;
  if (status === 'CANCELLED') return <Badge tone="danger">{LABELS[status]}</Badge>;
  if (overdue) return <Badge tone="danger">Gecikmiş</Badge>;
  return <Badge tone="warning">{LABELS[status]}</Badge>;
}

/** SCHEDULED bir asi, uygulama tarihi bugunden ONCE ise gecikmis sayilir -- KPI kartiyla AYNI kural. */
export function isVaccinationOverdue(status: VaccinationStatus, administeredDate: string): boolean {
  if (status !== 'SCHEDULED') return false;
  const date = new Date(administeredDate);
  const startOfToday = new Date();
  startOfToday.setHours(0, 0, 0, 0);
  return date.getTime() < startOfToday.getTime();
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
