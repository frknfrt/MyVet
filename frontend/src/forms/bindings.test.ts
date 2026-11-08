import { describe, expect, it } from 'vitest';
import type { OwnerProfile, PatientProfile } from '../api/patientApi';
import {
  computeWriteBackDiff,
  fillPlaceholders,
  initialValues,
  matchChoiceOption,
  resolveBindingValues,
} from './bindings';
import type { FormItem, FormTemplateContent } from './formModel';

const patient = {
  id: 'p1', name: 'Pamuk', speciesName: 'Köpek', breedName: 'Golden', sex: 'FEMALE',
  birthDate: null, microchipNumber: null, weightKg: 12.5, color: null,
} as unknown as PatientProfile;
const owner = {
  id: 'o1', fullName: 'Ayşe Yılmaz', phone: '0532 111 22 33', secondaryPhone: null,
  email: null, address: null, birthDate: null, nationalId: null,
} as unknown as OwnerProfile;
const today = new Date(2026, 10, 7, 23, 30);

const ctx = { patient, owner, clinicName: 'Yalıkavak', today };

function item(p: Partial<FormItem>): FormItem {
  return {
    type: 'SHORT_TEXT', code: null, label: null, text: null, required: false,
    binding: null, unit: null, options: null, multiple: false, ...p,
  };
}
const content = (items: FormItem[]): FormTemplateContent => ({ title: 'T', sections: [{ title: null, items }] });

describe('resolveBindingValues', () => {
  it('cinsiyet, klinik, tarih ve kiloyu cozer', () => {
    const r = resolveBindingValues(ctx);
    expect(r['patient.sex']).toBe('Dişi');
    expect(r['clinic.name']).toBe('Yalıkavak');
    expect(r.today).toBe('2026-11-07');
    expect(r['patient.weightKg']).toBe('12.5');
    expect(r['patient.species']).toBe('Köpek');
    expect(r['patient.age']).toBe('');
    expect(r['patient.color']).toBe('');
  });
  it('erkek ve bilinmeyen cinsiyet', () => {
    expect(resolveBindingValues({ ...ctx, patient: { ...patient, sex: 'MALE' } })['patient.sex']).toBe('Erkek');
    expect(resolveBindingValues({ ...ctx, patient: { ...patient, sex: 'UNKNOWN' } })['patient.sex']).toBe('');
  });
  it('yas dogum tarihinden gelir', () => {
    const r = resolveBindingValues({ ...ctx, patient: { ...patient, birthDate: '2000-01-01' } });
    expect(r['patient.age']).toMatch(/yaş/);
  });
});

describe('matchChoiceOption', () => {
  const opts = ['Kedi', 'Köpek', 'Kuş', 'Diğer'];
  it('Turkce buyuk harf duyarsiz', () => {
    expect(matchChoiceOption(opts, 'KÖPEK')).toBe('Köpek');
    expect(matchChoiceOption(opts, ' diğer ')).toBe('Diğer');
  });
  it('eslesme yoksa null', () => expect(matchChoiceOption(opts, 'Tavşan')).toBeNull());
});

describe('initialValues', () => {
  it('bagli alanlari ve CHOICE secimini doldurur', () => {
    const c = content([
      item({ code: 'ad', binding: 'patient.name' }),
      item({ code: 'tur', type: 'CHOICE', binding: 'patient.species', options: ['Kedi', 'Köpek'] }),
      item({ code: 'cins', type: 'CHOICE', binding: 'patient.breed', options: ['Kedi', 'Köpek'] }),
      item({ code: 'not' }),
      item({ type: 'SIGNATURE', code: 'imza' }),
    ]);
    const v = initialValues(c, resolveBindingValues(ctx));
    expect(v).toEqual({ ad: 'Pamuk', tur: 'Köpek', cins: [], not: '' });
  });
});

