import type { PlannedLine, Submission } from '../../shared/types';
import type { CardAction, CardLine, CardView, Tone } from './card';

function header(s: Submission): CardLine[] {
  const p = s.prescription;
  return [
    { text: s.patientName, tone: 'strong' },
    { text: `Reçete · ${p?.tarbilDiseaseName ?? 'teşhis yok'} · ${p?.lines.length ?? 0} ilaç`, tone: 'muted' },
  ];
}

export const lineText = (l: PlannedLine) => `${l.tarbilProductName ?? l.itemName} · Seri ${l.lotNumber ?? '—'} · ${l.quantity} adet`;

const FALLBACK: CardAction[] = [
  { id: 'manual', label: 'Kaydedildi olarak işaretle' },
  { id: 'dismiss', label: 'Bildirilmeyecek' },
];

/** Sorunlu satirdan sonra: kaydedilmis satirlardan siradaki ilac yeniden hesaplanir. */
const CONTINUE: CardAction[] = [{ id: 'stockWindow', label: 'Devam et' }, ...FALLBACK];

const view = (s: Submission, text: string, tone: Tone | undefined, actions: CardAction[] = FALLBACK): CardView => ({
  lines: [...header(s), { text, tone }],
  actions,
});

/** "Stoktan Duserek Ilac Recetesi Ekle" sayfasindaki kart metinleri (spec 2026-10-09 S7). */
export const prescriptionViews = {
  submitted: (s: Submission): CardView =>
    view(s, `✓ Bu reçete ${new Date(s.submittedAt!).toLocaleDateString('tr-TR')} tarihinde TARBİL'e kaydedilmiş. Tekrar girmeyin.`, 'ok', []),
  login: (s: Submission): CardView =>
    view(s, 'Reçete formu bulunamadı. TARBİL oturumunuz kapanmış olabilir: e-Devlet ile giriş yapın; girişten sonra reçete sayfasına kendimiz geçeceğiz.', 'warn', []),
  idle: (s: Submission): CardView =>
    view(s, 'Tedavi tarihini, teşhisi ve türü girip hayvanı çip (yoksa pasaport) numarasıyla bulacağız; ilaçları stoktan satır satır ekleyeceğiz.', 'muted',
      [{ id: 'fill', label: 'Formu doldur' }, ...FALLBACK]),
  wrongPage: (s: Submission): CardView =>
    ({ lines: [{ text: s.patientName, tone: 'strong' }, { text: 'Bu bir aşı aktarımı; "Aşı Uygulama Belgesi Ekle" sayfasında doldurulur.', tone: 'muted' }], actions: [] }),
  noChip: (s: Submission): CardView =>
    view(s, "Çip ve pasaport numarası yok. Vetly'de hastaya birini girin; hayvan TARBİL'de kayıtlı değilse önce kimliklendirme gerekir.", 'warn'),
  unsupportedSpecies: (s: Submission): CardView =>
    view(s, `Otomatik doldurma yalnız kedi ve köpek için (${s.speciesName ?? 'tür bilinmiyor'}). Formu kendiniz doldurun.`, 'warn'),
  noDisease: (s: Submission): CardView =>
    view(s, "Reçetede TARBİL teşhisi yok. Vetly'de teşhis seçin ya da formu kendiniz doldurun.", 'warn'),
  nothingToDeduct: (s: Submission): CardView =>
    view(s, 'TARBİL düşümü bekleyen ilaç kalmadı.', 'muted'),
  progress: (s: Submission, text: string): CardView => view(s, text, 'muted'),
  searching: (s: Submission): CardView =>
    view(s, 'PetVet arama penceresinde hayvan aranıyor… Pencere açılmadıysa aşağıdaki butona basın.', 'muted', [
      { id: 'petvet', label: 'Arama penceresini aç' },
      ...FALLBACK,
    ]),
  popupBlocked: (s: Submission): CardView =>
    view(s, 'Hayvan arama penceresi açılmadı. TARBİL için açılır pencerelere izin verin ya da aşağıdaki butona basın.', 'warn', [
      { id: 'petvet', label: 'Arama penceresini aç' },
      ...FALLBACK,
    ]),
  needsVet: (s: Submission, message: string): CardView => view(s, `${message} Hayvan forma eklenince devam edeceğiz.`, 'warn'),
  wrongAnimal: (s: Submission): CardView =>
    view(s, `Forma eklenen hayvan Vetly'deki hastayla (${s.microchipNumber ? `çip ${s.microchipNumber}` : `pasaport ${s.passportNumber ?? '—'}`}) aynı değil. Yanlış satırı silip doğru hayvanı ekleyin.`, 'warn'),
  dirtyForm: (s: Submission): CardView =>
    view(s, "Bu formda başka bir hastaya ya da başka ilaçlara ait yarım kalmış bir reçete var. Onu tamamlayıp Reçeteyi Onayla'ya basın ya da formu sıfırlayın (TARBİL'de hiçbir şey kaydedilmez).", 'warn',
      [{ id: 'resetForm', label: 'Formu sıfırla' }, ...FALLBACK]),
  choosingLine: (s: Submission, line: PlannedLine, index: number): CardView =>
    view(s, `✓ Hayvan formda. İlaç ${index + 1}/${s.prescription?.lines.length ?? 0}: ${lineText(line)} stoktan seçiliyor…`, 'muted'),
  stockWindowBlocked: (s: Submission): CardView =>
    view(s, 'Stok penceresi açılmadı. TARBİL için açılır pencerelere izin verin ya da aşağıdaki butona basın.', 'warn', [
      { id: 'stockWindow', label: 'Stok penceresini aç' },
      ...FALLBACK,
    ]),
  lineReady: (s: Submission, line: PlannedLine): CardView =>
    view(s, `✓ Satır hazır: ${lineText(line)} (Adet ve Miktar ${line.quantity}). Satırdaki Kaydet'e siz basın; sonraki ilaca geçeceğiz.`, 'ok'),
  wrongLine: (s: Submission, line: PlannedLine | undefined): CardView =>
    view(s, `Yanlış ürün seçildi: satırın serisi ${line?.lotNumber ?? '—'} olmalı. Satırı İptal edip "Devam et"e basın.`, 'warn', CONTINUE),
  lineNeedsVet: (s: Submission, message: string): CardView =>
    view(s, `${message} İlacı "Stok Ekle"den kendiniz ekleyin ya da satırı kaydettikten sonra "Devam et"e basın; tüm ilaçlar eklenince Reçeteyi Onayla'ya basın.`, 'warn', CONTINUE),
  /** TARBIL kaydi tamam ama Vetly reddetti: formu yeniden doldurmak mukerrer recete riskidir, yalniz elle kapatma. */
  confirmFailed: (s: Submission, code: string): CardView =>
    view(s, `TARBİL'e kaydedildi ama Vetly'ye işlenemedi (${code}). Formu tekrar doldurmayın; aşağıdan kapatın.`, 'warn'),
  allLinesReady: (s: Submission): CardView =>
    view(s, "✓ Tüm ilaçlar eklendi. Teşhis, hayvan ve satırları kontrol edip Reçeteyi Onayla'ya basın. Kaydı yakalayıp Vetly'ye işleyeceğiz.", 'ok'),
  done: (s: Submission, queued: boolean): CardView =>
    view(s, queued
      ? "✓ TARBİL'e kaydedildi. Vetly'ye şu an ulaşılamıyor; bağlantı gelince otomatik işlenecek."
      : "✓ TARBİL'e kaydedildi; Vetly stoğu düşüldü.", 'ok', []),
  failed: (s: Submission, code: string): CardView =>
    view(s, `Otomatik doldurma durdu (${code}). Kalan adımları TARBİL'de kendiniz tamamlayabilirsiniz.`, 'warn', [
      { id: 'fill', label: 'Yeniden dene' },
      ...FALLBACK,
    ]),
  elsewhere: (s: Submission): CardView =>
    ({ lines: [...header(s), { text: 'Bu reçeteyi "Stoktan Düşerek İlaç Reçetesi Ekle" sayfasında dolduracağız.', tone: 'muted' }],
      actions: [{ id: 'open', label: 'Reçete sayfasını aç' }] }),
};
