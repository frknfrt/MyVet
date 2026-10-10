import type { FlowState } from '../../shared/flowStore';
import type { ConfirmedLine, Submission } from '../../shared/types';
import { prescriptionViews as views } from '../core/prescriptionViews';
import { hasFreshSuccess, isConfirmClick, markStaleSuccess } from '../core/success';
import { PRESCRIPTION_RECEIPT, bySuffix } from '../selectors';
import { countReceiptAnimals, normalizeChip, normalizePassport, readReceiptChips } from '../steps/animalRows';
import { normalizeSerial } from '../steps/productRows';
import { resolveAnimalType } from '../steps/species';
import { confirmedLines, nextLine, readSavedStockLines, readStockEditRow } from '../steps/stockLines';
import { POPUP_WAIT_MS, SUCCESS_WINDOW_MS, type ReceiptDeps } from './vaccineReceipt';

const WAITING_FOR_ANIMAL = ['searching', 'transferred', 'needsVet'];
/** Receteyi Onayla + basari yakalamasinin gecerli oldugu adimlar. */
const CONFIRM_STEPS = ['awaitingConfirm', 'choosingProduct', 'productReady'];
const code = (e: unknown) => (e as { code?: string })?.code ?? 'UNKNOWN';

/**
 * "Stoktan Duserek Ilac Recetesi Ekle" sayfasi (spec 2026-10-09 S7, S11): tarih -> sinif -> tur -> teshis -> PetVet;
 * hayvan dogrulaninca ilaclar satir satir: "Stok Ekle" + stok penceresi (seri ile secim) + Adet/Miktar. Satir Kaydet'e ve
 * Receteyi Onayla'ya hekim basar; basari yakalaninca kaydedilen satirlar Vetly'ye gider. Resmi butonlara ASLA basmaz.
 */
