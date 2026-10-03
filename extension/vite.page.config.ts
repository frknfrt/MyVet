import { defineConfig } from 'vite';
import { resolve } from 'node:path';

// MAIN dunya betigi: ayri IIFE paket (content.js ile ayni dosyada olamaz, farkli dunyada calisir).
export default defineConfig({
  build: {
    outDir: 'dist',
    emptyOutDir: false,
    lib: {
      entry: resolve(__dirname, 'src/tarbil/page/main.ts'),
      formats: ['iife'],
      name: 'VetlyTarbilPage',
      fileName: () => 'page.js',
    },
  },
});
