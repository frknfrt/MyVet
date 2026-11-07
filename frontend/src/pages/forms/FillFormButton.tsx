import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { formsApi, type FormTemplateSummary } from '../../api/formsApi';
import { useAuth } from '../../auth/AuthContext';
import { Button } from '../../components/ui/Button';
import { FORM_ROLES } from './formRoles';
import styles from './FillFormButton.module.css';

interface FillFormButtonProps {
  patientId: string;
  encounterId?: string;
}

export function FillFormButton({ patientId, encounterId }: FillFormButtonProps) {
  const { session } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [templates, setTemplates] = useState<FormTemplateSummary[] | null>(null);
  const [error, setError] = useState(false);

  if (!session || !FORM_ROLES.includes(session.role)) return null;

  function toggle() {
    const next = !open;
    setOpen(next);
    if (next) {
      setError(false);
      formsApi
        .listTemplates('ACTIVE')
        .then(setTemplates)
        .catch(() => setError(true));
    }
  }

  function pick(templateId: string) {
    const qs = new URLSearchParams({ templateId, patientId });
    if (encounterId) qs.set('encounterId', encounterId);
    setOpen(false);
    navigate(`/formlar/doldur?${qs.toString()}`);
  }

  return (
    <div className={styles.wrap}>
      <Button variant="secondary" onClick={toggle} aria-haspopup="menu" aria-expanded={open}>
        📝 Form Doldur
      </Button>
      {open && (
        <div className={styles.menu} role="menu">
          {error ? (
            <div className={styles.note}>Formlar yüklenemedi</div>
          ) : templates === null ? (
            <div className={styles.note}>Yükleniyor...</div>
          ) : templates.length === 0 ? (
            <div className={styles.note}>
              Henüz form yok — Ayarlar &gt; Formlar'dan ekleyin
              {session.role === 'ADMIN' && (
                <>
                  {' '}
                  <Link to="/ayarlar/formlar">Formlara git</Link>
                </>
              )}
            </div>
          ) : (
            templates.map((t) => (
              <button key={t.id} type="button" role="menuitem" className={styles.item} onClick={() => pick(t.id)}>
                {t.title}
              </button>
            ))
          )}
        </div>
      )}
    </div>
  );
}
