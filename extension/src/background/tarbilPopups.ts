// TARBIL arama/stok pencerelerini TARBIL'in kendisi, sunucu yanitindan sonra window.open ile acar; kullanici tiklamasina
// bagli olmadigi icin Chrome engeller. Eklenti yalniz hbsapp icin acilir pencerelere izin verir (2026-10-06 karar);
// kullanicinin Chrome ayarlarina girmesi gerekmez. Diger sitelere dokunulmaz. Basarisiz olursa kart "pencereyi ac"
// dugmesini gostermeye devam eder.

export const TARBIL_POPUP_PATTERN = 'https://hbsapp.tarbil.gov.tr/*';

export interface PopupSettings {
  popups: { set: (details: { primaryPattern: string; setting: 'allow' }) => Promise<void> };
}

export async function allowTarbilPopups(settings: PopupSettings): Promise<boolean> {
  try {
    await settings.popups.set({ primaryPattern: TARBIL_POPUP_PATTERN, setting: 'allow' });
    return true;
  } catch {
    return false;
  }
}
