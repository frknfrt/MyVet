import { forwardRef, InputHTMLAttributes, LabelHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react';
import styles from './Field.module.css';

interface FieldWrapProps {
  label?: string;
  children: ReactNode;
}

export function FieldWrap({ label, children }: FieldWrapProps) {
  return (
    <div className={styles.field}>
      {label && <label className={styles.label}>{label}</label>}
      {children}
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
