// Dijital onay formu icerik modeli -- backend record'lariyla birebir (bkz. shared-model.md).

export type FormItemType =
  | 'TEXT_BLOCK'
  | 'SHORT_TEXT'
  | 'LONG_TEXT'
  | 'DATE'
  | 'PHONE'
  | 'NUMBER'
  | 'CHOICE'
  | 'SIGNATURE';

export const BINDINGS = [
  'owner.fullName',
  'owner.phone',
  'owner.secondaryPhone',
  'owner.nationalId',
  'owner.birthDate',
  'owner.address',
  'owner.email',
  'patient.name',
  'patient.species',
  'patient.breed',
  'patient.sex',
  'patient.age',
  'patient.color',
  'patient.weightKg',
  'patient.microchipNumber',
  'clinic.name',
  'today',
] as const;

export type FormBinding = (typeof BINDINGS)[number];

/** Imzadan sonra kayda geri yazilabilen baglantilar. */
export const WRITABLE_BINDINGS = [
  'owner.phone',
  'owner.secondaryPhone',
  'owner.nationalId',
  'owner.birthDate',
  'owner.address',
  'owner.email',
  'patient.color',
  'patient.weightKg',
  'patient.microchipNumber',
] as const satisfies readonly FormBinding[];

export type WritableBinding = (typeof WRITABLE_BINDINGS)[number];

export const BINDING_LABELS: Record<FormBinding, string> = {
  'owner.fullName': 'Sahip adı soyadı',
  'owner.phone': 'Sahip telefonu',
  'owner.secondaryPhone': 'Sahip ikinci telefonu',
  'owner.nationalId': 'Sahip TC kimlik no',
  'owner.birthDate': 'Sahip doğum tarihi',
  'owner.address': 'Sahip adresi',
  'owner.email': 'Sahip e-postası',
  'patient.name': 'Hasta adı',
  'patient.species': 'Hasta türü',
  'patient.breed': 'Hasta ırkı',
  'patient.sex': 'Hasta cinsiyeti',
  'patient.age': 'Hasta yaşı',
  'patient.color': 'Hasta rengi',
  'patient.weightKg': 'Hasta kilosu',
  'patient.microchipNumber': 'Mikroçip numarası',
  'clinic.name': 'Klinik adı',
  today: 'Bugünün tarihi',
};

export interface FormItem {
  type: FormItemType;
  code: string | null;
  label: string | null;
  text: string | null;
  required: boolean;
  binding: FormBinding | null;
  unit: string | null;
  options: string[] | null;
  multiple: boolean;
}

export interface FormSection {
  title: string | null;
  items: FormItem[];
}

export interface PrivacyNoticeAcknowledgement {
  version: number; text: string; hash: string; statement: string;
  sms: boolean; whatsapp: boolean; email: boolean;
}

export interface FormTemplateContent {
  privacyOptionsEnabled?: boolean;
  privacyNotice?: PrivacyNoticeAcknowledgement | null;
  title: string;
  sections: FormSection[];
}

export type FormValue = string | string[];
export type FormValues = Record<string, FormValue>;

export function isFormBinding(value: string | null): value is FormBinding {
  return value !== null && (BINDINGS as readonly string[]).includes(value);
}

/** Backend FormBinding.supports ile aynı tür sözleşmesi. */
export function supportsBinding(type: FormItemType, binding: FormBinding): boolean {
  switch (binding) {
    case 'owner.birthDate': case 'today': return type === 'DATE';
    case 'patient.weightKg': return type === 'NUMBER';
    case 'owner.phone': case 'owner.secondaryPhone':
      return type === 'PHONE' || type === 'SHORT_TEXT' || type === 'LONG_TEXT';
    case 'patient.species': case 'patient.breed': case 'patient.sex': case 'patient.color':
      return type === 'CHOICE' || type === 'SHORT_TEXT' || type === 'LONG_TEXT';
    default: return type === 'SHORT_TEXT' || type === 'LONG_TEXT';
  }
}

export function isWritableBinding(value: string | null): value is WritableBinding {
  return value !== null && (WRITABLE_BINDINGS as readonly string[]).includes(value);
}
