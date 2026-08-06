import { Badge } from '../../components/ui/Badge';
import { BoardingStayStatus } from '../../api/boardingApi';

const LABELS: Record<BoardingStayStatus, string> = {
  CHECKED_IN: 'Konaklamada',
  CHECKED_OUT: 'Çıkış Yapıldı',
  CANCELLED: 'İptal Edildi',
};

export function BoardingStayStatusBadge({ status }: { status: BoardingStayStatus }) {
  if (status === 'CHECKED_IN') return <Badge tone="success">{LABELS[status]}</Badge>;
  if (status === 'CANCELLED') return <Badge tone="danger">{LABELS[status]}</Badge>;
  return <Badge tone="neutral">{LABELS[status]}</Badge>;
}

export const COMMON_ROOM_GROUPS = ['Köpek Pansiyonu', 'Kedi Pansiyonu', 'VIP Oda', 'Özel Bakım'];
