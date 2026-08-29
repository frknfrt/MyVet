import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { CreateTenantPayload, platformAdminApi, TenantAdminOverview } from '../../api/platformAdminApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import styles from './PlatformAdminPages.module.css';
import { BILLING_STATUS_LABELS, BILLING_STATUS_TONES, TENANT_STATUS_LABELS, TENANT_STATUS_TONES } from './tenantBadges';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

const EMPTY_FORM: CreateTenantPayload = {
  tenantName: '',
  taxNumber: '',
  branchName: '',
  adminFullName: '',
  adminEmail: '',
  adminPassword: '',
};

export function TenantListPage() {
  const navigate = useNavigate();
  const [tenants, setTenants] = useState<TenantAdminOverview[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState<CreateTenantPayload>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    platformAdminApi
      .listTenants()
      .then(setTenants)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }, []);

  const filtered = tenants.filter((t) => t.name.toLowerCase().includes(query.trim().toLowerCase()));

  function openCreate() {
    setForm(EMPTY_FORM);
    setError(null);
    setModalOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const created = await platformAdminApi.createTenant(form);
      setModalOpen(false);
      navigate(`/platform-admin/tenants/${created.tenantId}`);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <div className={styles.title}>Kiracılar</div>

      <div className={styles.actionsRow}>
        <Button variant="primary" onClick={openCreate}>
          + Yeni Klinik
        </Button>
      </div>

      {error && <div className={styles.errorBanner}>{error}</div>}

      <div className={styles.searchRow}>
        <FieldWrap label="Ara">
          <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Klinik adı..." />
        </FieldWrap>
      </div>

      <div className={styles.tableCard}>
        <div className={styles.tableHead}>
          <div>Klinik Adı</div>
          <div>Plan</div>
          <div>Faturalama</div>
          <div>Durum</div>
          <div>Şube</div>
          <div>Personel</div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : filtered.length === 0 ? (
          <div className={styles.empty}>Kiracı bulunamadı</div>
        ) : (
          filtered.map((t) => (
            <div key={t.tenantId} className={styles.row} onClick={() => navigate(`/platform-admin/tenants/${t.tenantId}`)}>
              <div>{t.name}</div>
              <div className={styles.muted}>{t.planCode}</div>
              <div>
                <Badge tone={BILLING_STATUS_TONES[t.billingStatus]}>{BILLING_STATUS_LABELS[t.billingStatus]}</Badge>
              </div>
              <div>
                <Badge tone={TENANT_STATUS_TONES[t.status]}>{TENANT_STATUS_LABELS[t.status]}</Badge>
              </div>
              <div className={styles.muted}>{t.branchCount}</div>
              <div className={styles.muted}>{t.staffUserCount}</div>
            </div>
          ))
        )}
      </div>

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} width={480}>
        <form onSubmit={handleSubmit}>
          <div className={styles.modalTitle}>Yeni Klinik Oluştur</div>

          <FieldWrap label="Klinik adı">
            <Input
              value={form.tenantName}
              onChange={(e) => setForm((f) => ({ ...f, tenantName: e.target.value }))}
              required
            />
          </FieldWrap>
          <FieldWrap label="Vergi numarası">
            <Input value={form.taxNumber} onChange={(e) => setForm((f) => ({ ...f, taxNumber: e.target.value }))} />
          </FieldWrap>
          <FieldWrap label="Şube adı">
            <Input
              placeholder="Merkez Şube"
              value={form.branchName}
              onChange={(e) => setForm((f) => ({ ...f, branchName: e.target.value }))}
              required
            />
          </FieldWrap>
          <FieldWrap label="Yetkili adı soyadı">
            <Input
              value={form.adminFullName}
              onChange={(e) => setForm((f) => ({ ...f, adminFullName: e.target.value }))}
              required
            />
          </FieldWrap>
          <FieldWrap label="Yetkili e-postası">
            <Input
              type="email"
              value={form.adminEmail}
              onChange={(e) => setForm((f) => ({ ...f, adminEmail: e.target.value }))}
              required
            />
          </FieldWrap>
          <FieldWrap label="Geçici şifre">
            <Input
              type="password"
              minLength={8}
              placeholder="En az 8 karakter"
              value={form.adminPassword}
              onChange={(e) => setForm((f) => ({ ...f, adminPassword: e.target.value }))}
              required
            />
          </FieldWrap>

          <div className={styles.modalActions}>
            <Button type="button" variant="secondary" onClick={() => setModalOpen(false)}>
              Vazgeç
            </Button>
            <Button type="submit" variant="primary" disabled={saving}>
              {saving ? 'Oluşturuluyor...' : 'Klinik Oluştur'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
