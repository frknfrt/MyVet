import { ExamBodySystem } from '../../api/encounterApi';

export const EXAM_BODY_SYSTEMS: ExamBodySystem[] = [
  'GENERAL_APPEARANCE',
  'SKIN_COAT',
  'EYES_EARS_MOUTH',
  'CARDIOVASCULAR',
  'RESPIRATORY',
  'GASTROINTESTINAL',
  'UROGENITAL',
  'MUSCULOSKELETAL',
  'NEUROLOGICAL',
  'LYMPH_NODES',
];

export const EXAM_BODY_SYSTEM_LABELS: Record<ExamBodySystem, string> = {
  GENERAL_APPEARANCE: 'Genel Görünüm / Davranış',
  SKIN_COAT: 'Deri ve Kürk',
  EYES_EARS_MOUTH: 'Göz, Kulak, Burun, Ağız',
  CARDIOVASCULAR: 'Kardiyovasküler',
  RESPIRATORY: 'Solunum',
  GASTROINTESTINAL: 'Gastrointestinal',
  UROGENITAL: 'Ürogenital',
  MUSCULOSKELETAL: 'Kas-İskelet Sistemi',
  NEUROLOGICAL: 'Nörolojik',
  LYMPH_NODES: 'Lenf Nodları',
};
