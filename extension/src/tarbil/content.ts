// TARBIL icerik betigi (izole dunya): Vetly arka planiyla konusur, karti gosterir, akisi yonetir.
// Telerik islemleri page.js (MAIN dunya) uzerinden kopruyle yapilir. Sayfa turune gore:
//  - receipt: Asi Uygulama Belgesi Ekle -> doldurma akisi
//  - search : PetVet arama penceresi -> cip ile secim
//  - home   : e-Devlet donusu -> gerekiyorsa asi sayfasina gecis
//  - other  : bekleyen asi icin "Asi sayfasini ac" karti
import { chromeSessionStore } from '../background/chromeStorage';
import { FLOW_KEY, createFlowStore, type FlowState } from '../shared/flowStore';
import type { Submission } from '../shared/types';
import { createPageBridge } from './bridge';
import { createCard } from './card';
import { shouldRedirectHome } from './home';
import { createReceiptFlow } from './receiptFlow';
import { runSearchFlow, type Send } from './searchFlow';
import { VACCINE_PAGE_URL, pageKind } from './selectors';
import { views } from './views';

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
  case 'home':
    void home();
    break;
  default:
    void showElsewhere();
}
