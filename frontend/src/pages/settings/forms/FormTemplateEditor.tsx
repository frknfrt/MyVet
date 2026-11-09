import { useEffect, useMemo, useRef, useState } from 'react';
import { ApiError } from '../../../api/client';
import { formsApi } from '../../../api/formsApi';
import { Button } from '../../../components/ui/Button';
import { FieldWrap, Input } from '../../../components/ui/Field';
import { FormRenderer } from '../../../forms/FormRenderer';
import {
  allCodes,
  cleanContent,
  hasSignature,
  newItem,
  slugify,
  TYPE_LABELS,
  uniqueCode,
  validateContent,
} from '../../../forms/formEdit';
import { supportsBinding, type FormItem, type FormItemType, type FormTemplateContent } from '../../../forms/formModel';
import { FormItemEditor } from './FormItemEditor';
import { openProtectedFile } from './openProtectedFile';
import styles from './FormTemplateEditor.module.css';

interface Props {
  initialContent: FormTemplateContent;
  sourceImageId: string | null;
  /** null: yeni sablon (createTemplate); dolu: kayitli sablon (updateTemplate). */
  templateId: string | null;
  /** true: AI taslagi, henuz kaydedilmedi -- mavi cerceve. */
  isDraft: boolean;
  warnings: string[];
  onSaved: (id: string) => void;
  onCancel: () => void;
}

const LEAVE_MESSAGE = 'Kaydedilmemiş değişiklikleriniz var. Çıkmak istediğinize emin misiniz?';
const ADDABLE: FormItemType[] = ['SHORT_TEXT', 'LONG_TEXT', 'DATE', 'PHONE', 'NUMBER', 'CHOICE', 'TEXT_BLOCK', 'SIGNATURE'];

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

/** Referans gorsel yetkili istek gerektirir: blob'a cevirip object URL uretir. */
function useReferenceImage(sourceImageId: string | null): string | null {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!sourceImageId) return;
    let objectUrl: string | null = null;
    let cancelled = false;
    formsApi
      .fetchFile(formsApi.sourceImageUrl(sourceImageId))
      .then((res) => (res.ok ? res.blob() : Promise.reject(new Error('görsel alınamadı'))))
      .then((blob) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [sourceImageId]);
  return url;
}

