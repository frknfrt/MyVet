export interface KeyValueStore {
  get<T>(key: string): Promise<T | undefined>;
  set(key: string, value: unknown): Promise<void>;
  remove(key: string): Promise<void>;
}

function chromeArea(area: chrome.storage.StorageArea): KeyValueStore {
  return {
    async get<T>(key: string) {
      const result = await area.get(key);
      return result[key] as T | undefined;
    },
    async set(key, value) {
      await area.set({ [key]: value });
    },
    async remove(key) {
      await area.remove(key);
    },
  };
}

export const chromeLocalStore = (): KeyValueStore => chromeArea(chrome.storage.local);
export const chromeSessionStore = (): KeyValueStore => chromeArea(chrome.storage.session);

export function memoryStore(): KeyValueStore {
  const data = new Map<string, unknown>();
  return {
    async get<T>(key: string) {
      return data.get(key) as T | undefined;
    },
    async set(key, value) {
      data.set(key, structuredClone(value));
    },
    async remove(key) {
      data.delete(key);
    },
  };
}

export function createTokenStore(store: KeyValueStore) {
  const KEY = 'vetlyExtensionToken';
  return {
    async get(): Promise<string | null> {
      return (await store.get<string>(KEY)) ?? null;
    },
    set: (token: string) => store.set(KEY, token),
    clear: () => store.remove(KEY),
  };
}

export type TokenStore = ReturnType<typeof createTokenStore>;
