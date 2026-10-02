import type { BackgroundRequest, BackgroundResponse } from '../shared/messages';

export function sendToBackground<T>(req: BackgroundRequest): Promise<BackgroundResponse<T>> {
  return chrome.runtime.sendMessage(req);
}
