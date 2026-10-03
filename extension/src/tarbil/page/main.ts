// Sayfanin kendi dunyasinda (world: MAIN) calisir: $find ve PageRequestManager yalniz burada erisilebilir.
// Vetly API'sine, eklenti anahtarina ya da chrome.* API'lerine erisimi YOKTUR; yalniz komut yurutur.
import { installPageHandler } from '../bridge';
import { createPageOps } from './ops';
import type { Prm, TelerikComponent, TelerikEnv } from './telerik';

/* eslint-disable @typescript-eslint/no-explicit-any */
const w = window as any;

const env: TelerikEnv = {
  doc: document,
  find: (id: string) => (typeof w.$find === 'function' ? ((w.$find(id) as TelerikComponent | null) ?? null) : null),
  prm: () => (w.Sys?.WebForms?.PageRequestManager?.getInstance?.() as Prm | undefined) ?? null,
  isReady: () => Boolean(w.Sys?.Application?.get_isInitialized?.()),
};

installPageHandler(window, createPageOps(env));
