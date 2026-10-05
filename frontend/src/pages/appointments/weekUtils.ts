const DAY_MS = 24 * 60 * 60 * 1000;
const ISTANBUL_TZ = 'Europe/Istanbul';

/** Verilen anın (Date veya ISO metni) Europe/Istanbul takvim gününü YYYY-MM-DD olarak döndürür. */
export function isoDate(date: Date | string): string {
  const d = typeof date === 'string' ? new Date(date) : date;
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: ISTANBUL_TZ,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(d);
}

/** Verilen tarihin (Europe/Istanbul takvim günü esas alınarak) ait olduğu haftanın Pazartesi gününü (00:00 UTC, sentetik takvim ızgarası) döndürür. */
export function mondayOf(date: Date): Date {
  const [y, m, d] = isoDate(date).split('-').map(Number);
  const utcMidnight = new Date(Date.UTC(y, m - 1, d));
  const dayOfWeek = utcMidnight.getUTCDay(); // 0=Sun..6=Sat
  const diffToMonday = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
  return new Date(utcMidnight.getTime() + diffToMonday * DAY_MS);
}

export function addDays(date: Date, days: number): Date {
  return new Date(date.getTime() + days * DAY_MS);
}

export function formatDayLabel(date: Date): string {
  return date.toLocaleDateString('tr-TR', { weekday: 'short', day: '2-digit', month: '2-digit', timeZone: 'UTC' });
}

export function formatWeekRange(monday: Date): string {
  const sunday = addDays(monday, 6);
  const fmt = (d: Date) => d.toLocaleDateString('tr-TR', { day: '2-digit', month: 'long', timeZone: 'UTC' });
  return `${fmt(monday)} — ${fmt(sunday)}`;
}

export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit', timeZone: ISTANBUL_TZ });
}
