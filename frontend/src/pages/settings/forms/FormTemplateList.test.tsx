// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
vi.mock('../../../api/formsApi', () => ({ formsApi: { listTemplates: vi.fn(), archiveTemplate: vi.fn(), getTemplate: vi.fn(), sourceImageUrl: (id: string) => `/img/${id}` } }));
vi.mock('./openProtectedFile', () => ({ openProtectedFile: vi.fn().mockResolvedValue(undefined) }));
import { formsApi, type FormTemplateSummary } from '../../../api/formsApi';
import { openProtectedFile } from './openProtectedFile';
import { FormTemplateList } from './FormTemplateList';
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
let root: Root, container: HTMLDivElement;
const row = (status: 'ACTIVE' | 'ARCHIVED', title: string = status): FormTemplateSummary => ({ id: status, title, status, updatedAt: '2026-11-07T10:00:00Z', signedCount: 2, hasSourceImage: true });
beforeEach(() => { vi.clearAllMocks(); vi.mocked(formsApi.listTemplates).mockResolvedValue([]); vi.mocked(formsApi.archiveTemplate).mockResolvedValue(undefined); });
afterEach(() => { act(() => root.unmount()); container.remove(); vi.restoreAllMocks(); });
async function render() { container = document.createElement('div'); document.body.appendChild(container); root = createRoot(container); await act(async () => root.render(<MemoryRouter><FormTemplateList /></MemoryRouter>)); }
async function toggle() { await act(async () => container.querySelector<HTMLInputElement>('input[type=checkbox]')!.click()); }

it('shows empty state and create link', async () => {
  await render();
  expect(container.textContent).toContain('Henüz form şablonu yok');
  const createLink = [...container.querySelectorAll('a')].find(link => link.textContent?.trim() === '+ Yeni form');
  expect(createLink?.getAttribute('href')).toBe('/ayarlar/formlar/yeni');
});

it.each([true, false])('archives only after confirmation: %s', async allow => {
  vi.mocked(formsApi.listTemplates).mockResolvedValueOnce([row('ACTIVE')]);
  vi.spyOn(window, 'confirm').mockReturnValue(allow);
  await render();
  expect(container.textContent).toContain('2 imzalı form');
  expect(container.querySelector('a[href="/ayarlar/formlar/ACTIVE"]')).not.toBeNull();
  const archive = [...container.querySelectorAll('button')].find(b => b.textContent === 'Arşivle')!;
  await act(async () => archive.click());
  expect(formsApi.archiveTemplate).toHaveBeenCalledTimes(allow ? 1 : 0);
});

it('clears old error after successful archived reload and hides archived writes', async () => {
  vi.mocked(formsApi.listTemplates).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce([row('ARCHIVED')]);
  await render(); expect(container.textContent).toContain('offline');
  await toggle(); expect(formsApi.listTemplates).toHaveBeenLastCalledWith('ALL');
  expect(container.textContent).not.toContain('offline'); expect(container.textContent).toContain('ARCHIVED');
  expect([...container.querySelectorAll('button')].some(b => b.textContent === 'Arşivle')).toBe(false); expect(container.querySelector('a[href="/ayarlar/formlar/ARCHIVED"]')).toBeNull();
});

it('ignores late previous filter response', async () => {
  let finish!: (rows: FormTemplateSummary[]) => void;
  vi.mocked(formsApi.listTemplates).mockReturnValueOnce(new Promise(resolve => { finish = resolve; })).mockResolvedValueOnce([row('ARCHIVED', 'LATEST')]);
  await render(); expect(container.textContent).toContain('Yükleniyor');
  await toggle(); expect(container.textContent).toContain('LATEST');
  await act(async () => finish([row('ACTIVE', 'STALE')]));
  expect(container.textContent).toContain('LATEST'); expect(container.textContent).not.toContain('STALE');
});

it('opens reference through protected file helper', async () => {
  vi.mocked(formsApi.listTemplates).mockResolvedValue([row('ACTIVE')]);
  vi.mocked(formsApi.getTemplate).mockResolvedValue({ sourceImageId: 'img' } as never);
  await render(); const button = [...container.querySelectorAll('button')].find(b => b.textContent === 'Referans görsel')!;
  await act(async () => button.click()); expect(openProtectedFile).toHaveBeenCalledWith('/img/img');
});
