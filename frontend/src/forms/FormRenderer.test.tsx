// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { FormRenderer } from './FormRenderer';
import type { FormItem, FormTemplateContent, FormValues } from './formModel';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

function item(p: Partial<FormItem>): FormItem {
  return { type: 'SHORT_TEXT', code: null, label: null, text: null, required: false, binding: null, unit: null, options: null, multiple: false, ...p };
}

const content: FormTemplateContent = {
  title: 'Anestezi Onayı',
  sections: [
    {
      title: 'Bilgiler',
      items: [
        item({ type: 'SHORT_TEXT', code: 'ad', label: 'Ad' }),
        item({ type: 'TEXT_BLOCK', text: 'Ben {{ad}} olarak onaylıyorum. Not: {{not_}}' }),
        item({ type: 'CHOICE', code: 'tek', label: 'Tek', options: ['A', 'B'] }),
        item({ type: 'CHOICE', code: 'cok', label: 'Çok', options: ['X', 'Y'], multiple: true }),
        item({ type: 'NUMBER', code: 'kilo', label: 'Kilo', unit: 'kg' }),
        item({ type: 'SIGNATURE', code: 'imza', label: 'İmza' }),
      ],
    },
  ],
};

let container: HTMLDivElement;
let root: Root;

async function render(el: React.ReactElement) {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  await act(async () => root.render(el));
}
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe('FormRenderer', () => {
  it('fill modunda yer tutucuyu doldurur, bos olani altı çizili boşluk yapar', async () => {
    const values: FormValues = { ad: 'Ali', tek: '', cok: [], kilo: '' };
    await render(<FormRenderer content={content} values={values} mode="fill" onChange={() => {}} />);
    const p = container.querySelector('p')!;
    expect(p.textContent).toContain('Ben Ali olarak');
    expect(p.querySelectorAll('[data-blank]').length).toBe(1);
  });

  it('tek secimde string, cok secimde dizi ile onChange cagirir', async () => {
    const onChange = vi.fn();
    await render(<FormRenderer content={content} values={{ ad: '', tek: '', cok: ['X'], kilo: '' }} mode="fill" onChange={onChange} />);
    const radios = container.querySelectorAll<HTMLInputElement>('input[type=radio]');
    await act(async () => radios[1].click());
    expect(onChange).toHaveBeenCalledWith('tek', 'B');
    const boxes = container.querySelectorAll<HTMLInputElement>('input[type=checkbox]');
    await act(async () => boxes[1].click());
    expect(onChange).toHaveBeenCalledWith('cok', ['X', 'Y']);
    await act(async () => boxes[0].click());
    expect(onChange).toHaveBeenCalledWith('cok', []);
  });

  it('readonly modunda input yok, deger metin olarak gorunur', async () => {
    await render(<FormRenderer content={content} values={{ ad: 'Ali', tek: 'B', cok: ['X', 'Y'], kilo: '12' }} mode="readonly" />);
    expect(container.querySelector('input,textarea,select')).toBeNull();
    expect(container.textContent).toContain('X, Y');
    expect(container.textContent).toContain('12 kg');
  });

  it('imza yerine signatureSlot ya da varsayilan kutu', async () => {
    await render(<FormRenderer content={content} values={{}} mode="fill" signatureSlot={<b>PAD</b>} onChange={() => {}} />);
    expect(container.textContent).toContain('PAD');
    act(() => root.unmount());
    container.remove();
    await render(<FormRenderer content={content} values={{}} mode="preview" />);
    expect(container.textContent).toContain('İmza alanı');
  });
});
