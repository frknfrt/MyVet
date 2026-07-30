import { HTMLAttributes, ReactNode } from 'react';
import styles from './Card.module.css';

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
  hoverable?: boolean;
}

export function Card({ children, hoverable, className, ...rest }: CardProps) {
  return (
    <div
      className={[styles.card, hoverable && styles.hoverable, className].filter(Boolean).join(' ')}
      {...rest}
    >
      {children}
    </div>
  );
}
