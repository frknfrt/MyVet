import { Fragment, useId, type ReactNode } from 'react';
import { PrivacyNoticeLink } from './PrivacyNoticeLink';
import { fillPlaceholders } from './bindings';
import type { FormItem, FormTemplateContent, FormValue, FormValues } from './formModel';
import styles from './FormRenderer.module.css';

export type FormRenderMode = 'preview' | 'fill' | 'readonly';

interface FormRendererProps {
  content: FormTemplateContent;
  values: FormValues;
  onChange?: (code: string, value: FormValue) => void;
  mode: FormRenderMode;
  /** SIGNATURE yerine konur; yoksa "İmza alanı" kutusu. */
  signatureSlot?: ReactNode;
}

const PLACEHOLDER_SPLIT = /(\{\{\s*[a-z0-9_]+\s*\}\})/;

function asText(v: FormValue | undefined): string {
  if (v === undefined) return '';
  return Array.isArray(v) ? v.join(', ') : v;
}

function asList(v: FormValue | undefined): string[] {
  if (Array.isArray(v)) return v;
  return v ? [v] : [];
}

/** Paragraf: {{kod}} yer tutucusu dolu ise kalin deger, bos ise altı çizili boşluk. */
function TextBlock({ text, values, show }: { text: string; values: FormValues; show: boolean }) {
  if (!text.trim()) return null;
  return (
    <p className={styles.paragraph}>
      {text.split(PLACEHOLDER_SPLIT).map((part, i) => {
        if (!PLACEHOLDER_SPLIT.test(part)) return <Fragment key={i}>{part}</Fragment>;
        const value = show ? fillPlaceholders(part, values) : '';
        return value ? (
          <strong key={i}>{value}</strong>
        ) : (
          <span key={i} className={styles.blank} data-blank="">
            {' '}
          </span>
        );
      })}
    </p>
  );
}

function FieldRow({ item, values, onChange, mode }: {
  item: FormItem;
  values: FormValues;
  onChange?: (code: string, value: FormValue) => void;
  mode: FormRenderMode;
}) {
  const uid = useId();
  const code = item.code ?? '';
  const value = values[code];
  const label = (
    <>
      {item.label}
      {item.required && mode !== 'readonly' && <span className={styles.required}> *</span>}
    </>
  );

  if (mode === 'readonly') {
    const text = asText(value);
    return (
      <div className={styles.field}>
        <div className={styles.label}>{item.label}</div>
        <div className={styles.readValue}>{text ? (item.type === 'NUMBER' && item.unit ? `${text} ${item.unit}` : text) : '—'}</div>
      </div>
    );
  }

  const disabled = mode === 'preview';
  const set = (v: FormValue) => onChange?.(code, v);

  if (item.type === 'CHOICE') {
    const selected = asList(value);
    return (
      <fieldset className={styles.choiceGroup}>
        <legend className={styles.label}>{label}</legend>
        {(item.options ?? []).map((opt) => (
          <label key={opt} className={styles.choice}>
            <input
              type={item.multiple ? 'checkbox' : 'radio'}
              name={`${uid}-${code}`}
              disabled={disabled}
              checked={selected.includes(opt)}
              onChange={() => {
                if (!item.multiple) set(opt);
                else set(selected.includes(opt) ? selected.filter((o) => o !== opt) : [...selected, opt]);
              }}
            />
            <span>{opt}</span>
          </label>
        ))}
      </fieldset>
    );
  }

  const id = `${uid}-${code}`;
  const text = asText(value);
  let control: ReactNode;
  if (item.type === 'LONG_TEXT') {
    control = <textarea id={id} className={styles.textarea} rows={3} disabled={disabled} value={text} onChange={(e) => set(e.target.value)} />;
  } else if (item.type === 'NUMBER') {
    control = (
      <div className={styles.numberWrap}>
        <input id={id} className={styles.input} inputMode="decimal" disabled={disabled} value={text} onChange={(e) => set(e.target.value)} />
        {item.unit && <span className={styles.unit}>{item.unit}</span>}
      </div>
    );
  } else {
    const type = item.type === 'DATE' ? 'date' : item.type === 'PHONE' ? 'tel' : 'text';
    control = <input id={id} type={type} className={styles.input} disabled={disabled} value={text} onChange={(e) => set(e.target.value)} />;
  }

  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      {control}
    </div>
  );
}

export function FormRenderer({ content, values, onChange, mode, signatureSlot }: FormRendererProps) {
  return (
    <div className={styles.form}>
      <h2 className={styles.title}>{content.title || 'Form başlığı'}</h2>
      {content.sections.map((section, si) => (
        <section key={si} className={styles.section}>
          {section.title && <h3 className={styles.sectionTitle}>{section.title}</h3>}
          {section.items.map((item, ii) => {
            if (item.type === 'TEXT_BLOCK') {
              return <TextBlock key={ii} text={item.text ?? ''} values={values} show={mode !== 'preview'} />;
            }
            if (item.type === 'SIGNATURE') {
              return (
                <div key={item.code ?? `signature-${ii}`} className={styles.signature}>
                  {mode === 'readonly' && content.privacyNotice && <section aria-label="İmzalı KVKK ve iletişim tercihleri">
                    <PrivacyNoticeLink text={content.privacyNotice.text} version={content.privacyNotice.version} />
                    <p>✓ {content.privacyNotice.statement}</p>
                    <p>Hizmet bildirimleri: SMS {content.privacyNotice.sms ? 'Evet' : 'Hayır'} · WhatsApp {content.privacyNotice.whatsapp ? 'Evet' : 'Hayır'} · E-posta {content.privacyNotice.email ? 'Evet' : 'Hayır'}</p>
                  </section>}
                  {signatureSlot ?? <div className={styles.signatureBox}>İmza alanı</div>}
                </div>
              );
            }
            return <FieldRow key={item.code ?? `field-${ii}`} item={item} values={values} onChange={onChange} mode={mode} />;
          })}
        </section>
      ))}
    </div>
  );
}
