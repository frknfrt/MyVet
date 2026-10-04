// Telerik RadControls 2012.3 + ASP.NET AJAX (spec S3). Bilesenler her komutta YENIDEN aranir:
// async postback PageContentPanel'i yeniden cizer ve eski nesneler gecersiz kalir.

/* eslint-disable @typescript-eslint/no-explicit-any, @typescript-eslint/ban-types */
export type TelerikComponent = Record<string, any>;

export interface Prm {
  add_beginRequest(h: Function): void;
  remove_beginRequest(h: Function): void;
  add_endRequest(h: Function): void;
  remove_endRequest(h: Function): void;
}

export interface TelerikEnv {
  doc: Document;
  find: (id: string) => TelerikComponent | null;
  prm: () => Prm | null;
  isReady: () => boolean;
}

export type PageErrorCode = 'NOT_FOUND' | 'OPTION_NOT_FOUND' | 'AJAX_TIMEOUT' | 'AJAX_ERROR' | 'NOT_READY' | 'BAD_INPUT' | 'NOT_ALLOWED';

export class PageError extends Error {
  constructor(public readonly code: PageErrorCode, message: string) {
    super(message);
  }
}

export function clientId(env: TelerikEnv, suffix: string): string {
  const el = env.doc.querySelector(`[id$="${suffix}"]`);
  if (!el) throw new PageError('NOT_FOUND', `Sayfada bulunamadı: ${suffix}`);
  return el.id;
}

function component(env: TelerikEnv, suffix: string): TelerikComponent {
  const c = env.find(clientId(env, suffix));
  if (!c) throw new PageError('NOT_FOUND', `Bileşen hazır değil: ${suffix}`);
  return c;
}

/**
 * action'i calistirir ve tetikledigi async postback'in bitmesini bekler. startGraceMs icinde postback
 * baslamazsa postback olmadigi kabul edilir. Hata TARBIL'e birakilir (kendi mesajini gosterir), biz yalniz dururuz.
 */
export function withPostback(
  env: TelerikEnv,
  action: () => void,
  { startGraceMs = 1500, timeoutMs = 10_000 }: { startGraceMs?: number; timeoutMs?: number } = {},
): Promise<{ postback: boolean }> {
  return new Promise((resolve, reject) => {
    const prm = env.prm();
    if (!prm) {
      try {
        action();
        resolve({ postback: false });
      } catch (e) {
        reject(e);
      }
      return;
    }
    let started = false;
    const cleanup = () => {
      prm.remove_beginRequest(onBegin);
      prm.remove_endRequest(onEnd);
      clearTimeout(grace);
      clearTimeout(timeout);
    };
    const onBegin = () => {
      started = true;
    };
    const onEnd = (_sender: unknown, args: { get_error?: () => unknown } | undefined) => {
      cleanup();
      const error = args?.get_error?.();
      if (error) reject(new PageError('AJAX_ERROR', `TARBİL isteği hata verdi: ${String((error as Error).message ?? error)}`));
      else resolve({ postback: true });
    };
    prm.add_beginRequest(onBegin);
    prm.add_endRequest(onEnd);
    const grace = setTimeout(() => {
      if (!started) {
        cleanup();
        resolve({ postback: false });
      }
    }, startGraceMs);
    const timeout = setTimeout(() => {
      cleanup();
      reject(new PageError('AJAX_TIMEOUT', 'TARBİL yanıt vermedi'));
    }, timeoutMs);
    try {
      action();
    } catch (e) {
      cleanup();
      reject(e);
    }
  });
}

export async function waitUntil(test: () => boolean, timeoutMs: number, stepMs = 100): Promise<void> {
  const end = Date.now() + timeoutMs;
  while (!test()) {
    if (Date.now() > end) throw new PageError('NOT_READY', 'TARBİL sayfası hazır olmadı');
    await new Promise((r) => setTimeout(r, stepMs));
  }
}

export async function setDate(env: TelerikEnv, suffix: string, iso: string): Promise<{ changed: boolean }> {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) throw new PageError('BAD_INPUT', `Geçersiz tarih: ${iso}`);
  const target = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
  const picker = component(env, suffix);
  const current = picker.get_selectedDate?.() as Date | null | undefined;
  if (
    current &&
    current.getFullYear() === target.getFullYear() &&
    current.getMonth() === target.getMonth() &&
    current.getDate() === target.getDate()
  ) {
    return { changed: false };
  }
  await withPostback(env, () => picker.set_selectedDate(target));
  return { changed: true };
}

/** force: deger istemcide secili gorunse de sunucuya gitmemis olabilir (yarim kalmis postback); yeniden sec. */
export async function selectComboValue(env: TelerikEnv, suffix: string, value: string, force = false): Promise<{ changed: boolean }> {
  const combo = component(env, suffix);
  const alreadySelected = combo.get_value() === value;
  if (alreadySelected && !force) return { changed: false };
  const item = combo.findItemByValue(value);
  if (!item) throw new PageError('OPTION_NOT_FOUND', `Seçenek yok: ${suffix}`);
  await withPostback(env, () => {
    if (alreadySelected) combo.clearSelection?.();
    item.select();
  });
  return { changed: true };
}

export function setText(env: TelerikEnv, suffix: string, value: string): void {
  component(env, suffix).set_value(value);
}

export function clickButton(env: TelerikEnv, suffix: string): Promise<{ postback: boolean }> {
  const id = clientId(env, suffix);
  const c = env.find(id);
  return withPostback(env, () => {
    if (c && typeof c.click === 'function') {
      c.click();
      return;
    }
    const el = env.doc.getElementById(`${id}_input`) ?? env.doc.getElementById(id);
    if (!el) throw new PageError('NOT_FOUND', `Buton bulunamadı: ${suffix}`);
    (el as HTMLElement).click();
  });
}

export function clickElement(env: TelerikEnv, id: string): Promise<{ postback: boolean }> {
  const el = env.doc.getElementById(id);
  if (!el) return Promise.reject(new PageError('NOT_FOUND', `Öğe bulunamadı: ${id}`));
  return withPostback(env, () => (el as HTMLElement).click());
}
