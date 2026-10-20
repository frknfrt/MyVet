// Tek surum kaynagi: package.json. manifest.json ayni olmali (version.test.ts denetler).
// Adlandirilmis import: Vite paketin yalniz bu alanini pakete koyar.
import { version } from '../../package.json';

export const EXTENSION_VERSION: string = version;
