import { FormEvent, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { BillingStatus, Plan, platformAdminApi, TenantAdminOverview } from '../../api/platformAdminApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import styles from './PlatformAdminPages.module.css';
import { BILLING_STATUS_LABELS, BILLING_STATUS_TONES, formatDate, TENANT_STATUS_LABELS, TENANT_STATUS_TONES } from './tenantBadges';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

const BILLING_STATUS_OPTIONS: BillingStatus[] = ['TRIAL', 'ACTIVE', 'PAST_DUE', 'CANCELED'];

interface SubscriptionFormState {
  planCode: string;
  billingStatus: BillingStatus;
  renewsAt: string;
}

export function TenantDetailPage() {
  const { tenantId } = useParams<{ tenantId: string }>();
  const [tenant, setTenant] = useState<TenantAdminOverview | null>(null);
  const [plans, setPlans] = useState<Plan[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState<SubscriptionFormState>({ planCode: '', billingStatus: 'TRIAL', renewsAt: '' });

  function load() {
    if (!tenantId) return;
    platformAdminApi.getTenant(tenantId).then(setTenant).catch((err) => setError(errorMessageOf(err)));
  }

  useEffect(load, [tenantId]);
  useEffect(() => {
    platformAdminApi.listPlans().then(setPlans).catch(() => undefined);
  }, []);

  if (!tenantId) return null;

  function openSubscriptionModal() {
    if (!tenant) return;
    setForm({
      planCode: tenant.planCode,
      billingStatus: tenant.billingStatus,
      renewsAt: tenant.renewsAt ?? '',
    });
    setModalOpen(true);
  }

  async function handleSubscriptionSubmit(e: FormEvent) {
    e.preventDefault();
    if (!tenantId) return;
    setSaving(true);
    setError(null);
    try {
      await platformAdminApi.updateSubscription(tenantId, {
        planCode: form.planCode,
        billingStatus: form.billingStatus,
        renewsAt: form.renewsAt || null,
      });
      setModalOpen(false);
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setSaving(false);
    }
  }

  async function handleToggleStatus() {
    if (!tenant || !tenantId) return;
    try {
      if (tenant.status === 'SUSPENDED') {
        await platformAdminApi.activateTenant(tenantId);
      } else {
        await platformAdminApi.suspendTenant(tenantId);
      }
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    }
  }

  return (
    <div>
      <Link to="/platform-admin/tenants" className={styles.backLink}>
        ← Kiracılar
      </Link>
      <div className={styles.title}>{tenant?.name ?? 'Yükleniyor...'}</div>

      {error && <div className={styles.errorBanner}>{error}</div>}

      {tenant && (
        <>
          <div className={styles.detailGrid}>
            <div className={styles.card}>
              <div className={styles.cardTitle}>Kiracı Bilgileri</div>
              <div className={styles.infoRow}>
                <span className={styles.infoLabel}>Durum</span>
                <Badge tone={TENANT_STATUS_TONES[tenant.status]}>{TENANT_STATUS_LABELS[tenant.status]}</Badge>
              </div>
              <div className={styles.infoRow}>
                <span className={styles.infoLabel}>Vergi No</span>
                <span>{tenant.taxNumber || '—'}</span>
              </div>
              <div className={styles.infoRow}>
                <span className={styles.infoLabel}>Kayıt Tarihi</span>
                <span>{formatDate(tenant.createdAt)}</span>
              </div>
              <div className={styles.infoRow}>
                <span className={styles.infoLabel}>Şube Sayısı</span>
                <span>{tenant.branchCount}</span>
              </div>
              <div className={styles.infoRow}>
                <span className={styles.infoLabel}>Personel Sayısı</span>
                <span>{tenant.staffUserCount}</span>
              </div>
              <div className={styles.modalActions}>
                <Button variant={tenant.status === 'SUSPENDED' ? 'secondary' : 'danger'} onClick={handleToggleStatus}>
                  {tenant.status === 'SUSPENDED' ? 'Aktif Et' : 'Askıya Al'}
                </Button>
              </div>
            </div>

            <div className={styles.card}>
              <div className={styles.cardTitle}>Abonelik</div>
              <div className={styles.infoRow}>
                <span className={styles.infoLabel}>Plan</span>
                <span>{tenant.planCode}</span>
              </div>
              <div className={styles.infoRow}>
                <span className={styles.infoLabel}>Faturalama Durumu</span>
                <Badge tone={BILLING_STATUS_TONES[tenant.billingStatus]}>{BILLING_STATUS_LABELS[tenant.billingStatus]}</Badge>
              </div>
              <div className={styles.infoRow}>
                <span className={styles.infoLabel}>Başlangıç Tarihi</span>
                <span>{formatDate(tenant.startedAt)}</span>
              </div>
              <div className={styles.infoRow}>
                <span className={styles.infoLabel}>Yenileme Tarihi</span>
                <span>{formatDate(tenant.renewsAt)}</span>
              </div>
              <div className={styles.modalActions}>
                <Button variant="primary" onClick={openSubscriptionModal}>
                  Plan / Durum Değiştir
                </Button>
              </div>
            </div>
          </div>

          <Modal open={modalOpen} onClose={() => setModalOpen(false)} width={480}>
            <form onSubmit={handleSubscriptionSubmit}>
              <div className={styles.modalTitle}>Abonelik Değiştir</div>

              <FieldWrap label="Plan Kodu">
                <Input
                  value={form.planCode}
                  onChange={(e) => setForm((f) => ({ ...f, planCode: e.target.value }))}
                  list="plan-code-options"
                  required
                />
                <datalist id="plan-code-options">
                  {plans.map((p) => (
                    <option key={p.id} value={p.code} />
                  ))}
                </datalist>
              </FieldWrap>
              <FieldWrap label="Faturalama Durumu">
                <Select
                  value={form.billingStatus}
                  onChange={(e) => setForm((f) => ({ ...f, billingStatus: e.target.value as BillingStatus }))}
                >
                  {BILLING_STATUS_OPTIONS.map((status) => (
                    <option key={status} value={status}>
                      {BILLING_STATUS_LABELS[status]}
                    </option>
                  ))}
                </Select>
              </FieldWrap>
              <FieldWrap label="Yenileme Tarihi">
                <Input
                  type="date"
                  value={form.renewsAt}
                  onChange={(e) => setForm((f) => ({ ...f, renewsAt: e.target.value }))}
                />
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
        </>
      )}
    </div>
  );
}
