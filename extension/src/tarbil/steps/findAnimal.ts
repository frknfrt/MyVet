import type { BackgroundRequest, BackgroundResponse } from '../../shared/messages';
import type { FlowStore } from '../../shared/flowStore';
import type { Submission } from '../../shared/types';
import { normalizeChip, normalizePassport, pickAnimal, readSearchRows } from './animalRows';
import type { PageBridge } from '../core/bridge';
import type { Card } from '../core/card';
import { views } from '../core/views';

export type Send = <T>(req: BackgroundRequest) => Promise<BackgroundResponse<T>>;

export interface SearchDeps {
  bridge: PageBridge;
  flow: FlowStore;
  send: Send;
  doc: Document;
  card: Card;
  now: () => number;
}

/** Ana sayfa PetVet'e bastiktan sonra pencerenin bu sure icinde acilmasi beklenir; daha eski "searching" yok sayilir. */
export const SEARCH_HANDOFF_MS = 120_000;

const code = (e: unknown) => (e as { code?: string })?.code ?? 'UNKNOWN';

/**
 * PetVet hayvan arama penceresi (spec S3, S12.4): cip (yoksa pasaport no) yazilir, Ara'ya basilir; birebir eslesen
 * TEK ve CANLI satir varsa isaretlenip Transfer Et'e basilir. Pasaportla bulunan hayvanin TARBIL'deki cipi akisa
 * yazilir: ana sayfa forma eklenen hayvani o ciple dogrular. Diger her durumda karar hekime birakilir. Pencereyi hekim
 * kendisi actiysa (akis "searching" degil) hicbir sey yapilmaz.
 */
export async function runSearchFlow(d: SearchDeps): Promise<void> {
  const state = await d.flow.get();
  if (!state || state.step !== 'searching' || d.now() - state.updatedAt > SEARCH_HANDOFF_MS) return;
  const res = await d.send<Submission | null>({ type: 'GET_ACTIVE' });
  const s = res.ok ? res.data : null;
  const chip = normalizeChip(s?.microchipNumber);
  const passport = chip ? '' : normalizePassport(s?.passportNumber);
  if (!s || s.id !== state.submissionId || (!chip && !passport)) return;
  const by = chip ? 'çip' : 'pasaport';

  const needsVet = async (message: string) => {
    await d.flow.update(s.id, { step: 'needsVet', message });
    d.card.show(views.popup(message, 'warn'));
  };

  d.card.show(views.popup(`Vetly: ${by} numarasıyla aranıyor…`, 'muted'));
  try {
    await d.bridge.call('ready');
    if (chip) await d.bridge.call('searchChip', { chip });
    else await d.bridge.call('searchPassport', { passport: s.passportNumber!.trim() });
  } catch (e) {
    await needsVet(`Arama yapılamadı (${code(e)}). ${chip ? 'Çip' : 'Pasaport'} numarasını kendiniz aratın.`);
    return;
  }

  const pick = pickAnimal(readSearchRows(d.doc), { chip, passport });
  switch (pick.kind) {
    case 'none':
      await needsVet(`Bu ${by} numarasıyla TARBİL'de hayvan bulunamadı. Numarayı kontrol edin; hayvan kayıtlı değilse önce kimliklendirme gerekir.`);
      return;
    case 'mismatch': {
      const theirs = pick.listed.map((r) => (chip ? r.chip : r.passport) || '—').join(', ');
      await needsVet(
        `TARBİL ${pick.listed.length} hayvan listeledi ama ${by} numarası Vetly'dekiyle (${chip || s.passportNumber}) eşleşmedi; TARBİL'deki: ${theirs}. `
          + "Doğru satırı kendiniz işaretleyip Transfer Et'e basın; gerekirse Vetly'deki numarayı düzeltin.",
      );
      return;
    }
    case 'many':
      await needsVet(`Bu ${by} numarasıyla birden fazla hayvan çıktı. Doğru satırı kendiniz işaretleyip Transfer Et'e basın.`);
      return;
    case 'notAlive':
      await needsVet(`Hayvanın TARBİL'deki durumu "${pick.row.status ?? '—'}". Otomatik seçilmedi; kontrol edip kendiniz seçin.`);
      return;
    case 'one':
      // Forma eklenen hayvan cipiyle dogrulanir; pasaportla bulunan hayvanin TARBIL'de cipi yoksa dogrulanamaz.
      if (!chip && !pick.row.chip) {
        await needsVet("Hayvanın TARBİL'de çip kaydı yok, forma eklenince doğrulayamayız. Hayvanı kendiniz işaretleyip Transfer Et'e basın.");
        return;
      }
      if (!pick.row.checkboxId) {
        await needsVet("Satır işaretlenemedi. Hayvanı kendiniz işaretleyip Transfer Et'e basın.");
        return;
      }
      try {
        await d.bridge.call('checkRow', { checkboxId: pick.row.checkboxId });
      } catch (e) {
        await needsVet(`Satır işaretlenemedi (${code(e)}). Hayvanı kendiniz işaretleyip Transfer Et'e basın.`);
        return;
      }
      d.card.show(views.popup('Vetly: hayvan forma aktarılıyor…', 'muted'));
      // Transfer Et pencereyi kapatir; yanit gelmeyebilir. Durum once yazilir, ana sayfa sonucu tablodan dogrular.
      await d.flow.update(s.id, { step: 'transferred', ...(chip ? {} : { stepData: { matchedChip: pick.row.chip } }) });
      await d.bridge.call('clickAllowed', { page: 'animalSearch', button: 'transfer' }, 5000).catch(() => undefined);
  }
}
