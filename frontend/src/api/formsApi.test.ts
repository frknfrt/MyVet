import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./client', () => ({
  API_BASE_URL: 'https://configured.example',
  apiClient: { get: vi.fn(), post: vi.fn(), postMultipart: vi.fn() },
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
      requestId: 'submission-id', templateId: 't', templateContentHash: 'opened-template-hash', patientId: 'p', encounterId: null, values: {},
      signer: { type: 'OWNER', name: null, nationalId: null, phone: null }, signaturePngBase64: 'AAA',
    });
    expect(id).toBe('sf-1');
    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/signed-forms', expect.objectContaining({ templateContentHash: 'opened-template-hash', requestId: 'submission-id' }));
  });
});

it('all file URLs use the shared API base', () => {
  expect(formsApi.sourceImageUrl('img')).toBe('https://configured.example/api/v1/form-templates/source-images/img');
  expect(formsApi.templatePdfUrl('tpl')).toBe('https://configured.example/api/v1/form-templates/tpl/pdf');
  expect(formsApi.signatureUrl('sf')).toBe('https://configured.example/api/v1/signed-forms/sf/signature');
  expect(formsApi.signedFormPdfUrl('sf')).toBe('https://configured.example/api/v1/signed-forms/sf/pdf');
});

it.each([{ patientId: 'p&1' }, { ownerId: 'o&1' }, { patientId: 'p&1', ownerId: 'o&1' }])('list filter parameters are preserved: %o', async filter => {
  vi.mocked(apiClient.get).mockResolvedValue([]);
  await formsApi.listSignedForms(filter);
  const url = vi.mocked(apiClient.get).mock.lastCall![0];
  const params = new URL(url, 'https://test.invalid').searchParams;
  expect(params.get('patientId')).toBe(filter.patientId ?? null);
  expect(params.get('ownerId')).toBe(filter.ownerId ?? null);
});
