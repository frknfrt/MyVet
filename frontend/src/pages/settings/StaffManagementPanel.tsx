import { FormEvent, useEffect, useState } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { StaffRole } from '../../auth/session';
import { ApiError } from '../../api/client';
import { BranchItem, branchesApi } from '../../api/branchesApi';
import { CreateStaffUserPayload, StaffUserItem, UpdateStaffUserPayload, staffUsersApi } from '../../api/staffUsersApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select, Textarea } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import settingsStyles from './SettingsPage.module.css';
import styles from './StaffManagementPanel.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

const ROLE_LABELS: Record<StaffRole, string> = {
  VET: 'Veteriner Hekim',
  TECHNICIAN: 'Teknisyen',
  RECEPTIONIST: 'Resepsiyonist',
  ADMIN: 'Yönetici',
  OWNER_ACCOUNT: 'Sahip Hesabı',
};

const ASSIGNABLE_ROLES: StaffRole[] = ['VET', 'TECHNICIAN', 'RECEPTIONIST', 'ADMIN'];

interface StaffFormState {
  branchId: string;
  fullName: string;
  email: string;
  password: string;
  role: StaffRole;
  phone: string;
  licenseNumber: string;
  specialty: string;
  bio: string;
}

function emptyForm(defaultBranchId: string): StaffFormState {
  return {
    branchId: defaultBranchId,
    fullName: '',
    email: '',
    password: '',
    role: 'VET',
    phone: '',
    licenseNumber: '',
    specialty: '',
    bio: '',
  };
}

