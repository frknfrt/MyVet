import { createContext, ReactNode, useCallback, useContext, useEffect, useState } from 'react';
import { setPaymentRequiredHandler } from '../api/client';
import { SubscriptionAccess, subscriptionApi } from '../api/subscriptionApi';
import { useAuth } from './AuthContext';

interface SubscriptionAccessContextValue {
  /** Yüklenirken veya yüklenemezse null — kilit VARSAYILMAZ; asıl koruma sunucuda (402 TRIAL_EXPIRED). */
  access: SubscriptionAccess | null;
  /** Sunucu 402 TRIAL_EXPIRED döndüğünde çağrılır: durum henüz yüklenmemiş olsa da kilit sayfasına geçilir. */
  markExpired: () => void;
  reload: () => void;
}

const SubscriptionAccessContext = createContext<SubscriptionAccessContextValue>({
  access: null,
  markExpired: () => undefined,
  reload: () => undefined,
});

/**
 * Kliniğin abonelik erişim durumu (deneme / deneme doldu / test hesabı / aktif). Deneme
 * bandı ve süresi dolan denemede /uyelik yönlendirmesi bunu kullanır.
 */
export function SubscriptionAccessProvider({ children }: { children: ReactNode }) {
  const { session } = useAuth();
  const [access, setAccess] = useState<SubscriptionAccess | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const userKey = session?.staffUserId ?? null;

  useEffect(() => {
    setAccess(null);
    if (!userKey) return;
    let cancelled = false;
    subscriptionApi
      .getAccess()
      .then((result) => {
        if (!cancelled) setAccess(result);
      })
      .catch(() => {
        if (!cancelled) setAccess(null);
      });
    return () => {
      cancelled = true;
    };
  }, [userKey, reloadKey]);

  const markExpired = useCallback(() => {
    setAccess((current) =>
      current?.state === 'TRIAL_EXPIRED'
        ? current
        : { state: 'TRIAL_EXPIRED', trialEndsAt: current?.trialEndsAt ?? null, daysLeft: 0 }
    );
  }, []);

  const reload = useCallback(() => setReloadKey((key) => key + 1), []);

  useEffect(() => {
    setPaymentRequiredHandler(markExpired);
    return () => setPaymentRequiredHandler(null);
  }, [markExpired]);

  return (
    <SubscriptionAccessContext.Provider value={{ access, markExpired, reload }}>
      {children}
    </SubscriptionAccessContext.Provider>
  );
}

export function useSubscriptionAccess() {
  return useContext(SubscriptionAccessContext);
}
