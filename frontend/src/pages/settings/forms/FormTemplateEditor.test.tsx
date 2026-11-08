// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../../api/formsApi', () => ({
  formsApi: {
    createTemplate: vi.fn().mockResolvedValue('new-id'),
    updateTemplate: vi.fn().mockResolvedValue(undefined),
    fetchFile: vi.fn(),
    sourceImageUrl: (id: string) => `/img/${id}`,
    templatePdfUrl: (id: string) => `/pdf/${id}`,
  },
}));

import { formsApi } from '../../../api/formsApi';
import type { FormItem, FormTemplateContent } from '../../../forms/formModel';
import { FormTemplateEditor } from './FormTemplateEditor';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

function item(p: Partial<FormItem>): FormItem {
  return { type: 'SHORT_TEXT', code: null, label: null, text: null, required: false, binding: null, unit: null, options: null, multiple: false, ...p };
}
const base: FormTemplateContent = {
  title: 'Onam',
  sections: [{ title: null, items: [item({ type: 'SHORT_TEXT', code: 'ad', label: 'Ad' }), item({ type: 'SIGNATURE', code: 'imza', label: 'İmza' })] }],
};

let container: HTMLDivElement;
let root: Root;
const onSaved = vi.fn();

async function render(props: Partial<React.ComponentProps<typeof FormTemplateEditor>> = {}) {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  await act(async () =>
    root.render(
      <FormTemplateEditor initialContent={base} sourceImageId="img1" templateId={null} isDraft={false} warnings={[]} onSaved={onSaved} onCancel={() => {}} {...props} />,
    ),
  );
}
const btn = (text: string) => [...container.querySelectorAll('button')].find((b) => b.textContent?.trim() === text) as HTMLButtonElement | undefined;
const codes = () => [...container.querySelectorAll('[data-code]')].map((e) => e.textContent);

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(formsApi.fetchFile).mockRejectedValue(new Error('no image'));
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

function setValue(el: HTMLInputElement, v: string) {
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(el, v);
  el.dispatchEvent(new Event('input', { bubbles: true }));
}
async function typeInto(selector: string, v: string) {
  const el = container.querySelector<HTMLInputElement>(selector)!;
  await act(async () => setValue(el, v));
}

async function addField(label: string) {
  await act(async () => btn('+ Alan ekle')!.click());
  await act(async () => btn(label)!.click());
}

