import type { DocumentType } from '../shared/types';
import { pageKind, pageUrlFor } from '../tarbil/selectors';

/** chrome.tabs'in kullandigimiz kismi (testte sahtesi verilir). */
export interface TabsApi {
  query(q: { url: string }): Promise<{ id?: number; url?: string; windowId?: number }[]>;
  update(id: number, props: { active?: boolean }): Promise<unknown>;
  create(props: { url: string; active?: boolean }): Promise<unknown>;
}

/**
 * Belge turunun TARBIL sayfasini one getirir: acik bir sekmesi varsa ona gecer, yoksa yeni sekmede acar. Oturum kapaliysa
 * TARBIL giris ekranini gosterir; e-Devlet donusunde icerik betigi sayfaya gecer (spec S12.2). Baska bir TARBIL sekmesinin
 * adresi DEGISTIRILMEZ -- orada kaydedilmemis is olabilir.
 */
export async function openTarbilPage(
  tabs: TabsApi,
  focusWindow: (windowId: number) => Promise<unknown>,
  documentType: DocumentType = 'VACCINATION',
): Promise<void> {
  const kind = documentType === 'PRESCRIPTION' ? 'prescriptionReceipt' : 'receipt';
  const open = await tabs.query({ url: 'https://hbsapp.tarbil.gov.tr/*' });
  const page = open.find((t) => {
    if (t.id === undefined || !t.url) return false;
    const u = new URL(t.url);
    return pageKind({ pathname: u.pathname, search: u.search }) === kind;
  });
  if (page?.id !== undefined) {
    await tabs.update(page.id, { active: true });
    if (page.windowId !== undefined) await focusWindow(page.windowId);
    return;
  }
  await tabs.create({ url: pageUrlFor(documentType), active: true });
}

export const openVaccinePage = (tabs: TabsApi, focusWindow: (windowId: number) => Promise<unknown>) =>
  openTarbilPage(tabs, focusWindow, 'VACCINATION');
