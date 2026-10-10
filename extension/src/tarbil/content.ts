// TARBIL icerik betigi (izole dunya): Vetly arka planiyla konusur, karti gosterir, akisi yonetir.
// Telerik islemleri page.js (MAIN dunya) uzerinden kopruyle yapilir. Sayfa turune gore:
//  - receipt            : Asi Uygulama Belgesi Ekle -> doldurma akisi
//  - prescriptionReceipt: Stoktan Duserek Ilac Recetesi Ekle -> satir satir doldurma akisi
//  - search             : PetVet arama penceresi (asi ya da recete) -> cip ile secim
//  - vaccineStockPopup / medicineStockPopup: stok penceresi -> seri ile satir secimi
//  - home               : e-Devlet donusu -> gerekiyorsa aktarimin sayfasina gecis
//  - other              : bekleyen aktarim icin "sayfayi ac" karti
import { chromeLocalStore, chromeSessionStore } from '../background/chromeStorage';
import { FLOW_KEY, createFlowStore, type FlowState } from '../shared/flowStore';
import type { Submission } from '../shared/types';
import { createPageBridge } from './core/bridge';
import { createCard } from './core/card';
import { shouldRedirectHome } from './core/home';
import { isKeepAliveEnabled, startKeepAlive } from './core/keepAlive';
import { classifyPing, recordPing } from './core/keepAliveStatus';
import { createStockSync } from './pages/stockSync';
import { runStockPopupFlow } from './pages/stockPopup';
import { createReceiptFlow } from './pages/vaccineReceipt';
import { runMedicineStockPopupFlow } from './pages/medicineStockPopup';
import { createPrescriptionFlow } from './pages/prescriptionReceipt';
import { runSearchFlow, type Send } from './steps/findAnimal';
import { pageKind, pageUrlFor } from './selectors';
import { views } from './core/views';
import { prescriptionViews } from './core/prescriptionViews';

const send: Send = (req) => chrome.runtime.sendMessage(req);
const flow = createFlowStore(chromeSessionStore());
const card = createCard(document);

async function showElsewhere(): Promise<void> {
  const res = await send<Submission | null>({ type: 'GET_ACTIVE' });
  if (!res.ok || !res.data || res.data.status !== 'PENDING') return;
  const s = res.data;
  const documentType = s.documentType ?? 'VACCINATION';
  card.onAction((id) => {
    if (id === 'open') location.assign(pageUrlFor(documentType));
  });
  card.show(documentType === 'PRESCRIPTION' ? prescriptionViews.elsewhere(s) : views.elsewhere(s));
}

async function home(): Promise<void> {
  const state = await flow.get();
  const now = Date.now();
  if (state && shouldRedirectHome(state, now)) {
    await flow.update(state.submissionId, { redirectedAt: now });
    location.assign(pageUrlFor(state.documentType));
    return;
  }
  await showElsewhere();
}

// Her TARBIL sekmesinde (hbsapp ve vetilac) oturumu canli tut; ayar yan panelden kapatilabilir.
startKeepAlive({
  origin: location.origin,
  enabled: () => isKeepAliveEnabled(chromeLocalStore()),
  // Sonuc kaydedilir (yan panel durumu; oturum kapaninca nedenini gormek icin). Arka plan da ayrica istek atar.
  ping: async (url) => {
    let result;
    try {
      result = classifyPing(await fetch(url, { credentials: 'include', cache: 'no-store', redirect: 'manual' }));
    } catch {
      result = classifyPing(new Error('ag'));
    }
    await recordPing(chromeLocalStore(), location.origin, 'tab', result, Date.now());
  },
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
  void createStockSync({ bridge: createPageBridge(window), send, doc: document, card, kind, store: chromeLocalStore(), now: Date.now }).start();
}

function receiptDeps() {
  return {
    bridge: createPageBridge(window),
    flow,
    send,
    doc: document,
    card,
    now: Date.now,
    setTimer: (fn: () => void, ms: number) => {
      setTimeout(fn, ms);
    },
    observe: (cb: () => void) => {
      const mo = new MutationObserver(cb);
      mo.observe(document.body, { childList: true, subtree: true });
      return () => mo.disconnect();
    },
    reload: () => location.reload(),
  };
}

function runReceiptPage(page: { start(): Promise<void>; flowChanged(state: FlowState | null): void }): void {
  void page.start();
  chrome.storage.session.onChanged.addListener((changes) => {
    if ('activeSubmissionId' in changes) void page.start();
    else if (FLOW_KEY in changes) page.flowChanged((changes[FLOW_KEY].newValue as FlowState | undefined) ?? null);
  });
}

function popupDeps() {
  return {
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
  };
}

function routeTarbilPage(): void {
switch (pageKind(location)) {
  case 'receipt':
    runReceiptPage(createReceiptFlow(receiptDeps()));
    break;
  case 'prescriptionReceipt':
    runReceiptPage(createPrescriptionFlow(receiptDeps()));
    break;
  case 'search':
    void runSearchFlow({ bridge: createPageBridge(window), flow, send, doc: document, card, now: Date.now });
    break;
  case 'vaccineStockPopup':
    void runStockPopupFlow(popupDeps());
    break;
  case 'medicineStockPopup':
    void runMedicineStockPopupFlow(popupDeps());
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
