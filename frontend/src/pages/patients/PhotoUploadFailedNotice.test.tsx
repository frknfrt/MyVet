// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { PhotoUploadFailedNotice } from './PhotoUploadFailedNotice';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
afterEach(() => container?.remove());

async function renderAt(state: unknown) {
  container = document.createElement('div');
  document.body.appendChild(container);
  await act(async () => {
    createRoot(container).render(
      <MemoryRouter
        initialEntries={[{ pathname: '/hastalar/p9', state }]}
       
      >
        <PhotoUploadFailedNotice />
      </MemoryRouter>
    );
  });
}

describe('PhotoUploadFailedNotice', () => {
  it('tells the user the patient was saved but the photo was not, when arriving flagged', async () => {
    await renderAt({ photoUploadFailed: true });
    expect(container.textContent).toContain('Hasta kaydedildi ama fotoğraf yüklenemedi');
  });

  it('renders nothing on a normal visit', async () => {
    await renderAt(null);
    expect(container.textContent).toBe('');
  });
});
