// TARBIL icerik betigi (izole dunya): Vetly arka planiyla konusur, karti gosterir, akisi yonetir.
// Telerik islemleri page.js (MAIN dunya) uzerinden kopruyle yapilir. Sayfa turune gore:
//  - receipt: Asi Uygulama Belgesi Ekle -> doldurma akisi
//  - search : PetVet arama penceresi -> cip ile secim
//  - home   : e-Devlet donusu -> gerekiyorsa asi sayfasina gecis
//  - other  : bekleyen asi icin "Asi sayfasini ac" karti
import { chromeLocalStore, chromeSessionStore } from '../background/chromeStorage';
import { FLOW_KEY, createFlowStore, type FlowState } from '../shared/flowStore';
import type { Submission } from '../shared/types';
import { createPageBridge } from './core/bridge';
import { createCard } from './core/card';
import { shouldRedirectHome } from './core/home';
import { isKeepAliveEnabled, startKeepAlive } from './core/keepAlive';
import { createStockSync } from './pages/stockSync';
import { runStockPopupFlow } from './pages/stockPopup';
import { createReceiptFlow } from './pages/vaccineReceipt';
import { runSearchFlow, type Send } from './steps/findAnimal';
import { VACCINE_PAGE_URL, pageKind } from './selectors';
import { views } from './core/views';

const send: Send = (req) => chrome.runtime.sendMessage(req);
const flow = createFlowStore(chromeSessionStore());
const card = createCard(document);

async function showElsewhere(): Promise<void> {
  const res = await send<Submission | null>({ type: 'GET_ACTIVE' });
  if (!res.ok || !res.data || res.data.status !== 'PENDING') return;
  card.onAction((id) => {
    if (id === 'open') location.assign(VACCINE_PAGE_URL);
  });
  card.show(views.elsewhere(res.data));
}

async function home(): Promise<void> {
  const state = await flow.get();
  const now = Date.now();
  if (state && shouldRedirectHome(state, now)) {
    await flow.update(state.submissionId, { redirectedAt: now });
    location.assign(VACCINE_PAGE_URL);
    return;
  }
  await showElsewhere();
}

// Her TARBIL sekmesinde (hbsapp ve vetilac) oturumu canli tut; ayar yan panelden kapatilabilir.
startKeepAlive({
  origin: location.origin,
  enabled: () => isKeepAliveEnabled(chromeLocalStore()),
  ping: (url) => fetch(url, { credentials: 'include', cache: 'no-store', redirect: 'manual' }),
  setInterval: (fn, ms) => window.setInterval(fn, ms),
  clearInterval: (handle) => window.clearInterval(handle as number),
});

if (location.origin === 'https://vetilac.tarbil.gov.tr') {
  // vetilac'ta yalniz ilac stok sayfasi; digerlerinde yalniz oturum canli tutulur.
  if (pageKind(location) === 'medicineStock') startStockSync('medicineStock');
} else {
  routeTarbilPage();
}

function startStockSync(kind: 'vaccineStock' | 'medicineStock'): void {
  createStockSync({ bridge: createPageBridge(window), send, doc: document, card, kind }).start();
}

function routeTarbilPage(): void {
switch (pageKind(location)) {
  case 'receipt': {
    const receipt = createReceiptFlow({
      bridge: createPageBridge(window),
      flow,
      send,
      doc: document,
      card,
      now: Date.now,
      setTimer: (fn, ms) => {
        setTimeout(fn, ms);
      },
      observe: (cb) => {
        const mo = new MutationObserver(cb);
        mo.observe(document.body, { childList: true, subtree: true });
        return () => mo.disconnect();
      },
    });
    void receipt.start();
    chrome.storage.session.onChanged.addListener((changes) => {
      if ('activeSubmissionId' in changes) void receipt.start();
      else if (FLOW_KEY in changes) receipt.flowChanged((changes[FLOW_KEY].newValue as FlowState | undefined) ?? null);
    });
    break;
  }
  case 'search':
    void runSearchFlow({ bridge: createPageBridge(window), flow, send, doc: document, card, now: Date.now });
    break;
  case 'vaccineStockPopup':
    void runStockPopupFlow({
      bridge: createPageBridge(window),
      flow,
      send,
      doc: document,
      card,
      now: Date.now,
      today: () => {
        const d = new Date();
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
      },
    });
    break;
  case 'vaccineStock':
    startStockSync('vaccineStock');
    break;
  case 'home':
    void home();
    break;
  default:
    void showElsewhere();
}
}
