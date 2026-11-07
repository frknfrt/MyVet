import { defineConfig, loadEnv, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Content-Security-Policy: sayfaya sızan bir script oturum anahtarını çalamasın diye yalnızca kendi dosyalarımızdan
 * script çalışır. Yalnız derlemede eklenir (geliştirme sunucusu satır içi script kullanır). frame-ancestors meta
 * etiketinde çalışmaz; tıklama tuzağına karşı Nginx'te X-Frame-Options/frame-ancestors başlığı ayrıca verilmeli
 * (docs/deployment.md).
 */
export function buildCsp(apiBaseUrl: string | undefined): string {
  let apiOrigin = '';
  try {
    apiOrigin = apiBaseUrl ? new URL(apiBaseUrl).origin : '';
  } catch {
    apiOrigin = '';
  }
  const connect = ["'self'", apiOrigin].filter(Boolean).join(' ');
  return [
    "default-src 'self'",
    "script-src 'self'",
    // React'in style={{}} kullanımı CSSOM ile çalışır; 'unsafe-inline' yalnız kütüphanelerin <style> etiketleri için.
    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
    "font-src 'self' https://fonts.gstatic.com data:",
    // https: admin panelindeki e-posta önizlemesinin dış görselleri için.
    "img-src 'self' data: blob: https:",
    "media-src 'self' blob:",
    `connect-src ${connect}`,
    "frame-src 'self' blob:",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
  ].join('; ');
}

function cspMetaTag(apiBaseUrl: string | undefined): Plugin {
  return {
    name: 'vetly-csp-meta',
    apply: 'build',
    transformIndexHtml: () => [
      { tag: 'meta', attrs: { 'http-equiv': 'Content-Security-Policy', content: buildCsp(apiBaseUrl) }, injectTo: 'head-prepend' },
    ],
  };
}

export default defineConfig(({ mode }) => {
  const env = { ...loadEnv(mode, process.cwd(), 'VITE_'), ...process.env };
  return {
    plugins: [react(), cspMetaTag(env.VITE_API_BASE_URL)],
    server: { port: 5173 },
  };
});
