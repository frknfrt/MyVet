import { FormEvent, useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { Plan, platformAdminApi } from '../../api/platformAdminApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import styles from './PlatformAdminPages.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

interface PlanFormState {
  code: string;
  name: string;
  monthlyPrice: string;
  active: boolean;
}

const EMPTY_FORM: PlanFormState = { code: '', name: '', monthlyPrice: '', active: true };

export function PlanManagementPage() {
  const [plans, setPlans] = useState<Plan[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<PlanFormState>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  function load() {
    setLoading(true);
    platformAdminApi
      .listPlans()
      .then(setPlans)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  function openCreate() {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setModalOpen(true);
  }

  function openEdit(plan: Plan) {
    setEditingId(plan.id);
    setForm({ code: plan.code, name: plan.name, monthlyPrice: String(plan.monthlyPrice), active: plan.active });
    setModalOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const monthlyPrice = Number(form.monthlyPrice);
      if (editingId) {
        await platformAdminApi.updatePlan(editingId, { name: form.name, monthlyPrice, active: form.active });
      } else {
        await platformAdminApi.createPlan({ code: form.code, name: form.name, monthlyPrice });
      }
      setModalOpen(false);
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(plan: Plan) {
    if (!window.confirm(`"${plan.name}" planını silmek istediğinize emin misiniz?`)) return;
    try {
      await platformAdminApi.deletePlan(plan.id);
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    }
  }

  return (
    <div>
      <div className={styles.title}>Plan Kataloğu</div>

      <div className={styles.actionsRow}>
        <Button variant="primary" onClick={openCreate}>
          + Yeni Plan
        </Button>
      </div>

      {error && <div className={styles.errorBanner}>{error}</div>}

      <div className={styles.tableCard}>
        <div className={`${styles.tableHead} ${styles.planRow}`}>
          <div>Kod</div>
          <div>Ad</div>
          <div>Aylık Fiyat</div>
          <div>Durum</div>
          <div></div>
        </div>
        {loading ? (
          <div className={styles.empty}>Yükleniyor...</div>
        ) : plans.length === 0 ? (
          <div className={styles.empty}>Henüz plan tanımlanmadı</div>
        ) : (
          plans.map((plan) => (
            <div key={plan.id} className={`${styles.row} ${styles.planRow}`} style={{ cursor: 'default' }}>
              <div>{plan.code}</div>
              <div>{plan.name}</div>
              <div className={styles.muted}>{plan.monthlyPrice.toFixed(2)} ₺</div>
              <div>
                <Badge tone={plan.active ? 'success' : 'neutral'}>{plan.active ? 'Aktif' : 'Pasif'}</Badge>
              </div>
              <div className={styles.rowActions}>
                <Button variant="secondary" onClick={() => openEdit(plan)}>
                  Düzenle
                </Button>
                <Button variant="danger" onClick={() => handleDelete(plan)}>
                  Sil
                </Button>
              </div>
            </div>
          ))
        )}
      </div>

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} width={480}>
        <form onSubmit={handleSubmit}>
          <div className={styles.modalTitle}>{editingId ? 'Planı Düzenle' : 'Yeni Plan'}</div>

          <FieldWrap label="Plan Kodu">
            <Input
              value={form.code}
              onChange={(e) => setForm((f) => ({ ...f, code: e.target.value }))}
              disabled={!!editingId}
              required={!editingId}
            />
          </FieldWrap>
          <FieldWrap label="Plan Adı">
            <Input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} required />
          </FieldWrap>
          <FieldWrap label="Aylık Fiyat (₺)">
            <Input
              type="number"
              min={0}
              step="0.01"
              value={form.monthlyPrice}
              onChange={(e) => setForm((f) => ({ ...f, monthlyPrice: e.target.value }))}
              required
            />
          </FieldWrap>
          {editingId && (
            <FieldWrap label="Durum">
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
                <input
                  type="checkbox"
                  checked={form.active}
                  onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))}
                />
                Aktif
              </label>
            </FieldWrap>
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
