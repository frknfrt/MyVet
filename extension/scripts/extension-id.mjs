// manifest.json'daki "key" alanindan Chrome eklenti kimligini hesaplar:
// SHA-256(DER public key) -> ilk 16 bayt -> her hex hane 0-f => a-p.
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';

const manifest = JSON.parse(readFileSync(new URL('../public/manifest.json', import.meta.url), 'utf8'));
const der = Buffer.from(manifest.key, 'base64');
const hex = createHash('sha256').update(der).digest('hex').slice(0, 32);
const id = [...hex].map((c) => String.fromCharCode('a'.charCodeAt(0) + parseInt(c, 16))).join('');
console.log(id);
