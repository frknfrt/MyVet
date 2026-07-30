import { ButtonHTMLAttributes, MouseEvent, ReactNode, useRef } from 'react';
import styles from './Button.module.css';

type ButtonVariant = 'primary' | 'secondary' | 'tertiary' | 'danger' | 'ai';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  icon?: ReactNode;
  children: ReactNode;
}

/**
 * Kural (tasarım sistemi v1.3):
 * - Bir ekranda en fazla 1 "primary" (mor dolgu) buton olmalı.
 * - "Detay gör" gibi sık kullanılan ikincil eylemler "secondary" olmalı
 *   (şeffaf ghost stil KULLANILMAZ, görünmez olur).
 * - "tertiary" sadece satır içi metin linkleri için.
 * - "ai" varyantı AI tetikleyen eylemler için, mavi aile.
 */
export function Button({
  variant = 'secondary',
  icon,
  children,
  className,
  onClick,
  ...rest
}: ButtonProps) {
  const ref = useRef<HTMLButtonElement>(null);

  function handleClick(e: MouseEvent<HTMLButtonElement>) {
    const btn = ref.current;
    if (btn) {
      const rect = btn.getBoundingClientRect();
      const ripple = document.createElement('span');
      const size = Math.max(rect.width, rect.height) * 1.6;
      ripple.className = styles.ripple;
      ripple.style.width = ripple.style.height = `${size}px`;
      ripple.style.left = `${e.clientX - rect.left - size / 2}px`;
      ripple.style.top = `${e.clientY - rect.top - size / 2}px`;
      btn.appendChild(ripple);
      setTimeout(() => ripple.remove(), 620);
    }
    onClick?.(e);
  }

  return (
    <button
      ref={ref}
      className={[styles.btn, styles[variant], className].filter(Boolean).join(' ')}
      onClick={handleClick}
      {...rest}
    >
      {icon}
      {children}
    </button>
  );
}
