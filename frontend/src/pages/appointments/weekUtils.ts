const DAY_MS = 24 * 60 * 60 * 1000;

export function isoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

/** Verilen tarihin ait olduğu haftanın Pazartesi gününü (00:00 UTC) döndürür. */
export function mondayOf(date: Date): Date {
  const utcMidnight = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
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
  return new Date(iso).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit', timeZone: 'UTC' });
}