describe('computeWriteBackDiff', () => {
  const resolved = resolveBindingValues(ctx);
  const c = content([
    item({ code: 'tel', type: 'PHONE', label: 'Telefon', binding: 'owner.phone' }),
    item({ code: 'renk', label: 'Renk', binding: 'patient.color' }),
    item({ code: 'tc', label: 'TC', binding: 'owner.nationalId' }),
  ]);

  it('bicim farki fark sayilmaz', () => {
    expect(computeWriteBackDiff(content([c.sections[0].items[0]]), { tel: '05321112233' }, resolved, 'OWNER')).toEqual([]);
  });
  it('degisen telefon bir satir uretir', () => {
    const rows = computeWriteBackDiff(c, { tel: '0555 000 00 00', renk: '', tc: '' }, resolved, 'OWNER');
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({ binding: 'owner.phone', label: 'Telefon', oldValue: '0532 111 22 33', valid: true });
  });
  it('OTHER imzalayanda owner satiri yok, patient var', () => {
    const rows = computeWriteBackDiff(c, { tel: '0555 000 00 00', renk: 'Sarı', tc: '' }, resolved, 'OTHER');
    expect(rows.map((r) => r.binding)).toEqual(['patient.color']);
  });
  it('gecersiz TC valid:false', () => {
    const rows = computeWriteBackDiff(c, { tel: '', renk: '', tc: '12345678901' }, resolved, 'OWNER');
    expect(rows[0]).toMatchObject({ binding: 'owner.nationalId', valid: false, reason: 'Geçersiz TC kimlik no — kayda yazılamaz' });
  });
  it('gecerli TC valid:true', () => {
    const rows = computeWriteBackDiff(c, { tel: '', renk: '', tc: '10000000146' }, resolved, 'OWNER');
    expect(rows[0].valid).toBe(true);
  });
});

describe('fillPlaceholders', () => {
  it('kodlari doldurur', () => {
    expect(fillPlaceholders('…{{klinik}} muayenehanesi', { klinik: 'Yalıkavak' })).toBe('…Yalıkavak muayenehanesi');
  });
  it('eksik kod bos kalir', () => expect(fillPlaceholders('a {{x}} b', {})).toBe('a  b'));
});

describe('computeWriteBackDiff dogrulamalari', () => {
  const resolved = resolveBindingValues(ctx);
  const one = (b: FormItem['binding'], v: string, type: FormItem['type'] = 'SHORT_TEXT') =>
    computeWriteBackDiff(
      content([item({ code: 'x', type, binding: b, options: type === 'CHOICE' ? ['a', 'b'] : null })]),
      { x: v },
      resolved,
      'OWNER',
    );

  it('CHOICE alani asla geri yazilmaz', () => {
    expect(one('patient.color', 'Sarı', 'CHOICE')).toEqual([]);
    expect(one('patient.color', ['a', 'b'] as unknown as string, 'CHOICE')).toEqual([]);
  });
  it('dogum tarihi', () => {
    expect(one('owner.birthDate', '1990-05-17')[0].valid).toBe(true);
    expect(one('owner.birthDate', '17.05.1990')[0]).toMatchObject({ valid: false, reason: 'Geçersiz doğum tarihi — kayda yazılamaz' });
    expect(one('owner.birthDate', '2026-02-30')[0].valid).toBe(false);
    expect(one('owner.birthDate', '2027-01-01')[0].valid).toBe(false);
  });
  it('kilo', () => {
    expect(one('patient.weightKg', '4,5')[0].valid).toBe(true);
    expect(one('patient.weightKg', '9999.99')[0].valid).toBe(true);
    expect(one('patient.weightKg', '0.005')[0].valid).toBe(true);
    expect(one('patient.weightKg', '4,555')[0].valid).toBe(true);
    for (const bad of ['abc', '0', '-3', '10000', '9999.995', '0.001', '999999999999999999999']) {
      expect(one('patient.weightKg', bad)[0]).toMatchObject({ valid: false, reason: 'Geçersiz kilo — kayda yazılamaz' });
    }
  });
  it('e-posta', () => {
    expect(one('owner.email', 'a@b.com')[0].valid).toBe(true);
    expect(one('owner.email', 'abc')[0]).toMatchObject({ valid: false, reason: 'Geçersiz e-posta — kayda yazılamaz' });
  });
});

