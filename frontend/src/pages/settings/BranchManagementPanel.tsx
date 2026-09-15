import { FormEvent, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { BranchItem, branchesApi } from '../../api/branchesApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import settingsStyles from './SettingsPage.module.css';
import styles from './BranchManagementPanel.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

interface BranchFormState {
  name: string;
  address: string;
  city: string;
  timezone: string;
  tarbilBranchCode: string;
}

const EMPTY_FORM: BranchFormState = { name: '', address: '', city: '', timezone: 'Europe/Istanbul', tarbilBranchCode: '' };

export function BranchManagementPanel() {
  const [branches, setBranches] = useState<BranchItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<BranchFormState>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const initialFormRef = useRef<BranchFormState>(form);

  function load() {
    setLoading(true);
    branchesApi
      .list()
      .then(setBranches)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  function openCreate() {
    setEditingId(null);
    setForm(EMPTY_FORM);
    initialFormRef.current = EMPTY_FORM;
    setModalOpen(true);
  }

  function openEdit(b: BranchItem) {
    setEditingId(b.branchId);
    const initial: BranchFormState = {
      name: b.branchName,
      address: b.address ?? '',
      city: b.city ?? '',
      timezone: b.timezone ?? 'Europe/Istanbul',
      tarbilBranchCode: b.tarbilBranchCode ?? '',
    };
    setForm(initial);
    initialFormRef.current = initial;
    setModalOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      if (editingId) {
        await branchesApi.update(editingId, {
          address: form.address,
          city: form.city,
          timezone: form.timezone,
          tarbilBranchCode: form.tarbilBranchCode || undefined,
        });
      } else {
        await branchesApi.create({
          name: form.name,
          address: form.address || undefined,
          city: form.city || undefined,
          timezone: form.timezone || undefined,
          tarbilBranchCode: form.tarbilBranchCode || undefined,
        });
      }
      setModalOpen(false);
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <div className={styles.actionsRow}>
        <Button variant="primary" onClick={openCreate}>
          + Yeni Şube
        </Button>
      </div>

      {error && <div className={settingsStyles.errorBanner}>{error}</div>}

      <div className={settingsStyles.tableCard}>
        <div className={styles.tableHead}>
          <div>Şube Adı</div>
          <div>Şehir</div>
          <div>Adres</div>
          <div>Saat Dilimi</div>
          <div></div>
        </div>
        {loading ? (
          <div className={settingsStyles.empty}>Yükleniyor...</div>
        ) : branches.length === 0 ? (
          <div className={settingsStyles.empty}>Henüz şube eklenmedi</div>
        ) : (
          branches.map((b) => (
            <div key={b.branchId} className={styles.tableRow}>
              <div>{b.branchName}</div>
              <div className={settingsStyles.muted}>{b.city || '—'}</div>
              <div className={settingsStyles.muted}>{b.address || '—'}</div>
              <div className={settingsStyles.muted}>{b.timezone || '—'}</div>
              <div className={styles.rowActions}>
                <Button variant="secondary" onClick={() => openEdit(b)}>
                  Düzenle
                </Button>
              </div>
            </div>
          ))
        )}
      </div>

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        width={480}
        dirty={JSON.stringify(form) !== JSON.stringify(initialFormRef.current)}
      >
        <form onSubmit={handleSubmit}>
          <div className={styles.modalTitle}>{editingId ? 'Şubeyi Düzenle' : 'Yeni Şube'}</div>
          <FieldWrap label="Şube adı">
            <Input
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              disabled={!!editingId}
              required={!editingId}
            />
          </FieldWrap>
          <FieldWrap label="Şehir">
            <Input
              value={form.city}
              onChange={(e) => setForm((f) => ({ ...f, city: e.target.value }))}
              required={!!editingId}
            />
          </FieldWrap>
          <FieldWrap label="Adres">
            <Input
              value={form.address}
              onChange={(e) => setForm((f) => ({ ...f, address: e.target.value }))}
              required={!!editingId}
            />
          </FieldWrap>
          <FieldWrap label="Saat dilimi">
            <Input
              value={form.timezone}
              onChange={(e) => setForm((f) => ({ ...f, timezone: e.target.value }))}
              placeholder="Europe/Istanbul"
              required={!!editingId}
            />
          </FieldWrap>
          <FieldWrap label="TARBİL Şube Kodu">
            <Input value={form.tarbilBranchCode} onChange={(e) => setForm((f) => ({ ...f, tarbilBranchCode: e.target.value }))} />
          </FieldWrap>
          <div className={styles.modalActions}>
            <Button type="button" variant="secondary" onClick={() => setModalOpen(false)}>
              Vazgeç
            </Button>
            <Button type="submit" variant="primary" disabled={saving}>
              {saving ? 'Kaydediliyor...' : 'Kaydet'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
