import type { Submission } from '../../shared/types';
import { ANIMAL_TYPE } from '../selectors';

/** Vetly turu -> TARBIL cbxAnimalType degeri. Ogrenilmis eslestirme varsa o kazanir; yoksa yalniz kedi/kopek. */
export function resolveAnimalType(s: Pick<Submission, 'speciesName' | 'speciesMapping'>): string | null {
  const mapped = (s.speciesMapping as { animalType?: { value?: unknown } } | null)?.animalType?.value;
  if (typeof mapped === 'string' && mapped.length > 0) return mapped;
  const name = (s.speciesName ?? '').trim().toLocaleLowerCase('tr-TR').replace(/ö/g, 'o');
  if (name === 'kedi') return ANIMAL_TYPE.CAT;
  if (name === 'kopek') return ANIMAL_TYPE.DOG;
  return null;
}
