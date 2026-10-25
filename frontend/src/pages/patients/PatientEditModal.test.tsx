// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/patientApi', () => ({
  patientApi: {
    listSpecies: vi.fn().mockResolvedValue([]),
    listBreeds: vi.fn().mockResolvedValue([]),
    update: vi.fn().mockResolvedValue(undefined),
    updateIdentification: vi.fn().mockResolvedValue(undefined),
  },
}));

import { patientApi, PatientProfile } from '../../api/patientApi';
import { PatientEditModal } from './PatientEditModal';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const PROFILE: PatientProfile = {
  id: 'p1', name: 'Pamuk', speciesName: null, breedName: null, sex: 'FEMALE', neutered: false, birthDate: null,
  microchipNumber: null, passportNumber: null, weightKg: null, photoUrl: null, color: null, temperament: null,
  distinguishingMarks: null, aggressive: false, bloodType: null, foodBrand: null, criticalAlert: null, notes: null,
  protocolNumber: null, rabiesTag: null, status: 'ACTIVE', ownerId: 'o1',
  ownerFullName: 'Ayşe Yılmaz', ownerPhone: '5550000000',
};

let container: HTMLDivElement;
const onSaved = vi.fn();

async function render() {
  container = document.createElement('div');
  document.body.appendChild(container);
  await act(async () => {
    createRoot(container).render(<PatientEditModal open profile={PROFILE} onClose={() => undefined} onSaved={onSaved} />);
  });
  const name = container.querySelector<HTMLInputElement>('input[required]')!;
  return { name };
}

/** React'in kontrollu input'una deger yazar (degeri dogrudan atamak onChange tetiklemez). */
function typeInto(input: HTMLInputElement, value: string) {
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
  setter.call(input, value);
  input.dispatchEvent(new Event('input', { bubbles: true }));
}

async function submit() {
  await act(async () => {
    container.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  });
}

beforeEach(() => vi.clearAllMocks());
afterEach(() => container?.remove());

describe('PatientEditModal', () => {
  it('warns that the critical alert is sent to the AI suggestion', async () => {
    await render();
    expect(document.body.textContent).toContain('Bu alan yapay zekâ önerisine gönderilir');
  });

  it('shows the current name and saves the edited one, trimmed', async () => {
    const { name } = await render();
    expect(name.value).toBe('Pamuk');

    await act(async () => typeInto(name, '  Pamuk Kar  '));
    await submit();

    expect(patientApi.update).toHaveBeenCalledWith('p1', expect.objectContaining({ name: 'Pamuk Kar' }));
    expect(onSaved).toHaveBeenCalled();
  });

  it('does not save an empty name', async () => {
    const { name } = await render();

    await act(async () => typeInto(name, '   '));
    await submit();

    expect(patientApi.update).not.toHaveBeenCalled();
    expect(container.textContent).toContain('Hasta adı boş olamaz.');
  });
});
