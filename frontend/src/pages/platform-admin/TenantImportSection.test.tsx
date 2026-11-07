// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/dataImportApi', async () => {
  const actual = await vi.importActual<typeof import('../../api/dataImportApi')>('../../api/dataImportApi');
  return {
    ...actual,
    platformAdminImportApi: {
      list: vi.fn(),
      get: vi.fn(),
      preview: vi.fn(),
      discard: vi.fn(),
      commit: vi.fn(),
      rollback: vi.fn(),
      downloadTemplate: vi.fn(),
    },
  };
});

import { ImportBatch, ImportReport, platformAdminImportApi as api } from '../../api/dataImportApi';
import { TenantImportSection } from './TenantImportSection';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const TENANT = 't1';

function report(over: Partial<ImportReport> = {}): ImportReport {
  return {
    totals: {
      OWNER: { rowsInFile: 4, toCreate: 2, matched: 2, alreadyImported: 0, errorRows: 0 },
      PATIENT: { rowsInFile: 4, toCreate: 3, matched: 0, alreadyImported: 0, errorRows: 1 },
    },
    errors: [], warnings: [], ownerMatches: [], newSpecies: [], structuralErrors: [],
    ...over,
  };
}

function batch(over: Partial<ImportBatch> = {}): ImportBatch {
  return {
    id: 'b1', tenantId: TENANT, sourceSystem: 'KolayVet', fileName: 'klinik.xlsx', status: 'PREVIEWED',
    report: report(), totalRows: 7, processedRows: 0, errorMessage: null,
    createdAt: '2026-11-05T10:00:00Z', committedAt: null, rolledBackAt: null, rollbackable: false,
    ...over,
  };
}

let container: HTMLDivElement;
let root: Root;

async function mount() {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  await act(async () => {
    root.render(<TenantImportSection tenantId={TENANT} />);
  });
}

function button(text: string): HTMLButtonElement | undefined {
  return Array.from(container.querySelectorAll('button')).find((b) => b.textContent?.trim() === text);
}

async function click(el: HTMLElement) {
  await act(async () => {
    el.click();
  });
}

beforeEach(() => {
  vi.resetAllMocks();
});

afterEach(async () => {
  await act(async () => root?.unmount());
  container?.remove();
  vi.useRealTimers();
});

