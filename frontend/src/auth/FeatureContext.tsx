import { createContext, ReactNode, useContext, useEffect, useState } from 'react';
import { subscriptionApi } from '../api/subscriptionApi';
import { useAuth } from './AuthContext';

export type FeatureCode = 'AI_ASSISTANT' | 'IMAGING' | 'LAB_INTEGRATION' | 'BOARDING' | 'INVENTORY' | 'E_FATURA';

interface FeatureContextValue {
  hasFeature: (code: FeatureCode) => boolean;
}

const FeatureContext = createContext<FeatureContextValue>({ hasFeature: () => true });

/**
 * Kliniğin paketinde açık modüller — yalnız görünürlük içindir; asıl koruma backend'de
 * (403 FEATURE_NOT_IN_PLAN). Liste yüklenirken veya yüklenemezse her şey AÇIK varsayılır:
 * modülü yanlışlıkla gizlemek, tıklayınca anlaşılır bir 403 görmekten daha kötüdür.
 */
export function FeatureProvider({ children }: { children: ReactNode }) {
  const { session } = useAuth();
  const [enabled, setEnabled] = useState<Set<string> | null>(null);
  const token = session?.token ?? null;

  useEffect(() => {
    setEnabled(null);
    if (!token) return;
    let cancelled = false;
    subscriptionApi
      .getFeatures()
      .then((r) => {
        if (!cancelled) setEnabled(new Set(r.enabledFeatures));
      })
      .catch(() => {
        if (!cancelled) setEnabled(null);
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  const hasFeature = (code: FeatureCode) => enabled === null || enabled.has(code);
  return <FeatureContext.Provider value={{ hasFeature }}>{children}</FeatureContext.Provider>;
}

export function useFeatures() {
  return useContext(FeatureContext);
}
