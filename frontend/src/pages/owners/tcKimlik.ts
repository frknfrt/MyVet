/** Backend'deki TcKimlikValidator ile ayni resmi TCKN checksum algoritmasi -- aninda geri bildirim icin. */
export function isValidTcKimlik(candidate: string): boolean {
  if (!/^\d{11}$/.test(candidate) || candidate[0] === '0') return false;

  const d = candidate.split('').map(Number);
  const oddSum = d[0] + d[2] + d[4] + d[6] + d[8];
  const evenSum = d[1] + d[3] + d[5] + d[7];
  const expectedD10 = (oddSum * 7 - evenSum) % 10;
  const expectedD11 = (oddSum + evenSum + expectedD10) % 10;

  return d[9] === expectedD10 && d[10] === expectedD11;
}
