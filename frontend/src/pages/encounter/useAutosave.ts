import { useCallback, useEffect, useRef, useState } from 'react';

export type AutosaveStatus = 'idle' | 'pending' | 'saving' | 'saved' | 'error';

interface AutosaveOptions {
  /** Yazmaya ara verildikten sonra kaydetmeden önce beklenen süre (ms). */
  delayMs: number;
  /** false iken (salt okunur muayene) hiçbir şey kaydedilmez. */
  enabled: boolean;
  /** Hata sonrası yeniden deneme aralığı (ms). */
  retryMs?: number;
}

export interface Autosave {
  status: AutosaveStatus;
  lastSavedAt: Date | null;
  /** Kaydedilmemiş değişiklik var mı (bekleyen, kaydediliyor ya da hata). */
  dirty: boolean;
  /** Bekleyen değişikliği hemen gönderir; tamamlanınca döner, kaydedilemezse hata fırlatır. */
  flush: () => Promise<void>;
  /** Mevcut değeri "kayıtlı" kabul eder (sayfa ilk yüklendiğinde). */
  markSaved: (value: unknown) => void;
}

/**
 * Muayene ekranı otomatik kaydı: değer değişince `delayMs` bekler, sonra `save` çağrılır.
 * Kaydedilen değer anlık görüntü olarak tutulur; aynı değer tekrar gönderilmez.
 * Kaydetme başarısız olursa değer kaybolmaz, `retryMs` sonra yeniden denenir.
 */
export function useAutosave<T>(value: T, save: (value: T) => Promise<void>, options: AutosaveOptions): Autosave {
  const { delayMs, enabled, retryMs = 5000 } = options;
  const [status, setStatus] = useState<AutosaveStatus>('idle');
  const [lastSavedAt, setLastSavedAt] = useState<Date | null>(null);
  const savedSnapshot = useRef<string | null>(null);
  const latest = useRef(value);
  const saveRef = useRef(save);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const inFlight = useRef<Promise<void> | null>(null);

  latest.current = value;
  saveRef.current = save;

  const isDirty = useCallback(
    () => savedSnapshot.current !== null && JSON.stringify(latest.current) !== savedSnapshot.current,
    [],
  );

  const runSave = useCallback(async (): Promise<void> => {
    if (timer.current) {
      clearTimeout(timer.current);
      timer.current = null;
    }
    if (inFlight.current) await inFlight.current.catch(() => undefined);
    if (!isDirty()) return;
    const snapshot = JSON.stringify(latest.current);
    const toSave = latest.current;
    setStatus('saving');
    const attempt = saveRef
      .current(toSave)
      .then(() => {
        savedSnapshot.current = snapshot;
        setLastSavedAt(new Date());
        setStatus(isDirty() ? 'pending' : 'saved');
      })
      .catch((err) => {
        setStatus('error');
        throw err;
      });
    inFlight.current = attempt;
    try {
      await attempt;
    } finally {
      if (inFlight.current === attempt) inFlight.current = null;
    }
  }, [isDirty]);

  useEffect(() => {
    if (!enabled || savedSnapshot.current === null) return;
    if (!isDirty()) {
      // Yazılan geri silindi: kayıtlı değere döndü, gönderilecek bir şey yok.
      setStatus((s) => (s === 'pending' ? 'saved' : s));
      return;
    }
    setStatus((s) => (s === 'error' ? s : 'pending'));
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      runSave().catch(() => undefined);
    }, delayMs);
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, [value, enabled, delayMs, isDirty, runSave]);

  // Hata sonrası, kullanıcı yazmasa da yeniden dene.
  useEffect(() => {
    if (status !== 'error' || !enabled) return;
    const t = setTimeout(() => {
      runSave().catch(() => undefined);
    }, retryMs);
    return () => clearTimeout(t);
  }, [status, enabled, retryMs, runSave]);

  // Sayfadan uygulama içinde ayrılırken bekleyen değişikliği gönder.
  useEffect(() => {
    return () => {
      if (enabled && isDirty()) {
        saveRef.current(latest.current).catch(() => undefined);
      }
    };
  }, [enabled, isDirty]);

  const flush = useCallback(async () => {
    if (!enabled) return;
    await runSave();
  }, [enabled, runSave]);

  const markSaved = useCallback((v: unknown) => {
    savedSnapshot.current = JSON.stringify(v);
    setStatus('idle');
  }, []);

  return { status, lastSavedAt, dirty: status === 'pending' || status === 'saving' || status === 'error', flush, markSaved };
}
