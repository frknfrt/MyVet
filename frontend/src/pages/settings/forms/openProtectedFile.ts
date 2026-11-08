import { formsApi } from '../../../api/formsApi';

/** Protected preview; popup fallback downloads without replacing the editor. */
export async function openProtectedFile(url: string): Promise<void> {
  const tab = window.open('', '_blank');
  let objectUrl: string | null = null;
  try {
    const res = await formsApi.fetchFile(url);
    if (!res.ok) throw new Error('Dosya açılamadı');
    const blob = await res.blob();
    objectUrl = URL.createObjectURL(blob);
    if (tab) {
      tab.opener = null;
      tab.location.href = objectUrl;
    } else {
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      const extension = ({ 'application/pdf': '.pdf', 'image/png': '.png', 'image/jpeg': '.jpg', 'image/webp': '.webp' } as Record<string, string>)[blob.type] ?? '';
      anchor.download = `form-dosyasi${extension}`;
      document.body.appendChild(anchor);
      try { anchor.click(); } finally { anchor.remove(); }
    }
    const openedUrl = objectUrl;
    setTimeout(() => URL.revokeObjectURL(openedUrl), 60_000);
  } catch (err) {
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    tab?.close();
    throw err;
  }
}
