# Vetly TARBİL Yardımcısı (Chrome eklentisi)

Tasarım: `docs/superpowers/specs/2026-10-02-tarbil-eklenti-design.md`.

## Derleme

    npm ci
    npm run build          # prod: https://uygulama.vetly.com.tr
    npm run build:dev      # .env.development'taki VITE_VETLY_API_BASE (ör. http://localhost:8080)

## Kurulum (paketlenmemiş)

Chrome > `chrome://extensions` > Geliştirici modu > "Paketlenmemiş öğe yükle" > `extension/dist`.

## Eklenti kimliği

`npm run extension-id` — çıktı, frontend'deki `VITE_TARBIL_EXTENSION_ID` değeridir. Kimlik `public/manifest.json`'daki
`key` alanından türetilir; `key.pem` repoda tutulmaz (Web Store'a geçişte gerekir, güvenli yedekte saklanır).
