export const FORM_ROLES = ['VET', 'TECHNICIAN', 'RECEPTIONIST', 'ADMIN'];

/** dd.MM.yyyy HH:mm */
export function formatDateTime(iso: string): string {
  const d = new Date(iso);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getDate())}.${p(d.getMonth() + 1)}.${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`;
}
