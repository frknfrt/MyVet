// Faz 7: derlenmis eklentiyi (dist/) web uygulamasinin indirme klasorune zip'ler ve
// yanina en yeni surumu yazar. Kabuk komutu kullanmaz (Windows + Linux).
// Kullanim: npm run package  (once build calisir)
import { mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { zipSync } from 'fflate';

const root = fileURLToPath(new URL('..', import.meta.url));
const dist = join(root, 'dist');
const outDir = join(root, '..', 'frontend', 'public', 'downloads');

const { version } = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8'));
const manifest = JSON.parse(readFileSync(join(dist, 'manifest.json'), 'utf8'));
if (manifest.version !== version) {
  console.error(`dist/manifest.json surumu (${manifest.version}) package.json (${version}) ile ayni degil.`);
  process.exit(1);
}

function collect(dir, files = {}) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) collect(full, files);
    else files[relative(dist, full).split(sep).join('/')] = readFileSync(full);
  }
  return files;
}

mkdirSync(outDir, { recursive: true });
writeFileSync(join(outDir, 'vetly-tarbil-eklenti.zip'), zipSync(collect(dist), { level: 9 }));
writeFileSync(join(outDir, 'vetly-tarbil-eklenti.json'), JSON.stringify({ version }) + '\n');
console.log(`vetly-tarbil-eklenti ${version} -> ${outDir}`);
