import { Badge, BadgeTone } from '../../components/ui/Badge';
import { EncounterStatus } from '../../api/encounterApi';

const CONFIG: Record<EncounterStatus, { label: string; tone: BadgeTone }> = {
  DRAFT: { label: 'Taslak', tone: 'neutral' },
  FINALIZED: { label: 'Tamamlandı', tone: 'success' },
  AMENDED: { label: 'Düzeltildi', tone: 'warning' },
};

export function EncounterStatusBadge({ status }: { status: EncounterStatus }) {
  const { label, tone } = CONFIG[status];
  return <Badge tone={tone}>{label}</Badge>;
}
