// Eklenti surumu karsilastirmasi (x.y.z, sayisal) -- backend ClientVersion ile ayni kural:
// bozuk surum 0.0.0 sayilir; "0.10.0" > "0.9.0".
function parts(v: string): [number, number, number] {
  const m = /^(\d{1,9})\.(\d{1,9})\.(\d{1,9})$/.exec(v.trim());
  return m ? [Number(m[1]), Number(m[2]), Number(m[3])] : [0, 0, 0];
}

export function compareVersions(a: string, b: string): number {
  const pa = parts(a);
  const pb = parts(b);
  for (let i = 0; i < 3; i++) {
    if (pa[i] !== pb[i]) return pa[i] - pb[i];
  }
  return 0;
}
