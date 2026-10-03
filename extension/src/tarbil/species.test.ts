import { describe, expect, it } from 'vitest';
import { ANIMAL_TYPE } from './selectors';
import { resolveAnimalType } from './species';

describe('resolveAnimalType', () => {
  it('maps Vetly cat and dog names to TARBIL values', () => {
    expect(resolveAnimalType({ speciesName: 'Kedi', speciesMapping: null })).toBe(ANIMAL_TYPE.CAT);
    expect(resolveAnimalType({ speciesName: 'Kopek', speciesMapping: null })).toBe(ANIMAL_TYPE.DOG);
    expect(resolveAnimalType({ speciesName: ' KÖPEK ', speciesMapping: null })).toBe(ANIMAL_TYPE.DOG);
  });

  it('returns null for unsupported or missing species', () => {
    expect(resolveAnimalType({ speciesName: 'Tavşan', speciesMapping: null })).toBeNull();
    expect(resolveAnimalType({ speciesName: null, speciesMapping: null })).toBeNull();
  });

  it('prefers a learned species mapping', () => {
    expect(resolveAnimalType({ speciesName: 'Kedi', speciesMapping: { animalType: { value: 'guid-x', text: 'Kedi' } } })).toBe('guid-x');
  });
});
