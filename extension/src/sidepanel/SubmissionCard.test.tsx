// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it } from 'vitest';
import type { Submission } from '../shared/types';
import { SubmissionCard } from './SubmissionCard';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const base: Submission = {
  id: 's1', vaccinationRecordId: 'v1', status: 'PENDING', patientName: 'Pamuk', microchipNumber: '900', passportNumber: null,
  speciesId: null, speciesName: 'Kedi', breedName: null, sex: 'FEMALE', birthDate: null,
  vaccineName: 'Kuduz', lotNumber: 'L1', tarbilProductName: null, administeredDate: '2026-10-09',
  submittedAt: null, confirmationMethod: null, tarbilReference: null, vaccineKey: 'kuduz', vaccineMapping: null, speciesMapping: null,
  documentType: 'VACCINATION',
};

let container: HTMLDivElement;

function render(submission: Submission) {
  container = document.createElement('div');
  document.body.appendChild(container);
  act(() => createRoot(container).render(<SubmissionCard submission={submission} onDone={() => undefined} />));
  const buttons = Array.from(container.querySelectorAll("button")).map((b) => b.textContent ?? '');
  return { text: container.textContent ?? '', buttons };
}

afterEach(() => container?.remove());

describe('SubmissionCard', () => {
  it('shows a stock prescription with its lines and lets the extension fill it', () => {
    const { text, buttons } = render({
      ...base, documentType: 'PRESCRIPTION', vaccineName: 'Reçete', lotNumber: null, administeredDate: null, vaccineKey: '',
      prescription: {
        sourceKind: 'ENCOUNTER', tarbilDiseaseId: 'd1', tarbilDiseaseName: 'Pnömoni', treatmentStartDate: '2026-10-09',
        lines: [{ itemName: 'Synulox', tarbilProductName: 'SYNULOX', lotNumber: 'S1', quantity: 2 }],
      },
    });

    expect(text).toContain('Reçete — stoktan düşüm');
    expect(text).toContain('Pnömoni');
    expect(text).toContain('SYNULOX · Seri S1 · 2 adet');
    expect(text).not.toContain('yakında');
    expect(buttons).toContain("TARBİL'de doldur");
    expect(buttons).toContain('Gönderildi olarak işaretle');
  });

  it('keeps the vaccine card unchanged', () => {
    const { text, buttons } = render(base);

    expect(text).toContain('Kuduz · Lot L1');
    expect(buttons).toContain("TARBİL'de doldur");
  });
});
