// Sayfanin kendi dunyasinda (world: MAIN) calisir: $find ve PageRequestManager yalniz burada erisilebilir.
// Vetly API'sine, eklenti anahtarina ya da chrome.* API'lerine erisimi YOKTUR; yalniz komut yurutur.
import { installPageHandler } from '../bridge';

installPageHandler(window, {
  ping: () => 'pong',
});
