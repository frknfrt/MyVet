import { VETLY_API_BASE, VETLY_APP_ORIGIN } from '../shared/config';
import type { BackgroundRequest, ExternalRequest } from '../shared/messages';
import { chromeLocalStore, chromeSessionStore, createTokenStore } from './chromeStorage';
import { createConfirmationOutbox } from './confirmationOutbox';
import { createRouter } from './router';
import { openVaccinePage } from './tarbilTab';
import { createVetlyApi } from './vetlyApi';
import { KEEP_ALIVE_ALARM, pingAll } from '../tarbil/core/keepAliveStatus';
import { KEEP_ALIVE_INTERVAL_MS } from '../tarbil/core/keepAlive';

// Icerik betigi (TARBIL karti) activeSubmissionId degisikligini dinler; session varsayilan olarak ona kapali.
// Burada yalniz aktif asi kimligi durur, anahtar chrome.storage.local'da.
chrome.storage.session.setAccessLevel({ accessLevel: 'TRUSTED_AND_UNTRUSTED_CONTEXTS' }).catch(() => undefined);

const tokens = createTokenStore(chromeLocalStore());
const api = createVetlyApi({ baseUrl: VETLY_API_BASE, tokens });
const outbox = createConfirmationOutbox(chromeLocalStore(), api);
const router = createRouter({
  api,
  tokens,
  outbox,
  session: chromeSessionStore(),
  openVaccinePage: () => openVaccinePage(chrome.tabs, (windowId) => chrome.windows.update(windowId, { focused: true })),
});

// Arac cubugu simgesine tiklamak yan paneli acar (kullanici hareketi gerektiren tek yol).
chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: true }).catch(() => undefined);

chrome.runtime.onMessage.addListener((req: BackgroundRequest, _sender, sendResponse) => {
  router.handle(req).then(sendResponse);
  return true;
});

chrome.runtime.onMessageExternal.addListener((req: ExternalRequest, sender, sendResponse) => {
  // Tam esitlik: startsWith "https://uygulama.vetly.com.tr.baska-alan" gibi kokenleri de kabul ederdi.
  if (sender.origin !== VETLY_APP_ORIGIN) {
    sendResponse({ ok: false, error: 'İzin verilmeyen kaynak', code: 'UNKNOWN' });
    return false;
  }
  router.handleExternal(req).then(sendResponse);
  return true;
});

chrome.alarms.create('flush-confirmations', { periodInMinutes: 1 });
// TARBIL oturumunu canli tutma (2026-10-05): sekme uyutulsa da Chrome acik kaldikca istek gider; sonuc kaydedilir,
// kapanan oturum simgede "!" olarak gorunur. Ayar yan panelden kapatilabilir (keepAlive.isKeepAliveEnabled).
chrome.alarms.create(KEEP_ALIVE_ALARM, { periodInMinutes: KEEP_ALIVE_INTERVAL_MS / 60_000 });
chrome.action.setBadgeBackgroundColor({ color: '#c0392b' }).catch(() => undefined);
function keepTarbilAlive(): void {
  pingAll({
    store: chromeLocalStore(),
    now: Date.now,
    fetch: (url) => fetch(url, { credentials: 'include', cache: 'no-store', redirect: 'manual' }),
    setBadge: (text) => {
      chrome.action.setBadgeText({ text }).catch(() => undefined);
    },
  }).catch(() => undefined);
}

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === 'flush-confirmations') outbox.flush().catch(() => undefined);
  if (alarm.name === KEEP_ALIVE_ALARM) keepTarbilAlive();
});
chrome.runtime.onStartup.addListener(() => {
  outbox.flush().catch(() => undefined);
});
