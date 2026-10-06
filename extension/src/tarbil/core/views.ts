import type { Submission } from '../../shared/types';
import type { CardAction, CardLine, CardView, Tone } from './card';

function trDate(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  return m ? `${m[3]}.${m[2]}.${m[1]}` : iso;
}

function header(s: Submission): CardLine[] {
  return [
    { text: s.patientName, tone: 'strong' },
    { text: `${s.vaccineName}${s.lotNumber ? ` · Lot ${s.lotNumber}` : ''} · ${trDate(s.administeredDate)}`, tone: 'muted' },
  ];
}

const FALLBACK: CardAction[] = [
  { id: 'manual', label: 'Kaydedildi olarak işaretle' },
  { id: 'dismiss', label: 'Bildirilmeyecek' },
];

const view = (s: Submission, text: string, tone: Tone | undefined, actions: CardAction[] = FALLBACK): CardView => ({
  lines: [...header(s), { text, tone }],
  actions,
});

export const views = {
  submitted: (s: Submission): CardView =>
    view(s, `✓ Bu aşı ${new Date(s.submittedAt!).toLocaleDateString('tr-TR')} tarihinde TARBİL'e kaydedilmiş. Tekrar girmeyin.`, 'ok', []),
  login: (s: Submission): CardView =>
    view(
      s,
      'Aşı formu bulunamadı. TARBİL oturumunuz kapanmış olabilir: e-Devlet ile giriş yapın; girişten sonra aşı sayfasına kendimiz geçeceğiz.',
      'warn',
      [],
    ),
  idle: (s: Submission): CardView =>
    view(s, 'Uygulama tarihini ve türü girip hayvanı çip (yoksa pasaport) numarasıyla bulacağız.', 'muted', [{ id: 'fill', label: 'Formu doldur' }, ...FALLBACK]),
  noChip: (s: Submission): CardView =>
    view(s, "Çip ve pasaport numarası yok. Vetly'de hastaya birini girin; hayvan TARBİL'de kayıtlı değilse önce kimliklendirme gerekir.", 'warn'),
  unsupportedSpecies: (s: Submission): CardView =>
    view(s, `Otomatik doldurma yalnız kedi ve köpek için (${s.speciesName ?? 'tür bilinmiyor'}). Formu kendiniz doldurun.`, 'warn'),
  progress: (s: Submission, text: string): CardView => view(s, text, 'muted'),
  searching: (s: Submission): CardView =>
    view(s, 'PetVet arama penceresinde hayvan aranıyor… Pencere açılmadıysa aşağıdaki butona basın.', 'muted', [
      { id: 'petvet', label: 'Arama penceresini aç' },
      ...FALLBACK,
    ]),
  popupBlocked: (s: Submission): CardView =>
    view(
      s,
      'Hayvan arama penceresi açılmadı. Tarayıcı açılır pencereyi engellemiş olabilir: adres çubuğundaki simgeden TARBİL için açılır pencerelere izin verin ya da aşağıdaki butona basın.',
      'warn',
      [{ id: 'petvet', label: 'Arama penceresini aç' }, ...FALLBACK],
    ),
  needsVet: (s: Submission, message: string): CardView =>
    view(s, `${message} Hayvan forma eklenince devam edeceğiz.`, 'warn'),
  wrongAnimal: (s: Submission): CardView =>
    view(s, `Forma eklenen hayvan Vetly'deki hastayla (${s.microchipNumber ? `çip ${s.microchipNumber}` : `pasaport ${s.passportNumber ?? '—'}`}) aynı değil. Yanlış satırı silip doğru hayvanı ekleyin.`, 'warn'),
  resumeReady: (s: Submission): CardView => ({
    lines: [
      ...header(s),
      { text: `✓ Bu aşının hayvanı ve ürünü (Seri ${s.lotNumber ?? '—'}) formda zaten var; yeniden eklenmedi.`, tone: 'ok' },
      { text: "Detay alanlarını kontrol edip Onayla'ya basın. Kaydı yakalayıp Vetly'ye işleyeceğiz." },
    ],
    actions: FALLBACK,
  }),
  dirtyForm: (s: Submission): CardView =>
    view(
      s,
      "Bu formda başka bir hastaya ya da başka bir ürüne ait yarım kalmış bir belge var. Onu tamamlayıp Onayla'ya basın ya da formu sıfırlayın (TARBİL'de hiçbir şey kaydedilmez; satır Kaydet'e basılmışsa TARBİL stoğundan düşmüş olabilir).",
      'warn',
      [{ id: 'resetForm', label: 'Formu sıfırla' }, ...FALLBACK],
    ),
  choosingProduct: (s: Submission): CardView =>
    view(s, `✓ Hayvan forma eklendi. Stok penceresinde seri ${s.lotNumber ?? '—'} aranıyor…`, 'muted'),
  stockWindowBlocked: (s: Submission): CardView =>
    view(
      s,
      'Stok penceresi açılmadı. Tarayıcı açılır pencereyi engellemiş olabilir: TARBİL için açılır pencerelere izin verin ya da aşağıdaki butona basın.',
      'warn',
      [{ id: 'stockWindow', label: 'Stok penceresini aç' }, ...FALLBACK],
    ),
  productReady: (s: Submission): CardView => ({
    lines: [
      ...header(s),
      { text: `✓ Ürün satırı hazır: ${s.tarbilProductName ?? s.vaccineName} · Seri ${s.lotNumber ?? '—'} · 1 adet.`, tone: 'ok' },
      { text: "Satırdaki Kaydet'e siz basın (TARBİL stoğundan düşer), Detay alanlarını kontrol edin, sonra Onayla'ya basın. Kaydı yakalayıp Vetly'ye işleyeceğiz." },
    ],
    actions: FALLBACK,
  }),
  productNeedsVet: (s: Submission, message: string): CardView =>
    view(s, `${message} "Ürün Ekle"den aşıyı stoktan kendiniz seçin, kontrol edip Onayla'ya basın.`, 'warn'),
  wrongProduct: (s: Submission): CardView =>
    view(s, `Yanlış ürün seçildi: formdaki serinin Vetly'deki seriyle (${s.lotNumber ?? '—'}) aynı olması gerekir. Satırı İptal edip doğru seriyi seçin.`, 'warn'),
  addProduct: (s: Submission): CardView => ({
    lines: [
      ...header(s),
      { text: '✓ Hayvan forma eklendi.', tone: 'ok' },
      {
        text: `Şimdi "Ürün Ekle"den ${s.vaccineName}${s.lotNumber ? ` (lot ${s.lotNumber})` : ''} aşısını stoktan seçin, kontrol edip Onayla'ya basın. Kaydı yakalayıp Vetly'ye işleyeceğiz.`,
      },
    ],
    actions: FALLBACK,
  }),
  done: (s: Submission, queued: boolean): CardView =>
    view(
      s,
      queued
        ? "✓ TARBİL'e kaydedildi. Vetly'ye şu an ulaşılamıyor; bağlantı gelince otomatik işlenecek."
        : "✓ TARBİL'e kaydedildi ve Vetly'de işaretlendi.",
      'ok',
      [],
    ),
  failed: (s: Submission, code: string): CardView =>
    view(s, `Otomatik doldurma durdu (${code}). Kalan adımları TARBİL'de kendiniz tamamlayabilirsiniz.`, 'warn', [
      { id: 'fill', label: 'Yeniden dene' },
      ...FALLBACK,
    ]),
  elsewhere: (s: Submission): CardView =>
    view(s, 'Bu aşıyı "Aşı Uygulama Belgesi Ekle" sayfasında dolduracağız.', 'muted', [{ id: 'open', label: 'Aşı sayfasını aç' }]),
  stock: (text: string, tone?: Tone, actions: CardAction[] = []): CardView => ({ lines: [{ text: 'TARBİL stoğu', tone: 'strong' }, { text, tone }], actions }),
  stockPopup: (text: string, tone?: Tone): CardView => ({ lines: [{ text: 'Vetly', tone: 'strong' }, { text, tone }], actions: [] }),
  popup: (text: string, tone?: Tone): CardView => ({ lines: [{ text, tone }], actions: [] }),
};
