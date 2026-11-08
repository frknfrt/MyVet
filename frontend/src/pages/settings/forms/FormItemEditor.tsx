import { FieldWrap, Input, Select, Textarea } from '../../../components/ui/Field';
import { supportsBinding, BINDING_LABELS, BINDINGS, type FormBinding, type FormItem, type FormItemType } from '../../../forms/formModel';
import { FILLABLE_TYPES, TYPE_LABELS, isFillable } from '../../../forms/formEdit';
import styles from './FormTemplateEditor.module.css';

interface Props {
  item: FormItem;
  /** Ayni bolumdeki alan kodlari (paragrafta {{kod}} icin ipucu). */
  sectionCodes: string[];
  canMoveUp: boolean;
  canMoveDown: boolean;
  onPatch: (patch: Partial<FormItem>) => void;
  onTypeChange: (type: FormItemType) => void;
  onMove: (delta: -1 | 1) => void;
  onRemove: () => void;
}

const SWITCHABLE: FormItemType[] = [...FILLABLE_TYPES, 'TEXT_BLOCK'];

export function FormItemEditor({ item, sectionCodes, canMoveUp, canMoveDown, onPatch, onTypeChange, onMove, onRemove }: Props) {
  const isBlock = item.type === 'TEXT_BLOCK';
  const isSignature = item.type === 'SIGNATURE';

  return (
    <div className={styles.item}>
      <div className={styles.itemHead}>
        {isSignature ? (
          <span className={styles.typeFixed}>{TYPE_LABELS.SIGNATURE}</span>
        ) : (
          <Select aria-label="Alan türü" value={item.type} onChange={(e) => onTypeChange(e.target.value as FormItemType)}>
            {SWITCHABLE.map((t) => (
              <option key={t} value={t}>
                {TYPE_LABELS[t]}
              </option>
            ))}
          </Select>
        )}
        {!isBlock && (
          <span className={styles.code}>
            Kod: <code data-code="">{item.code}</code>
          </span>
        )}
        <div className={styles.itemActions}>
          <button type="button" className={styles.iconBtn} aria-label="Yukarı taşı" disabled={!canMoveUp} onClick={() => onMove(-1)}>
            ↑
          </button>
          <button type="button" className={styles.iconBtn} aria-label="Aşağı taşı" disabled={!canMoveDown} onClick={() => onMove(1)}>
            ↓
          </button>
          <button type="button" className={`${styles.iconBtn} ${styles.danger}`} aria-label="Sil" onClick={onRemove}>
            Sil
          </button>
        </div>
      </div>

      {isBlock ? (
        <FieldWrap
          label="Paragraf metni"
          hint={
            sectionCodes.length > 0
              ? `Alan değerini metne eklemek için {{kod}} yazın. Bu bölümdeki kodlar: ${sectionCodes.join(', ')}`
              : undefined
          }
        >
          <Textarea rows={4} value={item.text ?? ''} onChange={(e) => onPatch({ text: e.target.value })} />
        </FieldWrap>
      ) : (
        <FieldWrap label={isSignature ? 'Etiket' : 'Alan adı'}>
          <Input value={item.label ?? ''} placeholder="Alan adı" onChange={(e) => onPatch({ label: e.target.value })} />
        </FieldWrap>
      )}

      {isFillable(item.type) && (
        <FieldWrap label="Kayıttan doldur">
          <Select
            aria-label="Bağlantı"
            value={item.binding ?? ''}
            onChange={(e) => onPatch({ binding: (e.target.value || null) as FormBinding | null })}
          >
            <option value="">Bağlantı yok</option>
            {BINDINGS.map((b) => (
              <option key={b} value={b} disabled={!supportsBinding(item.type, b)}>
                {BINDING_LABELS[b]}
              </option>
            ))}
          </Select>
        </FieldWrap>
      )}

      {item.type === 'NUMBER' && (
        <FieldWrap label="Birim (isteğe bağlı)">
          <Input value={item.unit ?? ''} placeholder="kg" onChange={(e) => onPatch({ unit: e.target.value })} />
        </FieldWrap>
      )}

      {item.type === 'CHOICE' && (
        <>
          <FieldWrap label="Seçenekler (her satıra bir seçenek)">
            <Textarea
              rows={3}
              value={(item.options ?? []).join('\n')}
              onChange={(e) => onPatch({ options: e.target.value.split('\n') })}
            />
          </FieldWrap>
          <label className={styles.check}>
            <input type="checkbox" checked={item.multiple} onChange={(e) => onPatch({ multiple: e.target.checked })} />
            Birden fazla seçilebilsin
          </label>
        </>
      )}

      {isFillable(item.type) && (
        <label className={styles.check}>
          <input type="checkbox" checked={item.required} onChange={(e) => onPatch({ required: e.target.checked })} />
          Zorunlu
        </label>
      )}
    </div>
  );
}
