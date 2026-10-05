import type { KeyValueStore } from '../../background/chromeStorage';

// TARBIL oturumunu canli tutma (spec 2026-10-02 S12.3, 2026-10-04 kullanici istegi): acik bir TARBIL sekmesi
// varken birkac dakikada bir AYNI siteden sade bir sayfa istenir. Veri/form gondermez, hicbir butona basmaz;
// sunucu bos kalan oturumu kapatmasin diye. Oturum zaten dustuyse e-Devlet girisi yine hekimdedir.

export const KEEP_ALIVE_INTERVAL_MS = 4 * 60_000;
const SETTING_KEY = 'tarbilKeepAlive';

const PAGES: Record<string, string> = {
  'https://hbsapp.tarbil.gov.tr': '/Default.aspx',
  'https://vetilac.tarbil.gov.tr': '/Pages/PharmacyDefault.aspx',
};

/** Canli tutulan TARBIL siteleri (hbsapp asi, vetilac ilac). */
export const KEEP_ALIVE_ORIGINS: readonly string[] = Object.keys(PAGES);

export function keepAliveUrl(origin: string): string | null {
  const path = Object.prototype.hasOwnProperty.call(PAGES, origin) ? PAGES[origin] : null;
  return path ? `${origin}${path}` : null;
}

export async function isKeepAliveEnabled(store: KeyValueStore): Promise<boolean> {
  return (await store.get<boolean>(SETTING_KEY)) !== false;
}

export function setKeepAliveEnabled(store: KeyValueStore, enabled: boolean): Promise<void> {
  return store.set(SETTING_KEY, enabled);
}

export interface KeepAliveDeps {
  origin: string;
  enabled: () => Promise<boolean>;
  ping: (url: string) => Promise<unknown>;
  setInterval: (fn: () => void, ms: number) => unknown;
  clearInterval: (handle: unknown) => void;
}

/** @return durdurma fonksiyonu */
export function startKeepAlive(d: KeepAliveDeps): () => void {
  const url = keepAliveUrl(d.origin);
  if (!url) return () => undefined;
  const handle = d.setInterval(() => {
    void (async () => {
      try {
        if (await d.enabled()) await d.ping(url);
      } catch {
        // Ag hatasi ya da dusmus oturum: bir sonraki turda yeniden denenir; hekime gosterilecek bir sey yok.
      }
    })();
  }, KEEP_ALIVE_INTERVAL_MS);
  return () => d.clearInterval(handle);
}
