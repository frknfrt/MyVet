import { compareVersions } from './version';

// extension/ icinde `npm run package` uretir (Faz 7); dosyalar repoda tutulmaz.
export const EXTENSION_DOWNLOAD_URL = '/downloads/vetly-tarbil-eklenti.zip';
export const EXTENSION_RELEASE_URL = '/downloads/vetly-tarbil-eklenti.json';

/** Sunucudaki en yeni eklenti surumu; dosya yoksa/HTML donerse (SPA yonlendirmesi) null. */
export async function fetchLatestExtensionVersion(fetchFn: typeof fetch = fetch): Promise<string | null> {
  try {
    const res = await fetchFn(EXTENSION_RELEASE_URL, { cache: 'no-store' });
    if (!res.ok) return null;
    const body = (await res.json()) as { version?: unknown };
    return typeof body.version === 'string' ? body.version : null;
  } catch {
    return null;
  }
}

export type ExtensionVersionStatus =
  | { kind: 'not-installed' }
  | { kind: 'installed'; installed: string }
  | { kind: 'up-to-date'; installed: string }
  | { kind: 'outdated'; installed: string; latest: string };

export function extensionVersionStatus(installed: string | null, latest: string | null): ExtensionVersionStatus {
  if (!installed) return { kind: 'not-installed' };
  if (!latest) return { kind: 'installed', installed };
  return compareVersions(installed, latest) < 0
    ? { kind: 'outdated', installed, latest }
    : { kind: 'up-to-date', installed };
}
