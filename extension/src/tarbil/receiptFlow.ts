import type { FlowState, FlowStore } from '../shared/flowStore';
import type { Submission } from '../shared/types';
import { normalizeChip, readReceiptChips } from './animalRows';
import type { PageBridge } from './bridge';
import type { Card } from './card';
import type { Send } from './searchFlow';
import { RECEIPT, bySuffix } from './selectors';
import { resolveAnimalType } from './species';
import { views } from './views';

export interface ReceiptDeps {
  bridge: PageBridge;
  flow: FlowStore;
  send: Send;
  doc: Document;
  card: Card;
  now: () => number;
  setTimer: (fn: () => void, ms: number) => void;
  observe: (cb: () => void) => () => void;
}

/** PetVet'e basildiktan sonra arama penceresinin akisi devralmasi icin beklenen sure. */
export const POPUP_WAIT_MS = 15_000;
/** Onayla tiklamasindan sonra basari panelinin "bu kayda ait" sayilacagi sure (onay penceresi dahil). */
export const SUCCESS_WINDOW_MS = 120_000;

const WAITING_FOR_ANIMAL = ['searching', 'transferred', 'needsVet'];
const code = (e: unknown) => (e as { code?: string })?.code ?? 'UNKNOWN';

/**
 * "Asi Uygulama Belgesi Ekle" sayfasi (spec S12.6): tarih -> tur -> PetVet aramasi; hayvanin forma eklendigini
 * cipten dogrular; hekim urunu ekleyip Onayla'ya basinca basariyi yakalar. Onayla'ya ASLA basmaz.
 */
