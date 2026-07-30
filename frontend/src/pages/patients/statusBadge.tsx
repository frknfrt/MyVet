import { Badge } from '../../components/ui/Badge';
import { PatientStatus } from '../../api/patientApi';

const LABELS: Record<PatientStatus, string> = {
  ACTIVE: 'Aktif',
  DECEASED: 'Vefat Etti',
  TRANSFERRED: 'Nakil Oldu',
};

export function PatientStatusBadge({ status }: { status: PatientStatus }) {
  if (status === 'ACTIVE') return <Badge tone="success">{LABELS[status]}</Badge>;
  if (status === 'DECEASED') return <Badge tone="danger">{LABELS[status]}</Badge>;
  return <Badge tone="neutral">{LABELS[status]}</Badge>;
}
