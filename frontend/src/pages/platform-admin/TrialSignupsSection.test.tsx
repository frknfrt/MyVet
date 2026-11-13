// @vitest-environment jsdom
import { act } from 'react';
import { createRoot, Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/platformAdminApi', async () => {
  const actual = await vi.importActual<typeof import('../../api/platformAdminApi')>('../../api/platformAdminApi');
  return {
    ...actual,
    platformAdminApi: { ...actual.platformAdminApi, listTrialSignupRequests: vi.fn() },
  };
});

import { platformAdminApi, TrialSignupRequest } from '../../api/platformAdminApi';
import { TrialSignupsSection } from './TrialSignupsSection';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const listMock = vi.mocked(platformAdminApi.listTrialSignupRequests);

function request(over: Partial<TrialSignupRequest> = {}): TrialSignupRequest {
  return {
    id: 'r1',
    fullName: 'Ayşe Yılmaz',
    email: 'ayse@ornek.com',
    phone: '+905321112233',
    clinicName: 'Pati Klinik',
    city: 'İzmir',
    chamberRegistrationNo: '35-1234',
    status: 'COMPLETED',
    tenantId: 't1',
    createdAt: '2026-11-13T09:00:00Z',
    completedAt: '2026-11-13T09:01:00Z',
    ...over,
  };
}

describe('TrialSignupsSection', () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    act(() => root.unmount());
    container.remove();
    vi.clearAllMocks();
  });

  async function render() {
    await act(async () => {
      root.render(
        <MemoryRouter>
          <TrialSignupsSection />
        </MemoryRouter>
      );
    });
  }

  it('oda sicil numarasını, ili ve iletişim bilgisini gösterir', async () => {
    listMock.mockResolvedValue([request()]);

    await render();

    expect(container.textContent).toContain('Pati Klinik');
    expect(container.textContent).toContain('Ayşe Yılmaz');
    expect(container.textContent).toContain('35-1234');
    expect(container.textContent).toContain('İzmir');
    expect(container.textContent).toContain('+905321112233');
    expect(container.textContent).toContain('ayse@ornek.com');
  });

  it('tamamlanan kayıtta "Hesap Açıldı", bekleyende "Kod Bekliyor" rozetini gösterir', async () => {
    listMock.mockResolvedValue([
      request({ id: 'r1', status: 'COMPLETED' }),
      request({ id: 'r2', status: 'PENDING', tenantId: null, completedAt: null, clinicName: 'Yarım Klinik' }),
    ]);

    await render();

    expect(container.textContent).toContain('Hesap Açıldı');
    expect(container.textContent).toContain('Kod Bekliyor');
  });

  it('kayıt yokken boş durum mesajını gösterir', async () => {
    listMock.mockResolvedValue([]);

    await render();

    expect(container.textContent).toContain('Henüz deneme kaydı yok');
  });

  it('liste yüklenemezse hata mesajını gösterir', async () => {
    listMock.mockRejectedValue(new Error('Sunucuya ulaşılamadı'));

    await render();

    expect(container.textContent).toContain('Sunucuya ulaşılamadı');
  });
});
