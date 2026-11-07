import { formsApi } from '../../../api/formsApi';

/** Yetkili (cookie'li) bir dosyayi indirip yeni sekmede acar. Popup engeli icin sekme once acilir. */
export async function openProtectedFile(url: string): Promise<void> {
  const tab = window.open('', '_blank');
  try {
    const res = await formsApi.fetchFile(url);
    if (!res.ok) throw new Error('Dosya açılamadı');
    const objectUrl = URL.createObjectURL(await res.blob());
    if (tab) tab.location.href = objectUrl;
    else window.location.href = objectUrl;
    setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
  } catch (err) {
    tab?.close();
    throw err;
  }
}
