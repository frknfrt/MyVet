const DAY_MS = 24 * 60 * 60 * 1000;

/** Muayenenin açılışından bu yana geçen tam gün. */
export function daysOpen(encounterDate: string, now: Date = new Date()): number {
  return Math.max(0, Math.floor((now.getTime() - new Date(encounterDate).getTime()) / DAY_MS));
}

export function ageLabel(days: number): string {
  if (days === 0) return 'Bugün';
  if (days === 1) return 'Dün';
  return `${days} gün önce`;
}
