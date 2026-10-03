import type { Submission } from '../shared/types';
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
    view(s, 'Uygulama tarihini ve türü girip hayvanı çip numarasıyla bulacağız.', 'muted', [{ id: 'fill', label: 'Formu doldur' }, ...FALLBACK]),
  noChip: (s: Submission): CardView =>
    view(s, "Çip numarası yok. Hayvan TARBİL'de kayıtlı değilse önce kimliklendirme gerekir; formu kendiniz doldurun.", 'warn'),
  unsupportedSpecies: (s: Submission): CardView =>
    view(s, `Otomatik doldurma yalnız kedi ve köpek için (${s.speciesName ?? 'tür bilinmiyor'}). Formu kendiniz doldurun.`, 'warn'),
  progress: (s: Submission, text: string): CardView => view(s, text, 'muted'),
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
    view(s, `Forma eklenen hayvanın çipi Vetly'deki çiple (${s.microchipNumber ?? '—'}) aynı değil. Yanlış satırı silip doğru hayvanı ekleyin.`, 'warn'),
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
  popup: (text: string, tone?: Tone): CardView => ({ lines: [{ text, tone }], actions: [] }),
};
