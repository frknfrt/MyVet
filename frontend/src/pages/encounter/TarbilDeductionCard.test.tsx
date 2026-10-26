// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/tarbilApi', () => ({ tarbilApi: { getEncounterDeduction: vi.fn() } }));
vi.mock('../../api/inventoryApi', () => ({ inventoryApi: {} }));
vi.mock('./TarbilDiseaseSelect', () => ({ TarbilDiseaseSelect: () => null }));

import { tarbilApi } from '../../api/tarbilApi';
import { TarbilDeductionCard } from './TarbilDeductionCard';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
afterEach(() => container?.remove());

describe('TarbilDeductionCard (spec 2026-10-25)', () => {
  it('shows unit', async () => {
    vi.mocked(tarbilApi.getEncounterDeduction).mockResolvedValue({
      submissionId: null, submissionStatus: null,
      lines: [
        { itemName: 'Clavon', lotNumber: '63016', quantity: 1.5, status: 'PENDING', settledQuantity: null, unit: 'ML' },
        { itemName: 'Baytril', lotNumber: 'B1', quantity: 2, status: 'PENDING', settledQuantity: null, unit: 'TABLET' },
        { itemName: 'Tasma', lotNumber: null, quantity: 1, status: 'PENDING', settledQuantity: null, unit: 'ADET' },
      ],
    });
    container = document.createElement('div');
    document.body.appendChild(container);
    await act(async () => {
      createRoot(container).render(<TarbilDeductionCard encounterId="e1" canAct={false} />);
    });
    await act(async () => {});

    expect(container.textContent).toContain('1,5 ml');
    expect(container.textContent).toContain('2 tablet');
    expect(container.textContent).toContain('1 adet');
    expect(container.textContent).not.toContain('1,5 adet');
  });
});
