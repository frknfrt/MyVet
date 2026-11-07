// Sablon duzenleyicisinin saf yardimcilari: kod uretimi, bos oge, istemci dogrulamasi.
import type { FormItem, FormItemType, FormTemplateContent } from './formModel';

const TR_MAP: Record<string, string> = {
  ç: 'c', Ç: 'c', ğ: 'g', Ğ: 'g', ı: 'i', İ: 'i', ö: 'o', Ö: 'o', ş: 's', Ş: 's', ü: 'u', Ü: 'u',
};

const MAX_CODE = 40;

/** Etiketten [a-z0-9_] kod uretir; bossa 'alan'. */
export function slugify(label: string): string {
  const ascii = label.replace(/[çÇğĞıİöÖşŞüÜ]/g, (c) => TR_MAP[c] ?? c).toLowerCase();
  const slug = ascii
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, MAX_CODE)
    .replace(/_+$/g, '');
  return slug || 'alan';
}

/** Cakisan kodlara _2, _3 ... ekler (40 karakteri asmadan). */
export function uniqueCode(base: string, taken: ReadonlySet<string>): string {
  if (!taken.has(base)) return base;
  for (let n = 2; ; n++) {
    const suffix = `_${n}`;
    const candidate = base.slice(0, MAX_CODE - suffix.length) + suffix;
    if (!taken.has(candidate)) return candidate;
  }
}

export function allCodes(content: FormTemplateContent, except?: FormItem): Set<string> {
  const codes = new Set<string>();
  for (const s of content.sections) {
    for (const it of s.items) if (it !== except && it.code) codes.add(it.code);
  }
  return codes;
}

export function hasSignature(content: FormTemplateContent): boolean {
  return content.sections.some((s) => s.items.some((i) => i.type === 'SIGNATURE'));
}

export const FILLABLE_TYPES: FormItemType[] = ['SHORT_TEXT', 'LONG_TEXT', 'DATE', 'PHONE', 'NUMBER', 'CHOICE'];

export function isFillable(type: FormItemType): boolean {
  return FILLABLE_TYPES.includes(type);
}

export const TYPE_LABELS: Record<FormItemType, string> = {
  SHORT_TEXT: 'Kısa metin',
  LONG_TEXT: 'Uzun metin',
  DATE: 'Tarih',
  PHONE: 'Telefon',
  NUMBER: 'Sayı',
  CHOICE: 'Seçenekler',
  TEXT_BLOCK: 'Paragraf',
  SIGNATURE: 'İmza alanı',
};

/** Yeni oge; kodu icerigin mevcut kodlarina gore benzersiz uretilir. */
export function newItem(type: FormItemType, content: FormTemplateContent): FormItem {
  const base: FormItem = {
    type, code: null, label: null, text: null, required: false, binding: null, unit: null, options: null, multiple: false,
  };
  if (type === 'TEXT_BLOCK') return { ...base, text: '' };
  const label = type === 'SIGNATURE' ? 'İmza' : '';
  const code = uniqueCode(slugify(label), allCodes(content));
  return { ...base, code, label, options: type === 'CHOICE' ? ['Evet', 'Hayır'] : null };
}

/** Kaydetmeden once bos satir/bosluklari temizler. */
export function cleanContent(content: FormTemplateContent): FormTemplateContent {
  return {
    title: content.title.trim(),
    sections: content.sections.map((s) => ({
      title: s.title?.trim() ? s.title.trim() : null,
      items: s.items.map((i) => ({
        ...i,
        label: i.label === null ? null : i.label.trim(),
        text: i.text === null ? null : i.text.trim(),
        unit: i.type === 'NUMBER' && i.unit?.trim() ? i.unit.trim() : null,
        options: i.type === 'CHOICE' ? (i.options ?? []).map((o) => o.trim()).filter(Boolean) : null,
      })),
    })),
  };
}

const PLACEHOLDER = /\{\{\s*([a-z0-9_]+)\s*\}\}/g;

/** Backend kurallarinin istemci aynasi; hata mesajlari listesi (bossa gecerli). */
export function validateContent(raw: FormTemplateContent): string[] {
  const content = cleanContent(raw);
  const errors: string[] = [];
  if (!content.title) errors.push('Form başlığı boş olamaz.');

  const seen = new Set<string>();
  let signatures = 0;
  content.sections.forEach((section, si) => {
    const where = section.title ? `"${section.title}" bölümü` : `${si + 1}. bölüm`;
    const sectionCodes = new Set(section.items.filter((i) => i.type !== 'TEXT_BLOCK' && i.code).map((i) => i.code as string));
    section.items.forEach((i, ii) => {
      const name = `${where}, ${ii + 1}. öğe`;
      if (i.type === 'SIGNATURE') signatures++;
      if (i.type === 'TEXT_BLOCK') {
        if (!i.text) errors.push(`${name}: paragraf metni boş olamaz.`);
        for (const m of (i.text ?? '').matchAll(PLACEHOLDER)) {
          if (!sectionCodes.has(m[1])) errors.push(`${name}: {{${m[1]}}} aynı bölümdeki bir alanın kodu olmalı.`);
        }
        return;
      }
      if (!i.label) errors.push(`${name}: alan adı boş olamaz.`);
      if (!i.code) errors.push(`${name}: alan kodu eksik.`);
      else if (seen.has(i.code)) errors.push(`${name}: "${i.code}" kodu başka bir alanda da var.`);
      else seen.add(i.code);
      if (i.type === 'CHOICE' && (i.options?.length ?? 0) < 2) errors.push(`${name}: seçenekler en az 2 olmalı.`);
    });
  });
  if (signatures !== 1) errors.push('Formda tam olarak bir imza alanı olmalı.');
  return errors;
}

/** AI taslagi yoksa/bagli degilse: bos baslik, tek bolum, tek imza. */
export function fallbackContent(): FormTemplateContent {
  const empty: FormTemplateContent = { title: '', sections: [{ title: null, items: [] }] };
  empty.sections[0].items.push(newItem('SIGNATURE', empty));
  return empty;
}
