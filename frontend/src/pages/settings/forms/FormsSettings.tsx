import { useEffect, useState } from 'react';
import { Route, Routes, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../../api/client';
import { formsApi, type FormTemplateDetail } from '../../../api/formsApi';
import { PrivacyNoticeSettings } from './PrivacyNoticeSettings';
import { FormTemplateEditor } from './FormTemplateEditor';
import { FormTemplateList } from './FormTemplateList';
import { NewFormFromImage } from './NewFormFromImage';
import styles from '../SettingsPage.module.css';

const LIST_PATH = '/ayarlar/formlar';

function EditTemplate() {
  const { templateId } = useParams<{ templateId: string }>();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<FormTemplateDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!templateId) return;
    formsApi
      .getTemplate(templateId)
      .then(setDetail)
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Şablon yüklenemedi'));
  }, [templateId]);

  if (error) return <div className={styles.errorBanner}>{error}</div>;
  if (!detail) return <div>Yükleniyor…</div>;
  return (
    <FormTemplateEditor
      initialContent={detail.content}
      sourceImageId={detail.sourceImageId}
      templateId={detail.id}
      isDraft={false}
      warnings={[]}
      onSaved={() => navigate(LIST_PATH)}
      onCancel={() => navigate(LIST_PATH)}
    />
  );
}

/** Ayarlar > Formlar: liste, yeni (görselden) ve düzenleme ekranları. */
export function FormsSettings() {
  const navigate = useNavigate();
  return (
    <Routes>
      <Route index element={<FormTemplateList />} />
      <Route path="kvkk" element={<PrivacyNoticeSettings />} />
      <Route path="yeni" element={<NewFormFromImage onSaved={() => navigate(LIST_PATH)} onCancel={() => navigate(LIST_PATH)} />} />
      <Route path=":templateId" element={<EditTemplate />} />
    </Routes>
  );
}