export function FormTemplateEditor({ initialContent, sourceImageId, templateId, isDraft, warnings, onSaved, onCancel }: Props) {
  const [content, setContent] = useState<FormTemplateContent>(initialContent);
  const [menuSection, setMenuSection] = useState<number | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [errors, setErrors] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const initialJson = useRef(JSON.stringify(initialContent));
  // Kodu etiketten turetilebilen (henuz kaydedilmemis) alanlar. Kayitli sablondaki ve imza kodlari donuk.
  const autoCodes = useRef<Set<string>>(
    new Set(
      templateId === null
        ? initialContent.sections.flatMap((s) => s.items.filter((i) => i.code && i.type !== 'SIGNATURE' && i.type !== 'TEXT_BLOCK').map((i) => i.code as string))
        : [],
    ),
  );
  const imageUrl = useReferenceImage(sourceImageId);
  // View-only identities survive immutable edits without changing persisted JSON.
  const viewKeys = useRef(new WeakMap<object, string>());
  const viewKeySequence = useRef(0);
  function viewKey(value: object): string {
    let key = viewKeys.current.get(value);
    if (!key) { key = `form-view-${++viewKeySequence.current}`; viewKeys.current.set(value, key); }
    return key;
  }


  // Yeni (kaydedilmemis) sablon, icerik degismese de kaybolacak bir taslaktir.
  const dirty = !busy && (templateId === null || JSON.stringify(content) !== initialJson.current);
  useEffect(() => {
    if (!dirty) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '';
    };
    const guardLink = (e: MouseEvent) => {
      if (e.defaultPrevented || e.button !== 0 || e.ctrlKey || e.metaKey || e.shiftKey || e.altKey) return;
      const anchor = e.target instanceof Element ? e.target.closest<HTMLAnchorElement>('a[href]') : null;
      if (!anchor || anchor.target === '_blank' || anchor.hasAttribute('download')) return;
      const destination = new URL(anchor.href, window.location.href);
      if (destination.origin !== window.location.origin) return; // External navigation uses beforeunload.
      if (destination.pathname === window.location.pathname && destination.search === window.location.search) return;
      if (!window.confirm(LEAVE_MESSAGE)) { e.preventDefault(); e.stopPropagation(); }
    };
    window.addEventListener('beforeunload', handler);
    document.addEventListener('click', guardLink, true);
    return () => {
      window.removeEventListener('beforeunload', handler);
      document.removeEventListener('click', guardLink, true);
    };
  }, [dirty]);

  const cleaned = useMemo(() => cleanContent(content), [content]);

  function mutate(fn: (draft: FormTemplateContent) => FormTemplateContent) {
    setContent(current => {
      current.sections.forEach(section => { viewKey(section); section.items.forEach(viewKey); });
      const next = fn(current);
      next.sections.forEach((section, si) => {
        const previous = current.sections[si];
        if (!viewKeys.current.has(section) && previous && current.sections.length === next.sections.length) {
          viewKeys.current.set(section, viewKey(previous));
        }
        section.items.forEach((item, ii) => {
          if (!viewKeys.current.has(item) && previous?.items.length === section.items.length && previous.items[ii]) {
            viewKeys.current.set(item, viewKey(previous.items[ii]));
          }
        });
      });
      return next;
    });
    setErrors([]);
    setNotice(null);
  }

  function patchItem(si: number, ii: number, patch: Partial<FormItem>) {
    mutate((c) => {
      const section = c.sections[si];
      const old = section.items[ii];
      let next: FormItem = { ...old, ...patch };
      let items = section.items;
      // Etiket degisince kod yeniden uretilir; paragraflardaki {{eskiKod}} da guncellenir.
      if (patch.label !== undefined && old.type !== 'TEXT_BLOCK' && old.type !== 'SIGNATURE' && old.code && autoCodes.current.has(old.code)) {
        const code = uniqueCode(slugify(patch.label ?? ''), allCodes(c, old));
        autoCodes.current.delete(old.code);
        autoCodes.current.add(code);
        next = { ...next, code };
        if (old.code && old.code !== code) {
          const re = new RegExp(`\\{\\{\\s*${old.code}\\s*\\}\\}`, 'g');
          items = items.map((it) => (it.type === 'TEXT_BLOCK' && it.text ? { ...it, text: it.text.replace(re, `{{${code}}}`) } : it));
        }
      }
      items = items.map((it, k) => (k === ii ? next : it));
      return { ...c, sections: c.sections.map((s, k) => (k === si ? { ...s, items } : s)) };
    });
  }

  function changeType(si: number, ii: number, type: FormItemType) {
    mutate((c) => {
      const old = c.sections[si].items[ii];
      const block = type === 'TEXT_BLOCK';
      let code = block ? null : old.code;
      if (!block && !code) {
        code = uniqueCode(slugify(old.label ?? ''), allCodes(c, old));
        autoCodes.current.add(code);
      }
      // Tür değişince yalnız uyumlu veri bağlantısını koru
      const keepBinding = old.binding !== null && supportsBinding(type, old.binding);
      const next: FormItem = {
        ...old,
        type,
        code,
        text: block ? old.text ?? '' : null,
        label: block ? null : old.label ?? '',
        required: block ? false : old.required,
        binding: keepBinding ? old.binding : null,
        unit: type === 'NUMBER' ? old.unit : null,
        options: type === 'CHOICE' ? old.options ?? ['Evet', 'Hayır'] : null,
        multiple: type === 'CHOICE' ? old.multiple : false,
      };
      return { ...c, sections: c.sections.map((s, k) => (k === si ? { ...s, items: s.items.map((it, j) => (j === ii ? next : it)) } : s)) };
    });
  }

  function moveItem(si: number, ii: number, delta: -1 | 1) {
    mutate((c) => {
      const items = [...c.sections[si].items];
      const j = ii + delta;
      if (j < 0 || j >= items.length) return c;
      [items[ii], items[j]] = [items[j], items[ii]];
      return { ...c, sections: c.sections.map((s, k) => (k === si ? { ...s, items } : s)) };
    });
  }

  function removeItem(si: number, ii: number) {
    mutate((c) => ({
      ...c,
      sections: c.sections.map((s, k) => (k === si ? { ...s, items: s.items.filter((_, j) => j !== ii) } : s)),
    }));
  }

  function addItem(si: number, type: FormItemType) {
    setMenuSection(null);
    if (type === 'SIGNATURE' && hasSignature(content)) {
      setNotice('Formda zaten bir imza alanı var');
      return;
    }
    // Imza kutusu bolumun sonunda kalir: yeni alan onun onune girer.
    mutate((c) => ({
      ...c,
      sections: c.sections.map((s, k) => {
        if (k !== si) return s;
        const created = newItem(type, c);
        if (created.code && type !== 'SIGNATURE') autoCodes.current.add(created.code);
        const last = s.items[s.items.length - 1];
        const items =
          type !== 'SIGNATURE' && last?.type === 'SIGNATURE'
            ? [...s.items.slice(0, -1), created, last]
            : [...s.items, created];
        return { ...s, items };
      }),
    }));
  }

  async function save() {
    const problems = validateContent(content);
    if (problems.length > 0) {
      setErrors(problems);
      return;
    }
    setBusy(true);
    setErrors([]);
    try {
      if (templateId) {
        await formsApi.updateTemplate(templateId, cleaned);
        onSaved(templateId);
      } else {
        const id = await formsApi.createTemplate({ content: cleaned, sourceImageId });
        onSaved(id);
      }
    } catch (err) {
      setErrors([errorMessageOf(err)]);
      setBusy(false);
    }
  }

  function cancel() {
    if (dirty && !window.confirm(LEAVE_MESSAGE)) return;
    onCancel();
  }

  const editor = (
    <div className={styles.editorCol}>
      <FieldWrap label="Form başlığı">
        <Input value={content.title} placeholder="Örn. Anestezi ve Ameliyat Onam Formu" onChange={(e) => mutate((c) => ({ ...c, title: e.target.value }))} />
      </FieldWrap>

      {content.sections.map((section, si) => (
        <div key={viewKey(section)} className={styles.section}>
          <FieldWrap label="Bölüm başlığı (isteğe bağlı)">
            <Input
              value={section.title ?? ''}
              onChange={(e) => mutate((c) => ({ ...c, sections: c.sections.map((s, k) => (k === si ? { ...s, title: e.target.value } : s)) }))}
            />
          </FieldWrap>
          {section.items.map((item, ii) => (
            <FormItemEditor
              key={viewKey(item)}
              item={item}
              sectionCodes={section.items.filter((i) => i.type !== 'TEXT_BLOCK' && i.code).map((i) => i.code as string)}
              canMoveUp={ii > 0}
              canMoveDown={ii < section.items.length - 1}
              onPatch={(p) => patchItem(si, ii, p)}
              onTypeChange={(t) => changeType(si, ii, t)}
              onMove={(d) => moveItem(si, ii, d)}
              onRemove={() => removeItem(si, ii)}
            />
          ))}
          <div className={styles.addWrap}>
            <Button type="button" variant="secondary" onClick={() => setMenuSection(menuSection === si ? null : si)}>
              + Alan ekle
            </Button>
            {menuSection === si && (
              <div className={styles.menu} role="menu">
                {ADDABLE.map((t) => (
                  <button key={t} type="button" role="menuitem" className={styles.menuItem} onClick={() => addItem(si, t)}>
                    {TYPE_LABELS[t]}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      ))}
    </div>
  );

  const body = (
    <div className={`${styles.layout} ${imageUrl ? styles.withImage : ''}`}>
      {imageUrl && (
        <div className={styles.imageCol}>
          <div className={styles.colTitle}>Referans görsel</div>
          <img src={imageUrl} alt="Referans form görseli" className={styles.image} />
        </div>
      )}
      {editor}
      <div className={styles.previewCol}>
        <div className={styles.colTitle}>Önizleme</div>
        <div className={styles.previewPaper}>
          <FormRenderer content={cleaned} values={{}} mode="preview" />
        </div>
        {templateId && (
          <Button type="button" variant="tertiary" onClick={() => openProtectedFile(formsApi.templatePdfUrl(templateId)).catch((e) => setErrors([errorMessageOf(e)]))}>
            Boş PDF önizleme
          </Button>
        )}
      </div>
    </div>
  );

  return (
    <div>
      <label style={{display:'block',marginBottom:16}}>
        <input type="checkbox" checked={!!content.privacyOptionsEnabled} onChange={e => mutate(c => ({...c,privacyOptionsEnabled:e.target.checked}))} />
        Bu forma KVKK aydınlatma ve SMS / WhatsApp / e-posta bildirim tercihleri bölümünü ekle
      </label>
      <p>Bu bölüm imza öncesinde gösterilir. İletişim tercihleri isteğe bağlıdır ve pazarlama izni değildir.</p>
      {isDraft ? (
        <div className={styles.aiFrame}>
          <div className={styles.aiTitle}>AI taslağı — onayınızı bekliyor</div>
          {warnings.length > 0 && (
            <ul className={styles.warnings}>
              {warnings.map((w, i) => (
                <li key={i}>{w}</li>
              ))}
            </ul>
          )}
          {body}
        </div>
      ) : (
        <>
          {warnings.length > 0 && (
            <ul className={styles.warnings}>
              {warnings.map((w, i) => (
                <li key={i}>{w}</li>
              ))}
            </ul>
          )}
          {body}
        </>
      )}

      {notice && <div className={styles.notice}>{notice}</div>}
      {errors.length > 0 && (
        <ul className={styles.errors} role="alert">
          {errors.map((e, i) => (
            <li key={i}>{e}</li>
          ))}
        </ul>
      )}

      <div className={styles.footer}>
        <Button type="button" variant="primary" disabled={busy} onClick={save}>
          {templateId ? 'Kaydet' : 'Formu Oluştur'}
        </Button>
        <Button type="button" variant="secondary" disabled={busy} onClick={cancel}>
          Vazgeç
        </Button>
      </div>
    </div>
  );
}
