// Izole dunya (content.js) ile sayfanin kendi dunyasi (page.js) arasinda komut koprusu.
// Iki dunya ayni window'u paylasir; mesajlar sekilden (__vetly) taninir. Sayfa (TARBIL) bu
// mesajlari gorebilir: koprude yalniz TARBIL'e zaten girilecek degerler (tarih, tur, cip) tasinir.

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type PageHandler = (args: any) => unknown | Promise<unknown>;

export class BridgeError extends Error {
  constructor(public readonly code: string, message: string) {
    super(message);
  }
}

interface CommandMessage {
  __vetly: 'cmd';
  id: string;
  op: string;
  args?: unknown;
}

interface ResponseMessage {
  __vetly: 'res';
  id: string;
  ok: boolean;
  data?: unknown;
  code?: string;
  error?: string;
}

export interface PageBridge {
  call<T = unknown>(op: string, args?: unknown, timeoutMs?: number): Promise<T>;
}

export function installPageHandler(win: Window, handlers: Record<string, PageHandler>): () => void {
  const listener = async (event: MessageEvent) => {
    const m = event.data as CommandMessage | null;
    if (!m || m.__vetly !== 'cmd' || typeof m.id !== 'string') return;
    const reply = (r: Omit<ResponseMessage, '__vetly' | 'id'>) => win.postMessage({ __vetly: 'res', id: m.id, ...r }, '*');
    const handler = Object.prototype.hasOwnProperty.call(handlers, m.op) ? handlers[m.op] : undefined;
    if (!handler) {
      reply({ ok: false, code: 'UNKNOWN_OP', error: `Bilinmeyen komut: ${m.op}` });
      return;
    }
    try {
      reply({ ok: true, data: await handler(m.args) });
    } catch (e) {
      const err = e as { code?: string; message?: string };
      reply({ ok: false, code: err?.code ?? 'UNKNOWN', error: err?.message ?? String(e) });
    }
  };
  win.addEventListener('message', listener);
  return () => win.removeEventListener('message', listener);
}

export function createPageBridge(win: Window): PageBridge {
  const pending = new Map<string, { resolve: (v: unknown) => void; reject: (e: Error) => void; timer: ReturnType<typeof setTimeout> }>();
  win.addEventListener('message', (event: MessageEvent) => {
    const m = event.data as ResponseMessage | null;
    if (!m || m.__vetly !== 'res') return;
    const p = pending.get(m.id);
    if (!p) return;
    pending.delete(m.id);
    clearTimeout(p.timer);
    if (m.ok) p.resolve(m.data);
    else p.reject(new BridgeError(m.code ?? 'UNKNOWN', m.error ?? 'Sayfa işlemi başarısız'));
  });

  let seq = 0;
  return {
    call<T>(op: string, args?: unknown, timeoutMs = 20_000): Promise<T> {
      const id = `${Date.now()}-${++seq}`;
      return new Promise<T>((resolve, reject) => {
        const timer = setTimeout(() => {
          pending.delete(id);
          reject(new BridgeError('BRIDGE_TIMEOUT', `Sayfa yanıt vermedi: ${op}`));
        }, timeoutMs);
        pending.set(id, { resolve: resolve as (v: unknown) => void, reject, timer });
        win.postMessage({ __vetly: 'cmd', id, op, args } satisfies CommandMessage, '*');
      });
    },
  };
}
