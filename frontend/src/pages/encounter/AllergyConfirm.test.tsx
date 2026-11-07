// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/clinicalApi', () => ({ clinicalApi: { checkAllergies: vi.fn() } }));

import { AllergyAck, clinicalApi } from '../../api/clinicalApi';
import { useAllergyConfirm } from './AllergyConfirm';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const ALERT = 'Penisilin grubu antibiyotiklere karşı alerjisi var.';
let container: HTMLDivElement;
let api: ReturnType<typeof useAllergyConfirm>;
afterEach(() => container?.remove());

function Harness() {
  api = useAllergyConfirm('p1');
  return <>{api.modal}</>;
}

async function mount() {
  container = document.createElement('div');
  document.body.appendChild(container);
  await act(async () => {
    createRoot(container).render(<Harness />);
  });
}

function button(text: string) {
  return [...document.querySelectorAll('button')].find((b) => b.textContent?.includes(text)) as HTMLButtonElement;
}

async function startConfirm() {
  let result: AllergyAck | null | undefined;
  await act(async () => {
    api.confirm(['d1'], []).then((r) => (result = r));
  });
  return () => result;
}

describe('useAllergyConfirm', () => {
  it('passes silently when the patient has no alert', async () => {
    vi.mocked(clinicalApi.checkAllergies).mockResolvedValue({ criticalAlert: null, matches: [] });
    await mount();
    const result = await startConfirm();
    expect(result()).toEqual({});
    expect(document.body.textContent).not.toContain('uyarı');
  });

  it('asks for acknowledgement when an alert exists but nothing matches', async () => {
    vi.mocked(clinicalApi.checkAllergies).mockResolvedValue({ criticalAlert: ALERT, matches: [] });
    await mount();
    const result = await startConfirm();
    expect(document.body.textContent).toContain(ALERT);
    await act(async () => button('Kontrol ettim, ekle').click());
    expect(result()).toEqual({ allergyAcknowledged: true, allergyOverrideReason: null });
  });

  it('requires a reason for a matching drug and returns it', async () => {
    vi.mocked(clinicalApi.checkAllergies).mockResolvedValue({
      criticalAlert: ALERT,
      matches: [{ drugName: 'Amoksisilin', reason: 'Penisilin grubu' }],
    });
    await mount();
    const result = await startConfirm();
    expect(document.body.textContent).toContain('Penisilin grubu');
    expect(button('Gerekçeyle ekle').disabled).toBe(true);

    const textarea = document.querySelector('textarea')!;
    Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')!.set!.call(textarea, 'Kayıt hatalı, doğrulandı');
    await act(async () => {
      textarea.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await act(async () => button('Gerekçeyle ekle').click());
    expect(result()).toEqual({ allergyAcknowledged: true, allergyOverrideReason: 'Kayıt hatalı, doğrulandı' });
  });

  it('returns null when the user cancels', async () => {
    vi.mocked(clinicalApi.checkAllergies).mockResolvedValue({ criticalAlert: ALERT, matches: [] });
    await mount();
    const result = await startConfirm();
    await act(async () => button('Vazgeç').click());
    expect(result()).toBeNull();
  });
});
