import { defineConfig } from 'vitest/config';

// Birim/bilesen testleri (Faz 7'de eklendi). Playwright e2e ayri: npm run test:e2e.
export default defineConfig({
  test: { environment: 'node', include: ['src/**/*.test.ts', 'src/**/*.test.tsx'] },
});
