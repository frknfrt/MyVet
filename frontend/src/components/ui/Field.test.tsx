// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it } from 'vitest';
import { AI_FIELD_HINT, FieldWrap } from './Field';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
afterEach(() => container?.remove());

async function render(node: React.ReactNode) {
  container = document.createElement('div');
  document.body.appendChild(container);
  await act(async () => { createRoot(container).render(node); });
}

describe('FieldWrap', () => {
  it('shows a hint under the field when given', async () => {
    await render(<FieldWrap label="Kritik Uyarı" hint={AI_FIELD_HINT}><textarea /></FieldWrap>);
    expect(container.textContent).toContain('Bu alan yapay zekâ önerisine gönderilir');
  });

  it('shows no hint by default', async () => {
    await render(<FieldWrap label="Ad"><input /></FieldWrap>);
    expect(container.textContent).toBe('Ad');
  });
});