describe('FormTemplateEditor', () => {
  it('+ Alan ekle kisa metin ekler; kodlar alan, alan_2', async () => {
    await render();
    await addField('Kısa metin');
    await addField('Kısa metin');
    expect(codes()).toEqual(['ad', 'alan', 'alan_2', 'imza']);
  });

  it('yukari tasima sirayi degistirir, Sil kaldirir', async () => {
    await render();
    await addField('Kısa metin');
    // sira: ad, alan, imza -> alan'i yukari
    const ups = [...container.querySelectorAll('button[aria-label="Yukarı taşı"]')] as HTMLButtonElement[];
    await act(async () => ups[1].click());
    expect(codes()).toEqual(['alan', 'ad', 'imza']);
    const dels = [...container.querySelectorAll('button[aria-label="Sil"]')] as HTMLButtonElement[];
    await act(async () => dels[0].click());
    expect(codes()).toEqual(['ad', 'imza']);
  });

  it('ikinci imza eklenemez ve mesaj gosterilir', async () => {
    await render();
    await act(async () => btn('+ Alan ekle')!.click());
    await act(async () => btn('İmza alanı')!.click());
    expect(container.textContent).toContain('Formda zaten bir imza alanı var');
    expect(codes()).toEqual(['ad', 'imza']);
  });

  it('AI uyarilari ve taslak cercevesi gorunur', async () => {
    await render({ isDraft: true, warnings: ['Bir alan okunamadı'] });
    expect(container.textContent).toContain('AI taslağı — onayınızı bekliyor');
    expect(container.textContent).toContain('Bir alan okunamadı');
  });

  it('Formu Oluştur icerik + sourceImageId ile createTemplate cagirir', async () => {
    await render({ isDraft: true });
    await act(async () => btn('Formu Oluştur')!.click());
    expect(formsApi.createTemplate).toHaveBeenCalledWith({ content: base, sourceImageId: 'img1' });
    expect(onSaved).toHaveBeenCalledWith('new-id');
  });

  it('gecersiz icerikte (imzasiz) kaydetmez, hata gosterir', async () => {
    await render({ initialContent: { title: 'X', sections: [{ title: null, items: [item({ code: 'ad', label: 'Ad' })] }] } });
    await act(async () => btn('Formu Oluştur')!.click());
    expect(formsApi.createTemplate).not.toHaveBeenCalled();
    expect(container.textContent).toContain('tam olarak bir imza');
  });

  it('kayitli sablonda Kaydet updateTemplate cagirir', async () => {
    await render({ templateId: 't1' });
    await act(async () => btn('Kaydet')!.click());
    expect(formsApi.updateTemplate).toHaveBeenCalledWith('t1', base);
  });

  it('YENI alanin etiketi degisince kod guncellenir, yalniz ayni bolumdeki {{eski}} yeniden yazilir', async () => {
    const withBlocks: FormTemplateContent = {
      title: 'Onam',
      sections: [
        { title: null, items: [item({ type: 'TEXT_BLOCK', text: 'Ben {{alan}}' }), item({ type: 'SIGNATURE', code: 'imza', label: 'İmza' })] },
        { title: null, items: [item({ type: 'TEXT_BLOCK', text: 'Diger {{alan}}' })] },
      ],
    };
    await render({ initialContent: withBlocks });
    const addBtns = [...container.querySelectorAll('button')].filter((b) => b.textContent?.trim() === '+ Alan ekle');
    await act(async () => addBtns[0].click());
    await act(async () => btn('Kısa metin')!.click());
    expect(codes()).toEqual(['alan', 'imza']);
    await typeInto('input[placeholder="Alan adı"]', 'Çiğdem Öğüş');
    expect(codes()).toEqual(['cigdem_ogus', 'imza']);
    const texts = [...container.querySelectorAll('textarea')].map((t) => t.value);
    expect(texts).toContain('Ben {{cigdem_ogus}}');
    expect(texts).toContain('Diger {{alan}}');
  });

  it('YUKLENMIS (kayitli) alanin etiketi degisse de kodu sabit kalir; imza kodu da', async () => {
    await render({ templateId: 't1' });
    await typeInto('input[placeholder="Alan adı"]', 'Tamamen Farkli');
    expect(codes()).toEqual(['ad', 'imza']);
  });

  it('yeni sablonda imza etiketi degisse de imza kodu sabit', async () => {
    await render();
    const inputs = container.querySelectorAll<HTMLInputElement>('input[placeholder="Alan adı"]');
    await act(async () => setValue(inputs[1], 'Sahibin İmzası'));
    expect(codes()).toEqual(['ad', 'imza']);
  });

  it('tur CHOICE ye gecince baglanti temizlenir', async () => {
    await render({
      initialContent: { title: 'X', sections: [{ title: null, items: [item({ code: 'ad', label: 'Ad', binding: 'owner.fullName' }), item({ type: 'SIGNATURE', code: 'imza', label: 'İmza' })] }] },
    });
    const sel = container.querySelector<HTMLSelectElement>('select[aria-label="Bağlantı"]')!;
    expect(sel.value).toBe('owner.fullName');
    const typeSel = container.querySelector<HTMLSelectElement>('select[aria-label="Alan türü"]')!;
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value')!.set!.call(typeSel, 'CHOICE');
      typeSel.dispatchEvent(new Event('change', { bubbles: true }));
    });
    expect(container.querySelector<HTMLSelectElement>('select[aria-label="Bağlantı"]')!.value).toBe('');
  });
  it.each([
    ['SHORT_TEXT', 'owner.fullName', 'NUMBER', ''],
    ['SHORT_TEXT', 'owner.fullName', 'LONG_TEXT', 'owner.fullName'],
    ['SHORT_TEXT', 'patient.species', 'CHOICE', 'patient.species'],
    ['CHOICE', 'patient.sex', 'SHORT_TEXT', 'patient.sex'],
  ] as const)('tur degisimi yalniz uyumlu baglantiyi korur: %s %s -> %s', async (type, binding, nextType, expected) => {
    await render({ initialContent: { title: 'X', sections: [{ title: null, items: [
      item({ type, code: 'alan', label: 'Alan', binding, options: type === 'CHOICE' ? ['A', 'B'] : null }),
      item({ type: 'SIGNATURE', code: 'imza', label: 'İmza' }),
    ] }] } });
    const select = container.querySelector<HTMLSelectElement>('select[aria-label="Alan türü"]')!;
    await act(async () => {
      select.value = nextType;
      select.dispatchEvent(new Event('change', { bubbles: true }));
    });
    const bindings = container.querySelector<HTMLSelectElement>('select[aria-label="Bağlantı"]')!;
    expect(bindings.value).toBe(expected);
    expect(bindings.querySelector<HTMLOptionElement>('option[value="owner.birthDate"]')!.disabled).toBe(true);
  });

  it.each([true, false])('sidebar navigation asks before leaving unsaved draft; allow=%s', async allow => {
    await render();
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(allow);
    const anchor = document.createElement('a'); anchor.href = '/patients'; document.body.appendChild(anchor);
    const navigate = vi.fn((e: Event) => e.preventDefault()); anchor.addEventListener('click', navigate);
    try {
      anchor.click();
      expect(confirmation).toHaveBeenCalledTimes(1);
      expect(navigate).toHaveBeenCalledTimes(allow ? 1 : 0);
    } finally { confirmation.mockRestore(); anchor.remove(); }
  });

  it('pristine saved template does not block sidebar navigation', async () => {
    await render({ templateId: 't1' });
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const anchor = document.createElement('a'); anchor.href = '/patients'; document.body.appendChild(anchor);
    const navigate = vi.fn((e: Event) => e.preventDefault()); anchor.addEventListener('click', navigate);
    try { anchor.click(); expect(confirmation).not.toHaveBeenCalled(); expect(navigate).toHaveBeenCalledTimes(1); }
    finally { confirmation.mockRestore(); anchor.remove(); }
  });

  it('modified saved template blocks navigation and removes guard on unmount', async () => {
    await render({ templateId: 't1' });
    await typeInto('input[placeholder="Alan adı"]', 'Yeni etiket');
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const anchor = document.createElement('a'); anchor.href = '/patients'; document.body.appendChild(anchor);
    const navigate = vi.fn((e: Event) => e.preventDefault()); anchor.addEventListener('click', navigate);
    try {
      anchor.click(); expect(navigate).not.toHaveBeenCalled();
      await act(async () => root.render(null));
      anchor.click(); expect(navigate).toHaveBeenCalledTimes(1); expect(confirmation).toHaveBeenCalledTimes(1);
    } finally { confirmation.mockRestore(); anchor.remove(); }
  });

  it('switching CHOICE to paragraph and back does not retain stale input metadata', async () => {
    await render({ initialContent: { title: 'X', sections: [{ title: null, items: [
      item({ type: 'CHOICE', code: 'secim', label: 'Seçim', binding: 'patient.sex', options: ['A', 'B'], multiple: true }),
      item({ type: 'SIGNATURE', code: 'imza', label: 'İmza' }),
    ] }] } });
    const changeType = async (value: string) => {
      const select = container.querySelector<HTMLSelectElement>('select[aria-label="Alan türü"]')!;
      await act(async () => { select.value = value; select.dispatchEvent(new Event('change', { bubbles: true })); });
    };
    await changeType('TEXT_BLOCK');
    expect(container.querySelector('select[aria-label="Bağlantı"]')).toBeNull();
    await changeType('SHORT_TEXT');
    expect(container.querySelector<HTMLSelectElement>('select[aria-label="Bağlantı"]')!.value).toBe('');
    expect(container.textContent).not.toContain('Seçenekler (her satıra bir seçenek)');
  });

});
