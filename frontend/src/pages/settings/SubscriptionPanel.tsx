import { useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import {
  BillingStatus, CatalogPlan, PlatformInvoice, PlatformInvoiceStatus, SubscriptionOverview, subscriptionApi,
} from '../../api/subscriptionApi';
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

const INVOICE_STATUS_LABELS: Record<PlatformInvoiceStatus, string> = {
  ISSUED: 'Kesildi',
  PAID: 'Ödendi',
  OVERDUE: 'Gecikti',
  VOID: 'İptal',
};

const INVOICE_STATUS_TONES: Record<PlatformInvoiceStatus, BadgeTone> = {
  ISSUED: 'neutral',
  PAID: 'success',
  OVERDUE: 'danger',
  VOID: 'neutral',
};

function formatDate(value: string | null): string {
  if (!value) return '—';
  return new Date(value).toLocaleDateString('tr-TR');
}

function isPayable(status: PlatformInvoiceStatus): boolean {
  return status === 'ISSUED' || status === 'OVERDUE';
}

export function SubscriptionPanel() {
  const [subscription, setSubscription] = useState<SubscriptionOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [invoices, setInvoices] = useState<PlatformInvoice[]>([]);
  const [paymentInstructions, setPaymentInstructions] = useState('');
  const [invoicesLoading, setInvoicesLoading] = useState(true);
  const [invoicesError, setInvoicesError] = useState<string | null>(null);

  const [plans, setPlans] = useState<CatalogPlan[]>([]);

  const [checkoutLoadingId, setCheckoutLoadingId] = useState<string | null>(null);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const [paymentResult, setPaymentResult] = useState<'basarili' | 'hata' | null>(null);

  function loadInvoices() {
    setInvoicesLoading(true);
    subscriptionApi
      .getInvoices()
      .then((overview) => {
        setInvoices(overview.invoices);
        setPaymentInstructions(overview.paymentInstructions);
      })
      .catch((err) => setInvoicesError(errorMessageOf(err)))
      .finally(() => setInvoicesLoading(false));
  }

  useEffect(() => {
    subscriptionApi
      .getCurrent()
      .then(setSubscription)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));

    loadInvoices();

    subscriptionApi.getPlans().then(setPlans).catch(() => setPlans([]));

    const params = new URLSearchParams(window.location.search);
    const odeme = params.get('odeme');
    if (odeme === 'basarili' || odeme === 'hata') {
      setPaymentResult(odeme);
      params.delete('odeme');
      const newSearch = params.toString();
      window.history.replaceState({}, '', window.location.pathname + (newSearch ? `?${newSearch}` : ''));
    }
  }, []);

  async function handlePay(invoiceId: string) {
    setCheckoutLoadingId(invoiceId);
    setCheckoutError(null);
    try {
      const session = await subscriptionApi.initiateCheckout(invoiceId);
      window.location.href = session.checkoutFormUrl;
    } catch (err) {
      setCheckoutError(errorMessageOf(err));
      setCheckoutLoadingId(null);
    }
  }

  if (loading) {
    return <div className={settingsStyles.empty}>Yükleniyor...</div>;
  }

  if (error || !subscription) {
    return <div className={settingsStyles.errorBanner}>{error ?? 'Abonelik bilgisi bulunamadı'}</div>;
  }

  const hasUnpaidInvoice = invoices.some((inv) => isPayable(inv.status));
  const currentPlan = plans.find((p) => p.code === subscription.planCode) ?? null;

  return (
    <>
      {paymentResult === 'basarili' && (
        <div className={styles.paymentSuccessBanner}>Ödemeniz alındı, teşekkürler.</div>
      )}
      {paymentResult === 'hata' && (
        <div className={settingsStyles.errorBanner}>Ödeme tamamlanamadı, lütfen tekrar deneyin.</div>
      )}

      <Card className={styles.card}>
        {currentPlan?.imageUrl && (
          <img src={currentPlan.imageUrl} alt="" className={styles.planImage} />
        )}
        <div className={styles.planCode}>{currentPlan?.name ?? subscription.planCode}</div>
        {currentPlan?.badge && <span className={styles.planBadge}>{currentPlan.badge}</span>}
        <Badge tone={STATUS_TONES[subscription.billingStatus]}>{STATUS_LABELS[subscription.billingStatus]}</Badge>

        {currentPlan?.description && <p className={styles.planDescription}>{currentPlan.description}</p>}

        {currentPlan && currentPlan.features.length > 0 && (
          <ul className={styles.planFeatureList}>
            {currentPlan.features.map((feature) => (
              <li key={feature}>{feature}</li>
            ))}
          </ul>
        )}

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

      <Card className={styles.invoicesCard}>
        <div className={styles.invoicesTitle}>Faturalar</div>

        {invoicesLoading ? (
          <div className={settingsStyles.empty}>Yükleniyor...</div>
        ) : invoicesError ? (
          <div className={settingsStyles.errorBanner}>{invoicesError}</div>
        ) : (
          <>
            {hasUnpaidInvoice && paymentInstructions && (
              <div className={styles.paymentInstructions}>{paymentInstructions}</div>
            )}
            {checkoutError && <div className={settingsStyles.errorBanner}>{checkoutError}</div>}

            {invoices.length === 0 ? (
              <div className={settingsStyles.empty}>Henüz fatura kesilmedi</div>
            ) : (
              <>
                <div className={`${styles.invoiceRow} ${styles.invoiceRowHead}`}>
                  <div>Dönem</div>
                  <div>Tutar</div>
                  <div>Son Ödeme</div>
                  <div>Durum</div>
                  <div></div>
                </div>
                {invoices.map((inv) => (
                  <div key={inv.id} className={styles.invoiceRow}>
                    <div>
                      {formatDate(inv.periodStart)} — {formatDate(inv.periodEnd)}
                    </div>
                    <div className={styles.muted}>{inv.amount.toFixed(2)} ₺</div>
                    <div className={styles.muted}>{formatDate(inv.dueDate)}</div>
                    <div>
                      <Badge tone={INVOICE_STATUS_TONES[inv.status]}>{INVOICE_STATUS_LABELS[inv.status]}</Badge>
                    </div>
                    <div>
                      {isPayable(inv.status) && (
                        <Button
                          variant="secondary"
                          onClick={() => handlePay(inv.id)}
                          disabled={checkoutLoadingId === inv.id}
                        >
                          {checkoutLoadingId === inv.id ? 'Yönlendiriliyor...' : 'Öde'}
                        </Button>
                      )}
                    </div>
                  </div>
                ))}
              </>
            )}
          </>
        )}
      </Card>
    </>
  );
}
