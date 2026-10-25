import { forwardRef, InputHTMLAttributes, LabelHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react';
import styles from './Field.module.css';

/** Yapay zekâ önerisine giden serbest metin alanlarının altındaki uyarı (2026-10-25). */
export const AI_FIELD_HINT = 'Bu alan yapay zekâ önerisine gönderilir; sahip adı, telefon gibi kişisel bilgi yazmayın.';

interface FieldWrapProps {
  label?: string;
  /** Alanın altında gösterilen kısa açıklama. */
  hint?: string;
  children: ReactNode;
}

export function FieldWrap({ label, hint, children }: FieldWrapProps) {
  return (
    <div className={styles.field}>
      {label && <label className={styles.label}>{label}</label>}
      {children}
      {hint && <div className={styles.hint}>{hint}</div>}
    </div>
  );
}

export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={styles.input} {...props} />;
}

export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select className={styles.input} {...props} />;
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>((props, ref) => {
  return <textarea ref={ref} className={styles.textarea} {...props} />;
});
Textarea.displayName = 'Textarea';

export function Label(props: LabelHTMLAttributes<HTMLLabelElement>) {
  return <label className={styles.label} {...props} />;
}
