import { useState } from 'react';
import type { BillingPeriod, CatalogPlan } from '../../api/subscriptionApi';
import { Button } from '../ui/Button';
import { Input } from '../ui/Field';
import styles from './PlanPicker.module.css';

interface PlanPickerProps {
  plans: CatalogPlan[];
  onSelect: (planCode: string, billingPeriod: BillingPeriod, couponCode?: string) => void;
  /** Ödemesi başlatılan paketin kodu; doluyken tüm butonlar kilitlenir. */
  submittingPlanCode: string | null;
  error: string | null;
}

function formatPrice(value: number): string {
  return value.toLocaleString('tr-TR', { maximumFractionDigits: 2 });
}

/**
 * Denemeden ücretli üyeliğe geçişte paket seçimi — hem süresi dolan denemenin kilit
 * sayfasında hem Ayarlar > Abonelik'te kullanılır. Tasarım kuralı: tek primary buton
 * (rozetli paket, yoksa ilk paket); diğerleri secondary.
 */
export function PlanPicker({ plans, onSelect, submittingPlanCode, error }: PlanPickerProps) {
  const [period, setPeriod] = useState<BillingPeriod>('MONTHLY');
  const [showCoupon, setShowCoupon] = useState(false);
  const [couponCode, setCouponCode] = useState('');

  if (plans.length === 0) {
    return <div className={styles.empty}>Şu anda satın alınabilir paket yok. Lütfen bizimle iletişime geçin.</div>;
  }

  const featuredCode = (plans.find((p) => p.badge) ?? plans[0]).code;
  const submitting = submittingPlanCode !== null;

  return (
    <div className={styles.picker}>
      <div className={styles.periodToggle} role="group" aria-label="Ödeme dönemi">
        <button
          type="button"
          className={period === 'MONTHLY' ? styles.periodActive : styles.periodOption}
          aria-pressed={period === 'MONTHLY'}
          onClick={() => setPeriod('MONTHLY')}
        >
          Aylık
        </button>
        <button
          type="button"
          className={period === 'ANNUAL' ? styles.periodActive : styles.periodOption}
          aria-pressed={period === 'ANNUAL'}
          onClick={() => setPeriod('ANNUAL')}
        >
          Yıllık
        </button>
      </div>

      {error && (
        <div className={styles.error} role="alert">
          {error}
        </div>
      )}

      <div className={styles.grid}>
        {plans.map((plan) => {
          const annualUnavailable = period === 'ANNUAL' && plan.annualPrice === null;
          const price = period === 'ANNUAL' && plan.annualPrice !== null ? plan.annualPrice : plan.monthlyPrice;
          const variant = plan.code === featuredCode ? 'primary' : 'secondary';
          return (
            <div key={plan.id} className={styles.card} data-plan-code={plan.code}>
              <div className={styles.cardHead}>
                <span className={styles.planName}>{plan.name}</span>
                {plan.badge && <span className={styles.planBadge}>{plan.badge}</span>}
              </div>
              <div className={styles.price}>
                {annualUnavailable ? (
                  <span className={styles.unavailable}>Bu pakette yıllık ödeme yok</span>
                ) : (
                  <>
                    <span className={styles.amount}>{formatPrice(price)} ₺</span>
                    <span className={styles.per}>{period === 'ANNUAL' ? '/ yıl' : '/ ay'}</span>
                  </>
                )}
              </div>
              {plan.description && <p className={styles.description}>{plan.description}</p>}
              {plan.features.length > 0 && (
                <ul className={styles.features}>
                  {plan.features.map((feature) => (
                    <li key={feature}>{feature}</li>
                  ))}
                </ul>
              )}
              <Button
                type="button"
                variant={variant}
                data-variant={variant}
                className={styles.selectButton}
                disabled={submitting || annualUnavailable}
                onClick={() => onSelect(plan.code, period, couponCode.trim() || undefined)}
              >
                {submittingPlanCode === plan.code ? 'Yönlendiriliyor...' : 'Bu Paketi Seç'}
              </Button>
            </div>
          );
        })}
      </div>

      {showCoupon ? (
        <div className={styles.couponRow}>
          <Input
            name="couponCode"
            placeholder="Kupon kodu"
            value={couponCode}
            onChange={(e) => setCouponCode(e.target.value)}
            aria-label="Kupon kodu"
          />
        </div>
      ) : (
        <Button type="button" variant="tertiary" onClick={() => setShowCoupon(true)}>
          Kupon kodum var
        </Button>
      )}
    </div>
  );
}
