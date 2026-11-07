// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FillFormButton } from './FillFormButton';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const mocks = vi.hoisted(() => ({ listTemplates: vi.fn(), role: 'VET' as string }));

vi.mock('../../api/formsApi', () => ({ formsApi: { listTemplates: mocks.listTemplates } }));
vi.mock('../../auth/AuthContext', () => ({ useAuth: () => ({ session: { role: mocks.role } }) }));

function Where() {
  const loc = useLocation();
  return <div data-testid="where">{loc.pathname + loc.search}</div>;
}

let root: Root;
let container: HTMLDivElement;

async function mount(encounterId?: string) {
  await act(async () => {
    root.render(
      <MemoryRouter initialEntries={['/start']}>
        <Routes>
          <Route path="/start" element={<FillFormButton patientId="p1" encounterId={encounterId} />} />
          <Route path="*" element={<Where />} />
        </Routes>
      </MemoryRouter>,
    );
  });
}

async function openMenu() {
  await act(async () => {
    (container.querySelector('button') as HTMLButtonElement).click();
  });
}

beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  mocks.role = 'VET';
  mocks.listTemplates.mockReset();
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe('FillFormButton', () => {
  it('aktif sablon yoksa bos mesaj gosterir; ADMIN icin link vardir', async () => {
    mocks.listTemplates.mockResolvedValue([]);
    mocks.role = 'ADMIN';
    await mount();
    await openMenu();
    expect(container.textContent).toContain("Henüz form yok — Ayarlar > Formlar'dan ekleyin");
    expect(container.querySelector('a[href="/ayarlar/formlar"]')).not.toBeNull();
  });

  it('VET icin bos mesajda link yoktur', async () => {
    mocks.listTemplates.mockResolvedValue([]);
    await mount();
    await openMenu();
    expect(container.textContent).toContain('Henüz form yok');
    expect(container.querySelector('a')).toBeNull();
  });

  it('sablon secilince encounterId dahil dogru URL e gider', async () => {
    mocks.listTemplates.mockResolvedValue([{ id: 't1', title: 'Anestezi Onayı' }]);
    await mount('e9');
    await openMenu();
    expect(mocks.listTemplates).toHaveBeenCalledWith('ACTIVE');
    await act(async () => {
      (container.querySelector('[role="menuitem"]') as HTMLButtonElement).click();
    });
    expect(container.querySelector('[data-testid="where"]')?.textContent).toBe('/formlar/doldur?templateId=t1&patientId=p1&encounterId=e9');
  });

  it('encounterId yoksa URL e eklenmez', async () => {
    mocks.listTemplates.mockResolvedValue([{ id: 't1', title: 'X' }]);
    await mount();
    await openMenu();
    await act(async () => {
      (container.querySelector('[role="menuitem"]') as HTMLButtonElement).click();
    });
    expect(container.querySelector('[data-testid="where"]')?.textContent).toBe('/formlar/doldur?templateId=t1&patientId=p1');
  });

  it('yetkisiz rolde (ACCOUNTANT) gorunmez', async () => {
    mocks.role = 'ACCOUNTANT';
    await mount();
    expect(container.querySelector('button')).toBeNull();
  });
});
