/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_TARBIL_EXTENSION_ID?: string;
  readonly VITE_TARBIL_VACCINE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
