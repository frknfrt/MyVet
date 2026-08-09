import { useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { BillingStatus, SubscriptionOverview, subscriptionApi } from '../../api/subscriptionApi';
import { Badge, BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import settingsStyles from './SettingsPage.module.css';
import styles from './SubscriptionPanel.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

const STATUS_LABELS: Record<BillingStatus, string> = {
  TRIAL: 'Deneme Sürümü',
  ACTIVE: 'Aktif',
  PAST_DUE: 'Ödeme Gecikti',
  CANCELED: 'İptal Edildi',
};

const STATUS_TONES: Record<BillingStatus, BadgeTone> = {
  TRIAL: 'neutral',
  ACTIVE: 'success',
  PAST_DUE: 'warning',
  CANCELED: 'danger',
};

function formatDate(value: string | null): string {
  if (!value) return '—';
  return new Date(value).toLocaleDateString('tr-TR');
}

export function SubscriptionPanel() {
  const [subscription, setSubscription] = useState<SubscriptionOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    subscriptionApi
      .getCurrent()
      .then(setSubscription)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return <div className={settingsStyles.empty}>Yükleniyor...</div>;
  }

  if (error || !subscription) {
    return <div className={settingsStyles.errorBanner}>{error ?? 'Abonelik bilgisi bulunamadı'}</div>;
  }

  return (
    <Card className={styles.card}>
      <div className={styles.planCode}>{subscription.planCode}</div>
      <Badge tone={STATUS_TONES[subscription.billingStatus]}>{STATUS_LABELS[subscription.billingStatus]}</Badge>

      <div className={styles.row}>
        <span className={styles.rowLabel}>Başlangıç Tarihi</span>
        <span>{formatDate(subscription.startedAt)}</span>
      </div>
      <div className={styles.row}>
        <span className={styles.rowLabel}>Yenileme Tarihi</span>
        <span>{formatDate(subscription.renewsAt)}</span>
      </div>

      <div className={styles.ctaRow}>
        <div className={styles.ctaHint}>
          Planınızı değiştirmek veya faturalama bilgilerinizi güncellemek için bizimle iletişime geçin.
        </div>
        <Button variant="secondary" onClick={() => window.open('mailto:destek@myvet.app')}>
          Bizimle İletişime Geçin
        </Button>
      </div>
    </Card>
  );
}
