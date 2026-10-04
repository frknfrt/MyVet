import { VACCINE_PAGE_URL, pageKind } from '../tarbil/selectors';

/** chrome.tabs'in kullandigimiz kismi (testte sahtesi verilir). */
export interface TabsApi {
  query(q: { url: string }): Promise<{ id?: number; url?: string; windowId?: number }[]>;
  update(id: number, props: { active?: boolean }): Promise<unknown>;
  create(props: { url: string; active?: boolean }): Promise<unknown>;
}

/**
 * Asi Uygulama Belgesi Ekle sayfasini one getirir: acik bir asi sayfasi sekmesi varsa ona gecer, yoksa yeni
 * sekmede acar. Oturum kapaliysa TARBIL giris ekranini gosterir; e-Devlet donusunde icerik betigi asi sayfasina
 * gecer (spec S12.2). Baska bir TARBIL sekmesinin adresi DEGISTIRILMEZ -- orada kaydedilmemis is olabilir.
 */
export async function openVaccinePage(tabs: TabsApi, focusWindow: (windowId: number) => Promise<unknown>): Promise<void> {
  const open = await tabs.query({ url: 'https://hbsapp.tarbil.gov.tr/*' });
  const receipt = open.find((t) => {
    if (t.id === undefined || !t.url) return false;
    const u = new URL(t.url);
    return pageKind({ pathname: u.pathname, search: u.search }) === 'receipt';
  });
  if (receipt?.id !== undefined) {
    await tabs.update(receipt.id, { active: true });
    if (receipt.windowId !== undefined) await focusWindow(receipt.windowId);
    return;
  }
  await tabs.create({ url: VACCINE_PAGE_URL, active: true });
}
