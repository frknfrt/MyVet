import { useRef, useState } from 'react';
import { ApiError } from '../../../api/client';
import { formsApi, type FormTemplateDraft } from '../../../api/formsApi';
import { Button } from '../../../components/ui/Button';
import { fallbackContent } from '../../../forms/formEdit';
import type { FormTemplateContent } from '../../../forms/formModel';
import { prepareImage } from '../../../lib/prepareImage';
import { FormTemplateEditor } from './FormTemplateEditor';
import styles from './NewFormFromImage.module.css';

interface Props {
  onSaved: (id: string) => void;
  onCancel: () => void;
}

interface Draft {
  content: FormTemplateContent;
  sourceImageId: string;
  warnings: string[];
  /** true: gercek AI icerigi (mavi cerceve); false: bos yedek sablon. */
  fromAi: boolean;
}

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

/** Taslaktan duzenleyici icerigi; AI yoksa/okuyamadiysa bos sablon + aciklayici uyari. */
function toDraft(sourceImageId: string, draft: FormTemplateDraft): Draft {
  if (draft.content && draft.modelConnected) {
    return { content: draft.content, sourceImageId, warnings: draft.warnings, fromAi: true };
  }
  const reason = draft.modelConnected
    ? 'Formdan metin okunamadı; boş bir şablon açıldı. Formu elle doldurabilirsiniz.'
    : 'Yapay zekâ modeli bağlı değil; boş bir şablon açıldı. Formu elle doldurabilirsiniz.';
  return { content: fallbackContent(), sourceImageId, warnings: [reason, ...draft.warnings], fromAi: false };
}

export function NewFormFromImage({ onSaved, onCancel }: Props) {
  const cameraRef = useRef<HTMLInputElement>(null);
  const galleryRef = useRef<HTMLInputElement>(null);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);

  async function handleFile(file: File | undefined) {
    if (!file) return;
    setWorking(true);
    setError(null);
    try {
      const prepared = await prepareImage(file, 'document');
      const sourceImageId = await formsApi.uploadSourceImage(prepared);
      const result = await formsApi.extractDraft(sourceImageId);
      setDraft(toDraft(sourceImageId, result));
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setWorking(false);
    }
  }

  if (draft) {
    return (
      <FormTemplateEditor
        initialContent={draft.content}
        sourceImageId={draft.sourceImageId}
        templateId={null}
        isDraft={draft.fromAi}
        warnings={draft.warnings}
        onSaved={onSaved}
        onCancel={onCancel}
      />
    );
  }

  return (
    <div className={styles.wrap}>
      <h2 className={styles.title}>Yeni form</h2>
      <p className={styles.lead}>
        Mevcut kağıt onam formunuzun fotoğrafını çekin ya da yükleyin. Yapay zekâ bir taslak çıkarır; siz düzenleyip onaylarsınız.
      </p>

      {error && <div className={styles.error}>{error}</div>}

      {working ? (
        <div className={styles.working} role="status">
          <span className={styles.spinner} aria-hidden="true" />
          Form okunuyor…
        </div>
      ) : (
        <div className={styles.actions}>
          <Button type="button" variant="ai" onClick={() => cameraRef.current?.click()}>
            📷 Fotoğraf Çek
          </Button>
          <Button type="button" variant="secondary" onClick={() => galleryRef.current?.click()}>
            🖼 Görsel Yükle
          </Button>
          <Button type="button" variant="tertiary" onClick={onCancel}>
            Vazgeç
          </Button>
        </div>
      )}

      <input
        ref={cameraRef}
        type="file"
        accept="image/*"
        capture="environment"
        hidden
        onChange={(e) => {
          void handleFile(e.target.files?.[0]);
          e.target.value = '';
        }}
      />
      <input
        ref={galleryRef}
        type="file"
        accept="image/*"
        hidden
        onChange={(e) => {
          void handleFile(e.target.files?.[0]);
          e.target.value = '';
        }}
      />
    </div>
  );
}