export function StaffManagementPanel() {
  const { session } = useAuth();
  const [staff, setStaff] = useState<StaffUserItem[]>([]);
  const [branches, setBranches] = useState<BranchItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<StaffFormState>(emptyForm(session?.branchId ?? ''));
  const [saving, setSaving] = useState(false);

  function load() {
    setLoading(true);
    Promise.all([staffUsersApi.list(), branchesApi.list()])
      .then(([staffList, branchList]) => {
        setStaff(staffList);
        setBranches(branchList);
      })
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  function openCreate() {
    setEditingId(null);
    setForm(emptyForm(session?.branchId ?? ''));
    setModalOpen(true);
  }

  function openEdit(s: StaffUserItem) {
    setEditingId(s.id);
    setForm({
      branchId: s.branchId,
      fullName: s.fullName,
      email: s.email,
      password: '',
      role: s.role,
      phone: s.phone ?? '',
      licenseNumber: s.licenseNumber ?? '',
      specialty: s.specialty ?? '',
      bio: s.bio ?? '',
    });
    setModalOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      if (editingId) {
        const payload: UpdateStaffUserPayload = {
          fullName: form.fullName,
          phone: form.phone || undefined,
          role: form.role,
          licenseNumber: form.licenseNumber || undefined,
          specialty: form.specialty || undefined,
          bio: form.bio || undefined,
        };
        await staffUsersApi.update(editingId, payload);
      } else {
        const payload: CreateStaffUserPayload = {
          branchId: form.branchId,
          fullName: form.fullName,
          email: form.email,
          password: form.password,
          role: form.role,
          phone: form.phone || undefined,
          licenseNumber: form.licenseNumber || undefined,
          specialty: form.specialty || undefined,
          bio: form.bio || undefined,
        };
        await staffUsersApi.create(payload);
      }
      setModalOpen(false);
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setSaving(false);
    }
  }

  async function handleToggleActive(s: StaffUserItem) {
    try {
      if (s.active) await staffUsersApi.deactivate(s.id);
      else await staffUsersApi.activate(s.id);
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    }
  }

  const isSelf = editingId !== null && editingId === session?.staffUserId;

  return (
    <div>
      <div className={styles.actionsRow}>
        <Button variant="primary" onClick={openCreate}>
          + Yeni Kullanıcı
        </Button>
      </div>

      {error && <div className={settingsStyles.errorBanner}>{error}</div>}

      <div className={settingsStyles.tableCard}>
        <div className={styles.tableHead}>
          <div>Ad Soyad</div>
          <div>E-posta</div>
          <div>Rol</div>
          <div>Uzmanlık</div>
          <div>Durum</div>
          <div></div>
        </div>
        {loading ? (
          <div className={settingsStyles.empty}>Yükleniyor...</div>
        ) : staff.length === 0 ? (
          <div className={settingsStyles.empty}>Henüz personel eklenmedi</div>
        ) : (
          staff.map((s) => (
            <div key={s.id} className={styles.tableRow}>
              <div>{s.fullName}</div>
              <div className={settingsStyles.muted}>{s.email}</div>
              <div>{ROLE_LABELS[s.role]}</div>
              <div className={settingsStyles.muted}>{s.role === 'VET' ? s.specialty || '—' : '—'}</div>
              <div>
                <Badge tone={s.active ? 'success' : 'neutral'}>{s.active ? 'Aktif' : 'Pasif'}</Badge>
              </div>
              <div className={styles.rowActions}>
                <Button variant="secondary" onClick={() => openEdit(s)}>
                  Düzenle
                </Button>
                {s.id !== session?.staffUserId && (
                  <Button variant={s.active ? 'danger' : 'secondary'} onClick={() => handleToggleActive(s)}>
                    {s.active ? 'Pasifleştir' : 'Aktifleştir'}
                  </Button>
                )}
              </div>
            </div>
          ))
        )}
      </div>

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} width={560}>
        <form onSubmit={handleSubmit}>
          <div className={styles.modalTitle}>{editingId ? 'Kullanıcıyı Düzenle' : 'Yeni Kullanıcı'}</div>

          <div className={styles.formGrid}>
            <FieldWrap label="Ad Soyad">
              <Input value={form.fullName} onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))} required />
            </FieldWrap>
            <FieldWrap label="E-posta">
              <Input
                type="email"
                value={form.email}
                onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
                disabled={!!editingId}
                required={!editingId}
              />
            </FieldWrap>

            {!editingId && (
              <>
                <FieldWrap label="Şifre">
                  <Input
                    type="password"
                    value={form.password}
                    onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))}
                    minLength={8}
                    required
                  />
                </FieldWrap>
                <FieldWrap label="Şube">
                  <Select value={form.branchId} onChange={(e) => setForm((f) => ({ ...f, branchId: e.target.value }))} required>
                    {branches.map((b) => (
                      <option key={b.branchId} value={b.branchId}>
                        {b.branchName}
                      </option>
                    ))}
                  </Select>
                </FieldWrap>
              </>
            )}

            <FieldWrap label="Telefon">
              <Input value={form.phone} onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))} />
            </FieldWrap>
            <FieldWrap label="Rol">
              <Select
                value={form.role}
                onChange={(e) => setForm((f) => ({ ...f, role: e.target.value as StaffRole }))}
                disabled={isSelf}
              >
                {ASSIGNABLE_ROLES.map((r) => (
                  <option key={r} value={r}>
                    {ROLE_LABELS[r]}
                  </option>
                ))}
              </Select>
            </FieldWrap>
          </div>

          {isSelf && <div className={styles.selfHint}>Kendi rolünüzü değiştiremez veya kendinizi pasifleştiremezsiniz.</div>}

          {form.role === 'VET' && (
            <div className={styles.formGrid}>
              <FieldWrap label="Lisans No">
                <Input value={form.licenseNumber} onChange={(e) => setForm((f) => ({ ...f, licenseNumber: e.target.value }))} />
              </FieldWrap>
              <FieldWrap label="Uzmanlık Alanı">
                <Input value={form.specialty} onChange={(e) => setForm((f) => ({ ...f, specialty: e.target.value }))} />
              </FieldWrap>
              <div className={styles.formGridFull}>
                <FieldWrap label="Kısa Biyografi">
                  <Textarea rows={3} value={form.bio} onChange={(e) => setForm((f) => ({ ...f, bio: e.target.value }))} />
                </FieldWrap>
              </div>
            </div>
          )}

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
