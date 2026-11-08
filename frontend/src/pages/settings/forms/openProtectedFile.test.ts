// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
vi.mock('../../../api/formsApi', () => ({ formsApi: { fetchFile: vi.fn() } }));
import { formsApi } from '../../../api/formsApi';
import { openProtectedFile } from './openProtectedFile';

beforeEach(() => {
  vi.useFakeTimers();
  Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:preview') });
  Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() });
  vi.mocked(formsApi.fetchFile).mockResolvedValue(new Response('pdf', { headers: { 'Content-Type': 'application/pdf' } }));
});
afterEach(() => { vi.clearAllTimers(); vi.useRealTimers(); vi.restoreAllMocks(); vi.clearAllMocks(); });

it('opens the tab before fetching and removes its opener', async () => {
  const tab = { opener: window, location: { href: '' }, close: vi.fn() };
  const open = vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window);
  await openProtectedFile('/pdf');
  expect(open.mock.invocationCallOrder[0]).toBeLessThan(vi.mocked(formsApi.fetchFile).mock.invocationCallOrder[0]);
  expect(tab.location.href).toBe('blob:preview'); expect(tab.opener).toBeNull();
  expect(URL.revokeObjectURL).not.toHaveBeenCalled();
  vi.advanceTimersByTime(60_000);
  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview');
});

it('downloads if blocked, without replacing current page', async () => {
  vi.spyOn(window, 'open').mockReturnValue(null);
  const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  const location = window.location.href;
  await openProtectedFile('/pdf');
  expect(window.location.href).toBe(location);
  expect(click).toHaveBeenCalledTimes(1);
  const anchor = click.mock.instances[0] as HTMLAnchorElement;
  expect(anchor.download).toBe('form-dosyasi.pdf'); expect(anchor.href).toBe('blob:preview');
  expect(anchor.isConnected).toBe(false);
  expect(URL.revokeObjectURL).not.toHaveBeenCalled();
  vi.advanceTimersByTime(60_000); expect(URL.revokeObjectURL).toHaveBeenCalledOnce();
});

it.each(['http', 'network'])('closes empty tab on %s failure', async failure => {
  const tab = { location: { href: '' }, close: vi.fn() };
  vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window);
  if (failure === 'http') vi.mocked(formsApi.fetchFile).mockResolvedValue(new Response('', { status: 500 }));
  else vi.mocked(formsApi.fetchFile).mockRejectedValue(new Error('network'));
  await expect(openProtectedFile('/pdf')).rejects.toThrow();
  expect(tab.close).toHaveBeenCalledOnce(); expect(URL.createObjectURL).not.toHaveBeenCalled();
});