export function createPrescriptionFlow(d: ReceiptDeps) {
  let sub: Submission | null = null;
  let stopObserving: (() => void) | null = null;
  let finishing = false;
  let starting: Promise<void> | null = null;
  let evaluating: Promise<void> = Promise.resolve();
  const staleSuccess = new WeakSet<Element>();

  async function current(): Promise<FlowState | null> {
    const st = await d.flow.get();
    return st && sub && st.submissionId === sub.id ? st : null;
  }

  function start(): Promise<void> {
    starting ??= run().finally(() => {
      starting = null;
    });
    return starting;
  }

  async function run(): Promise<void> {
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
    if (sub.documentType !== 'PRESCRIPTION') {
      d.card.show(views.wrongPage(sub));
      return;
    }
    if (sub.status === 'SUBMITTED') {
      d.card.show(views.submitted(sub));
      return;
    }
    if (!d.doc.querySelector(bySuffix(PRESCRIPTION_RECEIPT.date))) {
      d.card.show(views.login(sub));
      return;
    }
    const st = await current();
    if (st?.step === 'armed') return fill();
    if (st && (WAITING_FOR_ANIMAL.includes(st.step) || CONFIRM_STEPS.includes(st.step))) {
      d.card.show(stepView(sub, st));
      watch();
      return;
    }
    d.card.show(views.idle(sub));
  }

  /** Sayfa yeniden yuklenince (tam postback, F5) adimin karti: aciklama ve eylemler kaybolmasin. */
  function stepView(s: Submission, st: FlowState) {
    const line = s.prescription?.lines[Number(st.stepData?.lineIndex)];
    switch (st.step) {
      case 'searching':
        return views.searching(s);
      case 'needsVet':
        return views.needsVet(s, st.message ?? '');
      case 'choosingProduct':
        // Onceki sayfanin pencere zamanlayicisi gitti: ekleme satiri bossa stok penceresi yeniden acilabilsin.
        return readStockEditRow(d.doc)?.serial ? views.progress(s, 'Seçilen ilaç kontrol ediliyor…') : views.stockWindowBlocked(s);
      case 'productReady':
        return line ? views.lineReady(s, line) : views.progress(s, 'Kaldığı yerden devam ediliyor…');
      case 'awaitingConfirm':
        return st.message ? views.lineNeedsVet(s, st.message) : views.allLinesReady(s);
      default:
        return views.progress(s, 'Kaldığı yerden devam ediliyor…');
    }
  }

  async function fill(): Promise<void> {
    const s = sub!;
    const p = s.prescription;
    const chip = normalizeChip(s.microchipNumber) || normalizePassport(s.passportNumber);
    const animalType = resolveAnimalType(s);
    const stop = async (message: string, v: ReturnType<typeof views.noChip>) => {
      await d.flow.update(s.id, { step: 'error', message });
      d.card.show(v);
    };
    if (!p || p.lines.length === 0) return stop('NO_LINES', views.nothingToDeduct(s));
    if (!p.tarbilDiseaseId) return stop('NO_DISEASE', views.noDisease(s));
    if (!chip) return stop('NO_CHIP', views.noChip(s));
    if (!animalType) return stop('UNSUPPORTED_SPECIES', views.unsupportedSpecies(s));
    const form = inspectForm(s, await current());
    if (form === 'dirty') return stop('DIRTY_FORM', views.dirtyForm(s));
    if (form === 'resume') {
      try {
        await ensureDisease(p.tarbilDiseaseId);
        await continueLines(s);
      } catch (e) {
        await failed(e);
        return;
      }
      watch();
      return;
    }
    await d.flow.update(s.id, { step: 'filling' });
    try {
      d.card.show(views.progress(s, 'Tedavi tarihi, reçete sınıfı, tür ve teşhis giriliyor…'));
      await d.bridge.call('ready');
      await d.bridge.call('setTreatmentDate', { iso: p.treatmentStartDate });
      await d.bridge.call('ensureTreatmentClass');
      await d.bridge.call('selectPrescriptionAnimalType', { value: animalType });
      await ensureDisease(p.tarbilDiseaseId);
      await openSearch();
    } catch (e) {
      await failed(e);
      return;
    }
    watch();
  }

  async function ensureDisease(value: string): Promise<void> {
    const r = await d.bridge.call<{ listed: boolean }>('addDisease', { value });
    if (!r?.listed) throw Object.assign(new Error('Teşhis eklenemedi'), { code: 'DISEASE_NOT_ADDED' });
  }

  /** empty: bos form; resume: yalniz bu hastanin hayvani ve bu recetenin ilaclari; dirty: baska hayvan/ilac var. */
  function inspectForm(s: Submission, st: FlowState | null): 'empty' | 'resume' | 'dirty' {
    const animals = countReceiptAnimals(d.doc);
    const saved = readSavedStockLines(d.doc);
    const edit = readStockEditRow(d.doc);
    if (animals === 0 && saved.length === 0 && !edit?.serial) return 'empty';
    const chips = readReceiptChips(d.doc);
    const expected = normalizeChip(s.microchipNumber) || normalizeChip(st?.stepData?.matchedChip as string | undefined);
    const ownAnimal = animals === 1 && expected.length > 0 && chips.length === 1 && chips[0] === expected;
    const planned = new Set((s.prescription?.lines ?? []).map((l) => normalizeSerial(l.lotNumber)));
    const ownLines = saved.every((l) => planned.has(l.serial)) && (!edit?.serial || planned.has(edit.serial));
    return ownAnimal && ownLines ? 'resume' : 'dirty';
  }

  async function openSearch(): Promise<void> {
    const s = sub!;
    if (!(await current())) await d.flow.arm(s.id, 'PRESCRIPTION');
    await d.flow.update(s.id, { step: 'searching' });
    d.card.show(views.progress(s, 'PetVet arama penceresinde hayvan aranıyor…'));
    await d.bridge.call('clickAllowed', { page: 'prescriptionReceipt', button: 'petVet' });
    d.card.show(views.searching(s));
    d.setTimer(() => {
      void (async () => {
        const st = await current();
        if (st?.step === 'searching' && d.now() - st.updatedAt >= POPUP_WAIT_MS) d.card.show(views.popupBlocked(s));
      })();
    }, POPUP_WAIT_MS);
  }

  /** Hayvan dogrulandi ya da bir satir kaydedildi: siradaki kaydedilmemis ilac icin "Stok Ekle" + stok penceresi. */
  async function continueLines(s: Submission): Promise<void> {
    const lines = s.prescription?.lines ?? [];
    const i = nextLine(lines, readSavedStockLines(d.doc));
    if (i < 0) {
      await d.flow.update(s.id, { step: 'awaitingConfirm', message: undefined });
      d.card.show(views.allLinesReady(s));
      return;
    }
    const line = lines[i];
    const st = await current();
    await d.flow.update(s.id, { step: 'choosingProduct', message: undefined, stepData: { ...st?.stepData, lineIndex: i } });
    const edit = readStockEditRow(d.doc);
    if (edit?.serial) {
      await checkEditRow(s);
      return;
    }
    if (!line.lotNumber) {
      const message = `${line.tarbilProductName ?? line.itemName} için Vetly'de seri numarası yok.`;
      await d.flow.update(s.id, { step: 'awaitingConfirm', message });
      d.card.show(views.lineNeedsVet(s, message));
      return;
    }
    d.card.show(views.choosingLine(s, line, i));
    try {
      if (!edit) await d.bridge.call('clickAllowed', { page: 'prescriptionReceipt', button: 'addStock' });
      await d.bridge.call('clickAllowed', { page: 'prescriptionReceipt', button: 'stockSearch' });
    } catch (e) {
      const message = `Stok satırı açılamadı (${code(e)}).`;
      await d.flow.update(s.id, { step: 'awaitingConfirm', message });
      d.card.show(views.lineNeedsVet(s, message));
      return;
    }
    d.setTimer(() => {
      void (async () => {
        const later = await current();
        if (later?.step === 'choosingProduct' && d.now() - later.updatedAt >= POPUP_WAIT_MS && !readStockEditRow(d.doc)?.serial) {
          d.card.show(views.stockWindowBlocked(s));
        }
      })();
    }, POPUP_WAIT_MS);
  }

  /** Stok penceresinden secilen ilac ekleme satirina geldi mi: seri planlanan seriyse Adet ve Miktar yazilir. */
  async function checkEditRow(s: Submission): Promise<void> {
    const st = await current();
    const line = s.prescription?.lines[Number(st?.stepData?.lineIndex)];
    const edit = readStockEditRow(d.doc);
    if (!line || !edit?.serial) return;
    if (edit.serial !== normalizeSerial(line.lotNumber)) {
      await d.flow.update(s.id, { step: 'awaitingConfirm' });
      d.card.show(views.wrongLine(s, line));
      return;
    }
    try {
      await d.bridge.call('setStockQuantities', { quantity: line.quantity });
    } catch (e) {
      const message = `Adet yazılamadı (${code(e)}).`;
      await d.flow.update(s.id, { step: 'awaitingConfirm', message });
      d.card.show(views.lineNeedsVet(s, message));
      return;
    }
    await d.flow.update(s.id, { step: 'productReady' });
    d.card.show(views.lineReady(s, line));
  }

  async function failed(e: unknown): Promise<void> {
    const s = sub!;
    await d.flow.update(s.id, { step: 'error', message: code(e) });
    d.card.show(views.failed(s, code(e)));
  }

  function watch(): void {
    if (stopObserving) return;
    stopObserving = d.observe(() => void scheduleEvaluate());
    void scheduleEvaluate();
  }

  function scheduleEvaluate(): Promise<void> {
    evaluating = evaluating.then(evaluate, evaluate);
    return evaluating;
  }

  async function evaluate(): Promise<void> {
    const s = sub;
    if (!s || finishing) return;
    const st = await current();
    if (!st) return;
    if (CONFIRM_STEPS.includes(st.step)) {
      const clickedRecently = st.insertClickedAt !== undefined && d.now() - st.insertClickedAt <= SUCCESS_WINDOW_MS;
      if (clickedRecently && hasFreshSuccess(d.doc, PRESCRIPTION_RECEIPT.successPanel, staleSuccess)) {
        await confirm(s, st);
        return;
      }
      if (st.step === 'choosingProduct') {
        await checkEditRow(s);
      } else if (st.step === 'productReady') {
        const line = s.prescription?.lines[Number(st.stepData?.lineIndex)];
        const saved = readSavedStockLines(d.doc).map((l) => l.serial);
        if (line && saved.includes(normalizeSerial(line.lotNumber))) await continueLines(s);
      }
      return;
    }
    if (!WAITING_FOR_ANIMAL.includes(st.step)) return;
    const chips = readReceiptChips(d.doc);
    const expected = normalizeChip(s.microchipNumber) || normalizeChip(st.stepData?.matchedChip as string | undefined);
    if (expected && chips.includes(expected)) {
      await continueLines(s);
    } else if (expected && chips.length > 0) {
      d.card.show(views.wrongAnimal(s));
    }
  }

  async function confirm(s: Submission, st: FlowState): Promise<void> {
    finishing = true;
    const lines = (st.stepData?.confirmedLines as ConfirmedLine[] | undefined)
      ?? confirmedLines(readSavedStockLines(d.doc), s.prescription?.lines ?? []);
    const r = await d.send<{ queued?: boolean }>({ type: 'MARK_SUBMITTED', id: s.id, method: 'AUTO', tarbilReference: null, lines });
    if (!r.ok) {
      finishing = false;
      d.card.show(views.confirmFailed(s, r.code));
      return;
    }
    await d.flow.update(s.id, { step: 'done' });
    stopObserving?.();
    stopObserving = null;
    d.card.show(views.done(s, (r.data as { queued?: boolean } | undefined)?.queued === true));
  }

  // Hekimin Receteyi Onayla tiklamasi: kaydedilmis satirlar tiklama aninda okunur (onaydan sonra form temizlenebilir).
  d.doc.addEventListener(
    'click',
    (e) => {
      if (!isConfirmClick(e.target, PRESCRIPTION_RECEIPT.insertButtons)) return;
      markStaleSuccess(d.doc, PRESCRIPTION_RECEIPT.successPanel, staleSuccess);
      const snapshot = confirmedLines(readSavedStockLines(d.doc), sub?.prescription?.lines ?? []);
      void (async () => {
        const st = await current();
        if (st && CONFIRM_STEPS.includes(st.step)) {
          await d.flow.update(st.submissionId, { insertClickedAt: d.now(), stepData: { ...st.stepData, confirmedLines: snapshot } });
        }
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
          await d.flow.arm(s.id, 'PRESCRIPTION');
          await start();
          return;
        case 'petvet':
          try {
            await openSearch();
            watch();
          } catch (e) {
            await failed(e);
          }
          return;
        case 'resetForm':
          await d.flow.arm(s.id, 'PRESCRIPTION');
          d.reload();
          return;
        case 'stockWindow':
          try {
            await continueLines(s);
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
    /** Arama/stok penceresi akis durumunu degistirdiginde (content.ts storage dinleyicisinden). */
    flowChanged(state: FlowState | null): void {
      const s = sub;
      if (!s || !state || state.submissionId !== s.id) return;
      if (state.step === 'armed') {
        void start();
        return;
      }
      if (state.step === 'awaitingConfirm' && state.message) {
        d.card.show(views.lineNeedsVet(s, state.message));
        return;
      }
      if (state.step === 'needsVet') d.card.show(views.needsVet(s, state.message ?? ''));
      else if (state.step === 'transferred') d.card.show(views.progress(s, 'Hayvan forma aktarılıyor…'));
      if (WAITING_FOR_ANIMAL.includes(state.step)) watch();
    },
  };
}
