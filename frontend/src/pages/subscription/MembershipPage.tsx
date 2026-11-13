import { useEffect, useState } from 'react';
import { Navigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { BillingPeriod, CatalogPlan, subscriptionApi } from '../../api/subscriptionApi';
import { useAuth } from '../../auth/AuthContext';
import { useSubscriptionAccess } from '../../auth/SubscriptionAccessContext';
import { AuthLayout } from '../../components/layout/AuthLayout';
import { PlanPicker } from '../../components/subscription/PlanPicker';
import { Button } from '../../components/ui/Button';
import { redirectTo } from './redirectTo';
import styles from './MembershipPage.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : 'Ödeme başlatılamadı, lütfen tekrar deneyin.';
}

/**
 * Deneme süresi dolan kliniğin gördüğü tek sayfa: yönetici paket seçip öder, diğer
 * personel yöneticiye yönlendirilir. Panelin geri kalanı RequireAuth tarafından buraya
 * yönlendirilir; sunucu da diğer tüm istekleri 402 ile reddeder.
 */
export function MembershipPage() {
  const { session, logout } = useAuth();
  const { access } = useSubscriptionAccess();
  const [searchParams] = useSearchParams();
  const isAdmin = session?.role === 'ADMIN';

  const [plans, setPlans] = useState<CatalogPlan[] | null>(null);
  const [plansError, setPlansError] = useState<string | null>(null);
  const [submittingPlanCode, setSubmittingPlanCode] = useState<string | null>(null);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);

  useEffect(() => {
    if (!isAdmin) return;
    let cancelled = false;
    subscriptionApi
      .getPlans()
      .then((list) => {
        if (!cancelled) setPlans(list);
      })
      .catch(() => {
        if (!cancelled) setPlansError('Paketler yüklenemedi. Sayfayı yenileyip tekrar deneyin.');
      });
    return () => {
      cancelled = true;
    };
  }, [isAdmin]);

  if (!session) {
    return <Navigate to="/login" replace />;
  }
  if (access && access.state !== 'TRIAL_EXPIRED') {
    return <Navigate to="/panel" replace />;
  }

  async function handleSelect(planCode: string, billingPeriod: BillingPeriod, couponCode?: string) {
    setSubmittingPlanCode(planCode);
    setCheckoutError(null);
    try {
      const checkout = await subscriptionApi.initiateUpgradeCheckout({ planCode, billingPeriod, couponCode });
      redirectTo(checkout.checkoutFormUrl);
    } catch (err) {
      setCheckoutError(errorMessageOf(err));
      setSubmittingPlanCode(null);
    }
  }

  return (
    <AuthLayout
      headline={<>Kaldığınız yerden devam edin</>}
      subcopy="Deneme sırasında girdiğiniz tüm veriler yerinde duruyor. Üyeliğinizi başlattığınız anda paneliniz açılır."
    >
      <div className={styles.content}>
        <h1 className={styles.title}>Deneme kullanım süreniz dolmuştur, lütfen üyelik başlatınız.</h1>

        {searchParams.get('odeme') === 'hata' && (
          <div className={styles.error} role="alert">
            Ödeme tamamlanamadı, lütfen tekrar deneyin.
          </div>
        )}

        {isAdmin ? (
          <>
            <p className={styles.lede}>Size uygun paketi seçin; ödeme onaylanınca üyeliğiniz hemen başlar.</p>
            {plansError && (
              <div className={styles.error} role="alert">
                {plansError}
              </div>
            )}
            {plans === null && !plansError ? (
              <p className={styles.lede}>Paketler yükleniyor...</p>
            ) : (
              plans !== null && (
                <PlanPicker
                  plans={plans}
                  onSelect={handleSelect}
                  submittingPlanCode={submittingPlanCode}
                  error={checkoutError}
                />
              )
            )}
          </>
        ) : (
          <p className={styles.lede}>Deneme süresi doldu, klinik yöneticinizle iletişime geçin.</p>
        )}

        <div className={styles.footer}>
          <span className={styles.signedInAs}>{session.fullName}</span>
          <Button type="button" variant="tertiary" onClick={logout}>
            Çıkış Yap
          </Button>
        </div>
      </div>
    </AuthLayout>
  );
}
