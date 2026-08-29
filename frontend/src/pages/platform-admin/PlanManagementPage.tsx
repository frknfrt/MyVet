import { FormEvent, useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { Plan, platformAdminApi } from '../../api/platformAdminApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Textarea } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import styles from './PlatformAdminPages.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

interface PlanFormState {
  code: string;
  name: string;
  monthlyPrice: string;
  annualPrice: string;
  description: string;
  badge: string;
  imageUrl: string;
  features: string[];
  active: boolean;
}

const EMPTY_FORM: PlanFormState = {
  code: '',
  name: '',
  monthlyPrice: '',
  annualPrice: '',
  description: '',
  badge: '',
  imageUrl: '',
  features: [],
  active: true,
};

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
    setForm({
      code: plan.code,
      name: plan.name,
      monthlyPrice: String(plan.monthlyPrice),
      annualPrice: plan.annualPrice === null ? '' : String(plan.annualPrice),
      description: plan.description ?? '',
      badge: plan.badge ?? '',
      imageUrl: plan.imageUrl ?? '',
      features: plan.features,
      active: plan.active,
    });
    setModalOpen(true);
  }

  function updateFeature(index: number, value: string) {
    setForm((f) => ({ ...f, features: f.features.map((feat, i) => (i === index ? value : feat)) }));
  }

  function addFeature() {
    setForm((f) => ({ ...f, features: [...f.features, ''] }));
  }

  function removeFeature(index: number) {
    setForm((f) => ({ ...f, features: f.features.filter((_, i) => i !== index) }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const monthlyPrice = Number(form.monthlyPrice);
      if (editingId) {
        await platformAdminApi.updatePlan(editingId, {
          name: form.name,
          monthlyPrice,
          annualPrice: form.annualPrice === '' ? null : Number(form.annualPrice),
          description: form.description === '' ? null : form.description,
          badge: form.badge === '' ? null : form.badge,
          imageUrl: form.imageUrl === '' ? null : form.imageUrl,
          features: form.features.map((f) => f.trim()).filter((f) => f.length > 0),
          active: form.active,
        });
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
          <div>Etiket</div>
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
              <div className={styles.muted}>{plan.badge || '—'}</div>
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
            <>
              <FieldWrap label="Yıllık Fiyat (₺)">
                <Input
                  type="number"
                  min={0}
                  step="0.01"
                  value={form.annualPrice}
                  onChange={(e) => setForm((f) => ({ ...f, annualPrice: e.target.value }))}
                  placeholder="Boş bırakılırsa yıllık seçenek gösterilmez"
                />
              </FieldWrap>
              <FieldWrap label="Etiket (ör. En Popüler)">
                <Input value={form.badge} onChange={(e) => setForm((f) => ({ ...f, badge: e.target.value }))} />
              </FieldWrap>
              <FieldWrap label="Görsel URL">
                <Input value={form.imageUrl} onChange={(e) => setForm((f) => ({ ...f, imageUrl: e.target.value }))} />
              </FieldWrap>
              <FieldWrap label="Açıklama">
                <Textarea
                  value={form.description}
                  onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
                  rows={2}
                />
              </FieldWrap>
              <FieldWrap label="Özellikler">
                {form.features.map((feature, index) => (
                  <div key={index} className={styles.featureRow}>
                    <Input value={feature} onChange={(e) => updateFeature(index, e.target.value)} />
                    <Button type="button" variant="tertiary" onClick={() => removeFeature(index)}>
                      Kaldır
                    </Button>
                  </div>
                ))}
                <Button type="button" variant="secondary" onClick={addFeature}>
                  + Özellik Ekle
                </Button>
              </FieldWrap>
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
            </>
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
