import type { OwnerProfile, PatientProfile } from '../api/patientApi';
import { ageLabelFrom } from '../pages/encounter/ageUtils';
import { isValidTcKimlik } from '../pages/owners/tcKimlik';
import {
  isFormBinding,
  isWritableBinding,
  BINDING_LABELS,
  type FormBinding,
  type FormItem,
  type FormTemplateContent,
  type FormValue,
  type FormValues,
  type WritableBinding,
} from './formModel';

export interface BindingContext {
  patient: PatientProfile;
  owner: OwnerProfile;
  clinicName: string;
  today: Date;
}

export type SignerType = 'OWNER' | 'OTHER';

export interface WriteBackRow {
  binding: WritableBinding;
  label: string;
  oldValue: string;
  newValue: string;
  valid: boolean;
  reason?: string;
}

const INVALID_TC_REASON = 'Geçersiz TC kimlik no — kayda yazılamaz';

/** Yerel tarihi (UTC degil) YYYY-MM-DD yapar. */
export function toLocalIsoDate(d: Date): string {
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

function sexLabel(sex: PatientProfile['sex']): string {
  if (sex === 'FEMALE') return 'Dişi';
  if (sex === 'MALE') return 'Erkek';
  return '';
}

export function resolveBindingValues(ctx: BindingContext): Record<FormBinding, string> {
  const { patient, owner, clinicName, today } = ctx;
  return {
    'owner.fullName': owner.fullName ?? '',
    'owner.phone': owner.phone ?? '',
    'owner.secondaryPhone': owner.secondaryPhone ?? '',
    'owner.nationalId': owner.nationalId ?? '',
    'owner.birthDate': owner.birthDate ?? '',
    'owner.address': owner.address ?? '',
    'owner.email': owner.email ?? '',
    'patient.name': patient.name ?? '',
    'patient.species': patient.speciesName ?? '',
    'patient.breed': patient.breedName ?? '',
    'patient.sex': sexLabel(patient.sex),
    'patient.age': ageLabelFrom(patient.birthDate) ?? '',
    'patient.color': patient.color ?? '',
    'patient.weightKg': patient.weightKg === null || patient.weightKg === undefined ? '' : String(patient.weightKg),
    'patient.microchipNumber': patient.microchipNumber ?? '',
    'clinic.name': clinicName ?? '',
    today: toLocalIsoDate(today),
  };
}

function norm(s: string): string {
  return s.trim().toLocaleLowerCase('tr');
}

/** Turkce buyuk/kucuk harf duyarsiz tam eslesme; bulunursa seceneğin kendi yazimini doner. */
export function matchChoiceOption(options: string[], value: string): string | null {
  const target = norm(value);
  if (!target) return null;
  return options.find((o) => norm(o) === target) ?? null;
}

function* allItems(content: FormTemplateContent): Generator<FormItem> {
  for (const section of content.sections) {
    for (const item of section.items) yield item;
  }
}

/**
 * Alan baslangic degerleri. CHOICE: tek secimde eslesen -> string, eslesme yoksa -> [],
 * coklu secimde -> string[]. Baglantisiz metin alani -> ''.
 */
export function initialValues(
  content: FormTemplateContent,
  resolved: Record<FormBinding, string>,
): FormValues {
  const values: FormValues = {};
  for (const item of allItems(content)) {
    if (!item.code || item.type === 'TEXT_BLOCK' || item.type === 'SIGNATURE') continue;
    const bound = item.binding && isFormBinding(item.binding) ? resolved[item.binding] : '';
    if (item.type === 'CHOICE') {
      const matched = bound ? matchChoiceOption(item.options ?? [], bound) : null;
      if (matched === null) values[item.code] = [];
      else values[item.code] = item.multiple ? [matched] : matched;
    } else {
      values[item.code] = bound;
    }
  }
  return values;
}

function valueAsText(v: FormValue | undefined): string {
  if (v === undefined) return '';
  return Array.isArray(v) ? v.join(', ') : v;
}

function comparable(binding: WritableBinding, v: string): string {
  const t = v.trim();
  return binding === 'owner.phone' || binding === 'owner.secondaryPhone' ? t.replace(/\D/g, '') : t;
}

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

function isRealIsoDate(v: string): boolean {
  const m = ISO_DATE.exec(v);
  if (!m) return false;
  const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
  return d.getFullYear() === Number(m[1]) && d.getMonth() === Number(m[2]) - 1 && d.getDate() === Number(m[3]);
}

/** Backend dogrulamasinin istemci aynasi; gecersizse neden metnini doner. */
function invalidReason(binding: WritableBinding, value: string, todayIso: string): string | null {
  switch (binding) {
    case 'owner.nationalId':
      return isValidTcKimlik(value) ? null : INVALID_TC_REASON;
    case 'owner.birthDate':
      return isRealIsoDate(value) && value <= todayIso ? null : 'Geçersiz doğum tarihi — kayda yazılamaz';
    case 'patient.weightKg': {
      const n = Number(value.replace(',', '.'));
      return /^\d+([.,]\d+)?$/.test(value) && n >= 0.005 && n <= 9999.99 ? null : 'Geçersiz kilo — kayda yazılamaz';
    }
    case 'owner.email':
      return /^[^\s@]+@[^\s@]+$/.test(value) ? null : 'Geçersiz e-posta — kayda yazılamaz';
    default:
      return null;
  }
}

export function computeWriteBackDiff(
  content: FormTemplateContent,
  values: FormValues,
  resolved: Record<FormBinding, string>,
  signerType: SignerType,
): WriteBackRow[] {
  const rows: WriteBackRow[] = [];
  for (const item of allItems(content)) {
    // CHOICE alanlari asla geri yazilmaz
    if (!item.code || item.type === 'CHOICE' || !isWritableBinding(item.binding)) continue;
    const binding = item.binding;
    if (signerType === 'OTHER' && binding.startsWith('owner.')) continue;

    const newValue = valueAsText(values[item.code]).trim();
    if (!newValue) continue;
    const oldValue = resolved[binding] ?? '';
    if (comparable(binding, newValue) === comparable(binding, oldValue)) continue;

    const row: WriteBackRow = {
      binding,
      label: item.label ?? BINDING_LABELS[binding],
      oldValue,
      newValue,
      valid: true,
    };
    const reason = invalidReason(binding, newValue, resolved.today);
    if (reason) {
      row.valid = false;
      row.reason = reason;
    }
    rows.push(row);
  }
  return rows;
}

/** TEXT_BLOCK metnindeki {{kod}} yer tutucularini alan degerleriyle doldurur; bilinmeyen/bos kod bos kalir. */
export function fillPlaceholders(text: string, values: Record<string, FormValue | undefined>): string {
  return text.replace(/\{\{\s*([a-z0-9_]+)\s*\}\}/g, (_m, code: string) => valueAsText(values[code]));
}
