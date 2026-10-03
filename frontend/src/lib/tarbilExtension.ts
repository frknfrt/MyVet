// Vetly TARBIL eklentisiyle konusma (externally_connectable). Eklenti yoksa
// chrome.runtime tanimsiz ya da sendMessage lastError verir.
type Reply = { ok: true; data: unknown } | { ok: false; error: string; code: string };

const EXTENSION_ID = import.meta.env.VITE_TARBIL_EXTENSION_ID;
export const TARBIL_VACCINE_URL = import.meta.env.VITE_TARBIL_VACCINE_URL ?? 'https://hbsapp.tarbil.gov.tr/Modules/RECEIPT/Pages/ATS/VaccineReceipt/VaccineReceiptPage.aspx?type=1';

declare const chrome: {
  runtime?: { sendMessage: (id: string, msg: unknown, cb: (reply: Reply | undefined) => void) => void; lastError?: unknown };
};

function send(msg: unknown): Promise<Reply | undefined> {
  return new Promise((resolve) => {
    if (!EXTENSION_ID || typeof chrome === 'undefined' || !chrome.runtime?.sendMessage) {
      resolve(undefined);
      return;
    }
    try {
      chrome.runtime.sendMessage(EXTENSION_ID, msg, (reply) => {
        resolve(chrome.runtime?.lastError ? undefined : reply);
      });
    } catch {
      resolve(undefined);
    }
  });
}

export async function isExtensionAvailable(): Promise<boolean> {
  const reply = await send({ type: 'PING' });
  return !!reply?.ok;
}

export async function selectForTarbil(
  vaccinationRecordId: string,
): Promise<{ ok: boolean; reason?: 'NOT_INSTALLED' | 'NOT_PAIRED' | 'NOT_FOUND' | 'ERROR' }> {
  const reply = await send({ type: 'SELECT_SUBMISSION', vaccinationRecordId });
  if (!reply) return { ok: false, reason: 'NOT_INSTALLED' };
  if (reply.ok) return { ok: true };
  if (reply.code === 'UNAUTHORIZED') return { ok: false, reason: 'NOT_PAIRED' };
  if (reply.code === 'NOT_FOUND') return { ok: false, reason: 'NOT_FOUND' };
  return { ok: false, reason: 'ERROR' };
}