export function createReceiptFlow(d: ReceiptDeps) {
  let sub: Submission | null = null;
  let stopObserving: (() => void) | null = null;
  let finishing = false;

  async function current(): Promise<FlowState | null> {
    const st = await d.flow.get();
    return st && sub && st.submissionId === sub.id ? st : null;
  }

  async function start(): Promise<void> {
    stopObserving?.();
    stopObserving = null;
    finishing = false;
    const res = await d.send<Submission | null>({ type: 'GET_ACTIVE' });
    if (!res.ok || !res.data || res.data.status === 'DISMISSED') {
      sub = null;
      d.card.hide();
      return;
    }
    sub = res.data;
    if (sub.status === 'SUBMITTED') {
      d.card.show(views.submitted(sub));
      return;
    }
    if (!d.doc.querySelector(bySuffix(RECEIPT.date))) {
      d.card.show(views.login(sub));
      return;
    }
    const st = await current();
    if (st?.step === 'armed') return fill();
    if (st && (WAITING_FOR_ANIMAL.includes(st.step) || st.step === 'awaitingConfirm')) {
      if (st.step !== 'awaitingConfirm') d.card.show(views.progress(sub, 'Hayvanın forma eklenmesi bekleniyor…'));
      else d.card.show(views.addProduct(sub));
      watch();
      return;
    }
    d.card.show(views.idle(sub));
  }

  async function fill(): Promise<void> {
    const s = sub!;
    const chip = normalizeChip(s.microchipNumber);
    const animalType = resolveAnimalType(s);
    if (!chip) {
      d.card.show(views.noChip(s));
      return;
    }
    if (!animalType) {
      d.card.show(views.unsupportedSpecies(s));
      return;
    }
    await d.flow.update(s.id, { step: 'filling' });
    try {
      d.card.show(views.progress(s, 'Uygulama tarihi ve tür giriliyor…'));
      await d.bridge.call('ready');
      await d.bridge.call('setDate', { iso: s.administeredDate });
      await d.bridge.call('selectAnimalType', { value: animalType });
      await openSearch();
    } catch (e) {
      await failed(e);
      return;
    }
    watch();
  }

  async function openSearch(): Promise<void> {
    const s = sub!;
    if (!(await current())) await d.flow.arm(s.id);
    await d.flow.update(s.id, { step: 'searching' });
    d.card.show(views.progress(s, 'PetVet arama penceresinde çip numarasıyla aranıyor…'));
    await d.bridge.call('clickPetVet');
    d.setTimer(() => {
      void (async () => {
        const st = await current();
        if (st?.step === 'searching' && d.now() - st.updatedAt >= POPUP_WAIT_MS) d.card.show(views.popupBlocked(s));
      })();
    }, POPUP_WAIT_MS);
  }

  async function failed(e: unknown): Promise<void> {
    const s = sub!;
    await d.flow.update(s.id, { step: 'error', message: code(e) });
    d.card.show(views.failed(s, code(e)));
  }

  function watch(): void {
    if (stopObserving) return;
    stopObserving = d.observe(() => void evaluate());
    void evaluate();
  }

  async function evaluate(): Promise<void> {
    const s = sub;
    if (!s || finishing) return;
    const st = await current();
    if (!st) return;
    if (st.step === 'awaitingConfirm') {
      const clickedRecently = st.insertClickedAt !== undefined && d.now() - st.insertClickedAt <= SUCCESS_WINDOW_MS;
      if (clickedRecently && d.doc.querySelector(bySuffix(RECEIPT.successPanel))) await confirm(s);
      return;
    }
    if (!WAITING_FOR_ANIMAL.includes(st.step)) return;
    const chips = readReceiptChips(d.doc);
    if (chips.includes(normalizeChip(s.microchipNumber))) {
      await d.flow.update(s.id, { step: 'awaitingConfirm' });
      d.card.show(views.addProduct(s));
    } else if (chips.length > 0) {
      d.card.show(views.wrongAnimal(s));
    }
  }

  async function confirm(s: Submission): Promise<void> {
    finishing = true;
    const r = await d.send<{ queued?: boolean }>({ type: 'MARK_SUBMITTED', id: s.id, method: 'AUTO', tarbilReference: null });
    if (!r.ok) {
      finishing = false;
      d.card.show(views.failed(s, r.code));
      return;
    }
    await d.flow.update(s.id, { step: 'done' });
    stopObserving?.();
    stopObserving = null;
    d.card.show(views.done(s, (r.data as { queued?: boolean } | undefined)?.queued === true));
  }

  // Hekimin Onayla tiklamasini fark et (yakalama asamasinda; TARBIL'in kendi isleyicisine dokunmadan).
  d.doc.addEventListener(
    'click',
    (e) => {
      const target = e.target as Element | null;
      if (!target?.closest || !RECEIPT.insertButtons.some((suffix) => target.closest(bySuffix(suffix)))) return;
      void (async () => {
        const st = await current();
        if (st?.step === 'awaitingConfirm') await d.flow.update(st.submissionId, { insertClickedAt: d.now() });
      })();
    },
    true,
  );

  d.card.onAction((id) => {
    const s = sub;
    if (!s) return;
    void (async () => {
      switch (id) {
        case 'fill':
          await d.flow.arm(s.id);
          await fill();
          return;
        case 'petvet':
          try {
            await openSearch();
            watch();
          } catch (e) {
            await failed(e);
          }
          return;
        case 'manual': {
          const r = await d.send<{ queued?: boolean }>({ type: 'MARK_SUBMITTED', id: s.id, method: 'MANUAL', tarbilReference: null });
          if (!r.ok) return d.card.show(views.failed(s, r.code));
          await d.flow.update(s.id, { step: 'done' });
          d.card.show(views.done(s, (r.data as { queued?: boolean } | undefined)?.queued === true));
          return;
        }
        case 'dismiss': {
          const r = await d.send({ type: 'DISMISS', id: s.id, reason: 'Bildirim gerekmiyor' });
          if (r.ok) d.card.hide();
          else d.card.show(views.failed(s, r.code));
          return;
        }
      }
    })();
  });

  return {
    start,
    /** Arama penceresi akis durumunu degistirdiginde (content.ts storage dinleyicisinden). */
    flowChanged(state: FlowState | null): void {
      const s = sub;
      if (!s || !state || state.submissionId !== s.id) return;
      if (state.step === 'needsVet') d.card.show(views.needsVet(s, state.message ?? ''));
      else if (state.step === 'transferred') d.card.show(views.progress(s, 'Hayvan forma aktarılıyor…'));
      if (WAITING_FOR_ANIMAL.includes(state.step)) watch();
    },
  };
}
