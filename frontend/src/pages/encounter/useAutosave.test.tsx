// @vitest-environment jsdom
import { act, useEffect, useState } from 'react';
import { createRoot, Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Autosave, useAutosave } from './useAutosave';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;
let setValue: (v: string) => void;
let api: Autosave;

function Harness({ save, enabled = true }: { save: (v: string) => Promise<void>; enabled?: boolean }) {
  const [value, set] = useState('kayıtlı');
  setValue = set;
  api = useAutosave(value, save, { delayMs: 1500, enabled, retryMs: 5000 });
  useEffect(() => api.markSaved('kayıtlı'), []); // sayfa yüklemesi
  return null;
}

async function mount(save: (v: string) => Promise<void>, enabled = true) {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  await act(async () => root.render(<Harness save={save} enabled={enabled} />));
}

beforeEach(() => vi.useFakeTimers());
afterEach(() => {
  act(() => root?.unmount());
  container?.remove();
  vi.useRealTimers();
});

describe('useAutosave', () => {
  it('does not save the value that was just loaded', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    await mount(save);
    await act(async () => vi.advanceTimersByTime(3000));
    expect(save).not.toHaveBeenCalled();
    expect(api.dirty).toBe(false);
  });

  it('saves once after typing pauses, not on every keystroke', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    await mount(save);

    await act(async () => setValue('a'));
    await act(async () => vi.advanceTimersByTime(1000));
    await act(async () => setValue('ab'));
    await act(async () => vi.advanceTimersByTime(1000));
    expect(save).not.toHaveBeenCalled();
    expect(api.status).toBe('pending');

    await act(async () => vi.advanceTimersByTime(600));
    expect(save).toHaveBeenCalledTimes(1);
    expect(save).toHaveBeenCalledWith('ab');
    expect(api.status).toBe('saved');
    expect(api.lastSavedAt).not.toBeNull();
  });

  it('flush sends the pending change immediately', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    await mount(save);
    await act(async () => setValue('acil'));
    await act(async () => api.flush());
    expect(save).toHaveBeenCalledWith('acil');
    expect(api.dirty).toBe(false);
  });

  it('keeps the change and retries after a failed save', async () => {
    const save = vi.fn().mockRejectedValueOnce(new Error('ağ hatası')).mockResolvedValue(undefined);
    await mount(save);
    await act(async () => setValue('not'));
    await act(async () => vi.advanceTimersByTime(1500));
    expect(api.status).toBe('error');
    expect(api.dirty).toBe(true);

    await act(async () => vi.advanceTimersByTime(5000));
    expect(save).toHaveBeenCalledTimes(2);
    expect(save).toHaveBeenLastCalledWith('not');
    expect(api.status).toBe('saved');
  });

  it('does nothing when disabled (read-only encounter)', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    await mount(save, false);
    await act(async () => setValue('x'));
    await act(async () => vi.advanceTimersByTime(3000));
    expect(save).not.toHaveBeenCalled();
  });
});
