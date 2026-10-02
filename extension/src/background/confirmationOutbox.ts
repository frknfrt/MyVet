import type { ConfirmationMethod } from '../shared/types';
import type { KeyValueStore } from './chromeStorage';
import { ApiError, type VetlyApi } from './vetlyApi';

export interface OutboxEntry {
  id: string;
  method: ConfirmationMethod;
  tarbilReference: string | null;
}

const KEY = 'confirmationOutbox';

/**
 * TARBIL'e kaydedilmis ama Vetly'ye bildirilememis onaylar. Kayip olmamasi icin
 * chrome.storage.local'da tutulur; sunucu ucu idempotent oldugu icin tekrar gonderim guvenli.
 */
export function createConfirmationOutbox(store: KeyValueStore, api: Pick<VetlyApi, 'markSubmitted'>) {
  async function read(): Promise<OutboxEntry[]> {
    return (await store.get<OutboxEntry[]>(KEY)) ?? [];
  }

  return {
    async enqueue(entry: OutboxEntry) {
      const entries = await read();
      if (!entries.some((e) => e.id === entry.id)) {
        await store.set(KEY, [...entries, entry]);
      }
    },
    async size() {
      return (await read()).length;
    },
    async flush(): Promise<number> {
      const entries = await read();
      const remaining: OutboxEntry[] = [];
      let sent = 0;
      for (let i = 0; i < entries.length; i++) {
        const entry = entries[i];
        try {
          await api.markSubmitted(entry.id, entry.method, entry.tarbilReference);
          sent++;
        } catch (e) {
          const code = e instanceof ApiError ? e.code : 'UNKNOWN';
          if (code === 'NOT_FOUND' || code === 'CONFLICT') continue;
          remaining.push(entry);
          if (code === 'UNAUTHORIZED') {
            remaining.push(...entries.slice(i + 1));
            break;
          }
        }
      }
      await store.set(KEY, remaining);
      return sent;
    },
  };
}

export type ConfirmationOutbox = ReturnType<typeof createConfirmationOutbox>;
