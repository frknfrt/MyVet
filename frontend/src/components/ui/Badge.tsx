import { ReactNode } from 'react';
import styles from './Badge.module.css';

export type BadgeTone = 'success' | 'warning' | 'danger' | 'ai' | 'neutral' | 'gold';

interface BadgeProps {
  tone: BadgeTone;
  icon?: ReactNode;
  children: ReactNode;
}

/**
 * Kural: dört renk ailesi asla birbirinin alanına girmez.
 * success/warning/danger = durum bildirimi, ai = AI üretimi içerik,
 * gold = "önerilen/premium" (çok nadir kullanılır).
 */
export function Badge({ tone, icon, children }: BadgeProps) {
  return (
    <span className={[styles.badge, styles[tone]].join(' ')}>
      {icon}
      {children}
    </span>
  );
}
