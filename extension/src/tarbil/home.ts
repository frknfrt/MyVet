import type { FlowState } from '../shared/flowStore';

export const ARMED_TTL_MS = 30 * 60_000;
export const REDIRECT_COOLDOWN_MS = 60_000;

/**
 * e-Devlet girisi hekimi TARBIL ana sayfasina dondurur (spec S12.2). Yalniz yeni baslatilmis (armed) bir aktarim
 * varsa asi sayfasina gecilir; bayat akis ya da bir dakika icinde ikinci yonlendirme (dongu) yapilmaz.
 */
export function shouldRedirectHome(state: FlowState | null, now: number): boolean {
  if (!state || state.step !== 'armed') return false;
  if (now - state.updatedAt > ARMED_TTL_MS) return false;
  if (state.redirectedAt !== undefined && now - state.redirectedAt < REDIRECT_COOLDOWN_MS) return false;
  return true;
}
