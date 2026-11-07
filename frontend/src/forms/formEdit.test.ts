import { describe, expect, it } from 'vitest';
import { fallbackContent, slugify, uniqueCode, validateContent } from './formEdit';
import type { FormItem, FormTemplateContent } from './formModel';

function item(p: Partial<FormItem>): FormItem {
  return { type: 'SHORT_TEXT', code: null, label: null, text: null, required: false, binding: null, unit: null, options: null, multiple: false, ...p };
}
const sig = item({ type: 'SIGNATURE', code: 'imza', label: 'İmza' });
const field = item({ code: 'ad', label: 'Ad' });
const content = (...sections: FormItem[][]): FormTemplateContent => ({
  title: 'Onam',
  sections: sections.map((items) => ({ title: null, items })),
});

describe('slugify', () => {
  it('Turkce karakterleri sadelestirir', () => {
    expect(slugify('Çiğdem Öğüş İ')).toBe('cigdem_ogus_i');
    expect(slugify('ŞÜĞIı')).toBe('sugii');
  });
  it('40 karaktere keser, sondaki alt cizgiyi atar', () => {
    const s = slugify('a'.repeat(39) + ' b');
    expect(s.length).toBeLessThanOrEqual(40);
    expect(s.endsWith('_')).toBe(false);
    expect(slugify('x'.repeat(60))).toBe('x'.repeat(40));
  });
  it('bos / sembol etiketi icin alan', () => {
    expect(slugify('')).toBe('alan');
    expect(slugify('  !!! ')).toBe('alan');
  });
});

describe('uniqueCode', () => {
  it('cakismada _2, _3 ekler', () => {
    expect(uniqueCode('alan', new Set())).toBe('alan');
    expect(uniqueCode('alan', new Set(['alan']))).toBe('alan_2');
    expect(uniqueCode('alan', new Set(['alan', 'alan_2']))).toBe('alan_3');
  });
  it('40 karakter sinirinda sonek icin yer acar', () => {
    const base = 'x'.repeat(40);
    const first = uniqueCode(base, new Set([base]));
    expect(first).toBe('x'.repeat(38) + '_2');
    const second = uniqueCode(base, new Set([base, first]));
    expect(second).toBe('x'.repeat(38) + '_3');
    expect(second.length).toBe(40);
  });
});

describe('validateContent', () => {
  it('gecerli icerikte hata yok', () => {
    expect(validateContent(content([field, sig]))).toEqual([]);
  });
  it('baslik bos', () => {
    const c = { ...content([field, sig]), title: '  ' };
    expect(validateContent(c).join()).toContain('başlığı boş');
  });
  it('imza yok ve iki imza', () => {
    expect(validateContent(content([field])).join()).toContain('tam olarak bir imza');
    const second = item({ type: 'SIGNATURE', code: 'imza_2', label: 'İmza 2' });
    expect(validateContent(content([field, sig, second])).join()).toContain('tam olarak bir imza');
  });
  it('tekrar eden kod', () => {
    const dup = item({ code: 'ad', label: 'Ad 2' });
    expect(validateContent(content([field, dup, sig])).join()).toContain('"ad" kodu');
  });
  it('CHOICE 2den az secenek (bos satirlar sayilmaz)', () => {
    const ch = item({ type: 'CHOICE', code: 'sec', label: 'Seç', options: ['A', ' '] });
    expect(validateContent(content([ch, sig])).join()).toContain('en az 2');
  });
  it('baska bolumdeki alana yer tutucu', () => {
    const block = item({ type: 'TEXT_BLOCK', text: 'Merhaba {{ad}}' });
    const errs = validateContent(content([block, sig], [field]));
    expect(errs.join()).toContain('{{ad}}');
  });
  it('ayni bolumdeki yer tutucu gecerli', () => {
    const block = item({ type: 'TEXT_BLOCK', text: 'Merhaba {{ad}}' });
    expect(validateContent(content([field, block, sig]))).toEqual([]);
  });
});

describe('fallbackContent', () => {
  it('bos baslik, tek bolum, tek imza', () => {
    const c = fallbackContent();
    expect(c.title).toBe('');
    expect(c.sections).toHaveLength(1);
    expect(c.sections[0].items.map((i) => i.type)).toEqual(['SIGNATURE']);
  });
});
