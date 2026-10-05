import type { KeyValueStore } from '../../background/chromeStorage';
import { KEEP_ALIVE_ORIGINS, isKeepAliveEnabled, keepAliveUrl } from './keepAlive';

// TARBIL oturumunu canli tutma kaydi (2026-10-05): her istegin sonucu saklanir ki oturum kapandiginda nedeni gorulsun
// -- istekler kesildi mi (sekme uyutuldu), yoksa istekler surerken TARBIL oturumu kendisi mi kapatti. Sekme ici
// (tab) ve arka plan (background) istekleri ayri tutulur: arka plan isteginin cerez tasiyip tasimadigi da gorulur.
// Kisisel veri yok: yalniz zaman ve sonuc.

export type PingResult = 'ok' | 'loggedOut' | 'error';
export type PingSource = 'tab' | 'background';

export interface SourceStatus {
  lastResult: PingResult;
  lastCheckedAt: number;
  lastOkAt?: number;
  loggedOutSince?: number;
}

export type OriginStatus = Partial<Record<PingSource, SourceStatus>>;
export type KeepAliveStatus = Record<string, OriginStatus>;

export interface OriginSummary {
  state: 'open' | 'closed' | 'stale' | 'never';
  lastOkAt?: number;
  closedAt?: number;
  lastCheckedAt?: number;
}

const STATUS_KEY = 'tarbilKeepAliveStatus';
/** Istekler 4 dk'da bir; bundan uzun sure kayit yoksa istekler durmus demektir. */
const STALE_AFTER_MS = 15 * 60_000;

export const KEEP_ALIVE_ALARM = 'tarbil-keep-alive';

/** redirect:'manual' ile: TARBIL oturumu kapaliysa zaman asimi sayfasina yonlendirir. */
export function classifyPing(res: { type: string; status: number } | Error): PingResult {
  if (res instanceof Error) return 'error';
  if (res.type === 'opaqueredirect' || (res.status >= 300 && res.status < 400)) return 'loggedOut';
  return res.status === 200 ? 'ok' : 'error';
}

export async function readKeepAliveStatus(store: KeyValueStore): Promise<KeepAliveStatus> {
  return (await store.get<KeepAliveStatus>(STATUS_KEY)) ?? {};
}

export async function recordPing(store: KeyValueStore, origin: string, source: PingSource, result: PingResult, now: number): Promise<void> {
  const all = await readKeepAliveStatus(store);
  const prev = all[origin]?.[source];
  const next: SourceStatus = {
    lastResult: result,
    lastCheckedAt: now,
    lastOkAt: result === 'ok' ? now : prev?.lastOkAt,
    loggedOutSince: result === 'loggedOut' ? (prev?.lastResult === 'loggedOut' ? prev.loggedOutSince : now) : result === 'ok' ? undefined : prev?.loggedOutSince,
  };
  await store.set(STATUS_KEY, { ...all, [origin]: { ...all[origin], [source]: next } });
}

export function summarizeOrigin(status: OriginStatus | undefined, now: number): OriginSummary {
  const sources = Object.values(status ?? {}).filter((s): s is SourceStatus => !!s);
  if (sources.length === 0) return { state: 'never' };
  const lastCheckedAt = Math.max(...sources.map((s) => s.lastCheckedAt));
  const okTimes = sources.map((s) => s.lastOkAt).filter((t): t is number => t !== undefined);
  const lastOkAt = okTimes.length ? Math.max(...okTimes) : undefined;
  if (now - lastCheckedAt > STALE_AFTER_MS) return { state: 'stale', lastOkAt, lastCheckedAt };
  if (sources.some((s) => s.lastResult === 'ok' && s.lastOkAt !== undefined && now - s.lastOkAt <= STALE_AFTER_MS)) {
    return { state: 'open', lastOkAt, lastCheckedAt };
  }
  if (lastOkAt === undefined) return { state: 'never', lastCheckedAt };
  const closedTimes = sources.filter((s) => s.lastResult === 'loggedOut' && s.loggedOutSince !== undefined).map((s) => s.loggedOutSince!);
  return { state: 'closed', lastOkAt, lastCheckedAt, closedAt: closedTimes.length ? Math.min(...closedTimes) : undefined };
}

export interface PingAllDeps {
  store: KeyValueStore;
  now: () => number;
  fetch: (url: string) => Promise<{ type: string; status: number }>;
  setBadge: (text: string) => void;
}

/** Arka plan alarmi: iki TARBIL sitesine de istek, sonuclari kaydet, kapanan oturumu simgede "!" ile goster. */
export async function pingAll(d: PingAllDeps): Promise<void> {
  if (!(await isKeepAliveEnabled(d.store))) return;
  for (const origin of KEEP_ALIVE_ORIGINS) {
    const url = keepAliveUrl(origin)!;
    let result: PingResult;
    try {
      result = classifyPing(await d.fetch(url));
    } catch (e) {
      result = classifyPing(e instanceof Error ? e : new Error(String(e)));
    }
    await recordPing(d.store, origin, 'background', result, d.now());
  }
  await refreshBadge(d.store, d.now(), d.setBadge);
}

export async function refreshBadge(store: KeyValueStore, now: number, setBadge: (text: string) => void): Promise<void> {
  const status = await readKeepAliveStatus(store);
  const anyClosed = KEEP_ALIVE_ORIGINS.some((o) => summarizeOrigin(status[o], now).state === 'closed');
  setBadge(anyClosed ? '!' : '');
}

/** Yan panel metni; fmt zamani gosterir (ornegin "14:32"). */
export function statusText(s: OriginSummary, fmt: (ms: number) => string): string {
  switch (s.state) {
    case 'open':
      return `Açık (son kontrol ${fmt(s.lastCheckedAt ?? s.lastOkAt ?? 0)})`;
    case 'closed':
      return `Kapandı ${s.closedAt !== undefined ? fmt(s.closedAt) : ''} (son başarılı ${fmt(s.lastOkAt ?? 0)}) — e-Devlet ile yeniden giriş yapın`;
    case 'stale':
      return `Kontrol durdu (son kontrol ${fmt(s.lastCheckedAt ?? 0)}) — Chrome eklentiyi uyutmuş olabilir`;
    default:
      return 'Giriş yapılmamış';
  }
}

