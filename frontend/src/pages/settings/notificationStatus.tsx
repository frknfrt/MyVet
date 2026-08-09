import { Badge, BadgeTone } from '../../components/ui/Badge';
import { NotificationChannel, NotificationLogStatus, NotificationType } from '../../api/notificationApi';

const STATUS_CONFIG: Record<NotificationLogStatus, { label: string; tone: BadgeTone }> = {
  PENDING: { label: 'Bekliyor', tone: 'neutral' },
  SENT: { label: 'Gönderildi', tone: 'success' },
  FAILED: { label: 'Başarısız', tone: 'danger' },
};

const TYPE_LABELS: Record<NotificationType, string> = {
  APPOINTMENT_CONFIRMATION: 'Randevu Onayı',
  APPOINTMENT_REMINDER: 'Randevu Hatırlatma',
  CAMPAIGN_MESSAGE: 'Kampanya Mesajı',
};

const CHANNEL_LABELS: Record<NotificationChannel, string> = {
  SMS: 'SMS',
  WHATSAPP: 'WhatsApp',
};

export function NotificationStatusBadge({ status }: { status: NotificationLogStatus }) {
  const { label, tone } = STATUS_CONFIG[status];
  return <Badge tone={tone}>{label}</Badge>;
}

export function notificationTypeLabel(type: NotificationType) {
  return TYPE_LABELS[type];
}

export function notificationChannelLabel(channel: NotificationChannel) {
  return CHANNEL_LABELS[channel];
}