describe('TenantImportSection', () => {
  it('disables commit when structural errors exist', async () => {
    vi.mocked(api.list).mockResolvedValue([
      batch({ report: report({ structuralErrors: ['Hasta sayfasında "sahip_kaynak_id" sütunu yok'] }) }),
    ]);
    await mount();

    expect(container.textContent).toContain('Hasta sayfasında "sahip_kaynak_id" sütunu yok');
    expect(button('Aktar')?.disabled).toBe(true);
    expect(button('İptal et')).toBeDefined();
  });

  it('sends unchecked matches as rejectedMatchSourceIds', async () => {
    vi.mocked(api.list).mockResolvedValue([
      batch({
        report: report({
          ownerMatches: [
            { sourceId: 'S1', sourceName: 'Ali', sourcePhone: '555', existingOwnerId: 'o1', existingOwnerName: 'Ali Y.' },
            { sourceId: 'S2', sourceName: 'Veli', sourcePhone: '556', existingOwnerId: 'o2', existingOwnerName: 'Veli K.' },
          ],
        }),
      }),
    ]);
    vi.mocked(api.commit).mockResolvedValue(batch({ status: 'COMMITTING' }));
    vi.mocked(api.get).mockResolvedValue(batch({ status: 'COMMITTING' }));
    await mount();

    const boxes = Array.from(container.querySelectorAll('input[type="checkbox"]')) as HTMLInputElement[];
    expect(boxes).toHaveLength(2);
    expect(boxes.every((b) => b.checked)).toBe(true);
    await click(boxes[1]);
    expect(boxes[1].checked).toBe(false);

    await click(button('Aktar')!);
    expect(container.textContent).toContain(
      '6 kayıt oluşturulacak, 1 kayıt mevcut kayıtlarla eşleşecek, 1 hatalı satır atlanacak.'
    );
    await click(button('Onayla')!);

    expect(api.commit).toHaveBeenCalledWith(TENANT, 'b1', ['S2']);
  });

  it('polls while committing and stops when committed', async () => {
    vi.useFakeTimers();
    vi.mocked(api.list).mockResolvedValue([batch({ status: 'COMMITTING', processedRows: 2 })]);
    vi.mocked(api.get)
      .mockResolvedValueOnce(batch({ status: 'COMMITTING', processedRows: 5 }))
      .mockResolvedValue(batch({ status: 'COMMITTED', processedRows: 7, rollbackable: true }));
    await mount();

    expect(container.querySelector('[role="progressbar"]')).not.toBeNull();
    expect(api.get).not.toHaveBeenCalled();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    expect(api.get).toHaveBeenCalledTimes(1);
    expect(container.querySelector('[role="progressbar"]')).not.toBeNull();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2000);
    });
    expect(api.get).toHaveBeenCalledTimes(2);
    expect(container.querySelector('[role="progressbar"]')).toBeNull();
    expect(button('Geri al')).toBeDefined();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10000);
    });
    expect(api.get).toHaveBeenCalledTimes(2);
  });

  it('shows blockers when rollback is refused', async () => {
    vi.mocked(api.list).mockResolvedValue([batch({ status: 'COMMITTED', processedRows: 7, rollbackable: true })]);
    vi.mocked(api.rollback).mockResolvedValue({
      rolledBack: false,
      blockers: [{ table: 'appointments', rowId: 'row-9', column: 'patient_id' }],
    });
    await mount();

    await click(button('Geri al')!);
    await click(button('Onayla')!);

    expect(api.rollback).toHaveBeenCalledWith(TENANT, 'b1');
    expect(container.textContent).toContain(
      'Bu aktarım geri alınamaz: klinik aktarılan kayıtlara yeni kayıt eklemiş'
    );
    expect(container.textContent).toContain('appointments');
    expect(container.textContent).toContain('row-9');
    expect(container.textContent).toContain('patient_id');
  });

  it('shows "Geri al" only when the server marks the batch rollbackable', async () => {
    vi.mocked(api.list).mockResolvedValue([
      batch({ id: 'f1', status: 'FAILED', errorMessage: 'temizlendi', rollbackable: false }),
    ]);
    await mount();
    expect(container.textContent).toContain('temizlendi');
    expect(button('Geri al')).toBeUndefined();

    await act(async () => root.unmount());
    container.remove();
    vi.mocked(api.list).mockResolvedValue([
      batch({ id: 'f2', status: 'FAILED', errorMessage: 'temizlenemedi', rollbackable: true }),
    ]);
    await mount();
    expect(button('Geri al')).toBeDefined();
  });

  it('lists new breeds with their species', async () => {
    vi.mocked(api.list).mockResolvedValue([
      batch({ report: report({ newBreeds: [{ species: 'Köpek', name: 'Karakaçan' }, { species: 'Tavşan', name: 'Hollanda Lop' }] }) }),
    ]);
    await mount();

    expect(container.textContent).toContain('Eklenecek yeni ırklar');
    expect(container.textContent).toContain('Karakaçan (Köpek), Hollanda Lop (Tavşan)');
  });

  it('explains what is missing instead of silently ignoring the preview click', async () => {
    vi.mocked(api.list).mockResolvedValue([]);
    await mount();

    const preview = button('Önizle')!;
    expect(preview.disabled).toBe(false);
    await click(preview);

    expect(api.preview).not.toHaveBeenCalled();
    expect(container.querySelector('[role="alert"]')?.textContent).toBe(
      'Önizlemek için "Kaynak program" alanını doldurun ve bir .xlsx dosyası seçin.'
    );
  });

  it('suggests previously used source systems in the "Kaynak program" input', async () => {
    vi.mocked(api.list).mockResolvedValue([
      batch({ id: 'a', sourceSystem: 'KolayVet' }),
      batch({ id: 'b', sourceSystem: 'VetPro', status: 'COMMITTED' }),
      batch({ id: 'c', sourceSystem: 'KolayVet', status: 'DISCARDED' }),
    ]);
    await mount();

    const input = Array.from(container.querySelectorAll('input')).find((i) => i.placeholder === 'Örn. KolayVet')!;
    const listId = input.getAttribute('list');
    expect(listId).toBeTruthy();
    const datalist = container.querySelector(`datalist[id="${listId}"]`)!;
    expect(datalist).not.toBeNull();
    const options = Array.from(datalist.querySelectorAll('option')).map((o) => o.getAttribute('value'));
    expect(options).toEqual(['KolayVet', 'VetPro']);
  });
});
