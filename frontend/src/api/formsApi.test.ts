import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./client', () => ({
  apiClient: { post: vi.fn(), postMultipart: vi.fn() },
}));

import { apiClient } from './client';
import { formsApi } from './formsApi';

const content = { title: 'T', sections: [] };

describe('formsApi olusturma uclari govdedeki id yi doner', () => {
  beforeEach(() => vi.clearAllMocks());

  it('uploadSourceImage', async () => {
    vi.mocked(apiClient.postMultipart).mockResolvedValue({ id: 'img-1' });
    expect(await formsApi.uploadSourceImage(new Blob(['x']))).toBe('img-1');
    expect(vi.mocked(apiClient.postMultipart).mock.calls[0][0]).toBe('/api/v1/form-templates/source-images');
  });
  it('createTemplate', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ id: 'tpl-1' });
    expect(await formsApi.createTemplate({ content, sourceImageId: null })).toBe('tpl-1');
  });
  it('createSignedForm', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ id: 'sf-1' });
    const id = await formsApi.createSignedForm({
      templateId: 't', patientId: 'p', encounterId: null, values: {},
      signer: { type: 'OWNER', name: null, nationalId: null, phone: null }, signaturePngBase64: 'AAA',
    });
    expect(id).toBe('sf-1');
  });
});
