import { defineConfig } from 'vite';
import { resolve } from 'node:path';

export default defineConfig({
  build: {
    outDir: 'dist',
    emptyOutDir: false,
    lib: {
      entry: resolve(__dirname, 'src/tarbil/content.ts'),
      formats: ['iife'],
      name: 'VetlyTarbilContent',
      fileName: () => 'content.js',
    },
  },
});
