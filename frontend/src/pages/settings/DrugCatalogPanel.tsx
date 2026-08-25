import { FormEvent, useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { clinicalApi, DrugSummary } from '../../api/clinicalApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import settingsStyles from './SettingsPage.module.css';
import styles from './DrugCatalogPanel.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

interface DrugFormState {
  name: string;
  activeIngredient: string;
  isControlled: boolean;
  interactingDrugIds: string[];
}

function emptyForm(): DrugFormState {
  return { name: '', activeIngredient: '', isControlled: false, interactingDrugIds: [] };
}

export function DrugCatalogPanel() {
  const [drugs, setDrugs] = useState<DrugSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<DrugFormState>(emptyForm());
  const [saving, setSaving] = useState(false);

  function load() {
    setLoading(true);
    clinicalApi
      .listDrugs()
      .then(setDrugs)
      .catch((err) => setError(errorMessageOf(err)))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  function openCreate() {
    setEditingId(null);
    setForm(emptyForm());
    setModalOpen(true);
  }

  function openEdit(d: DrugSummary) {
    setEditingId(d.id);
    setForm({
      name: d.name,
      activeIngredient: d.activeIngredient ?? '',
      isControlled: d.isControlled,
      interactingDrugIds: d.interactingDrugIds,
    });
    setModalOpen(true);
  }

  function toggleInteracting(drugId: string) {
    setForm((f) => ({
      ...f,
      interactingDrugIds: f.interactingDrugIds.includes(drugId)
        ? f.interactingDrugIds.filter((id) => id !== drugId)
        : [...f.interactingDrugIds, drugId],
    }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      if (editingId) {
        await clinicalApi.updateDrug(editingId, {
          name: form.name,
          activeIngredient: form.activeIngredient,
          isControlled: form.isControlled,
          interactingDrugIds: form.interactingDrugIds,
        });
      } else {
        await clinicalApi.createDrug({
          name: form.name,
          activeIngredient: form.activeIngredient,
          isControlled: form.isControlled,
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

  const otherDrugs = drugs.filter((d) => d.id !== editingId);

  return (
    <div>
      <div className={styles.actionsRow}>
        <Button variant="primary" onClick={openCreate}>
          + Yeni İlaç
        </Button>
      </div>

      {error && <div className={settingsStyles.errorBanner}>{error}</div>}

      <div className={settingsStyles.tableCard}>
        <div className={styles.tableHead}>
          <div>Ad</div>
          <div>Etken Madde</div>
          <div>Kontrollü</div>
          <div>Etkileşim</div>
          <div></div>
        </div>
        {loading ? (
          <div className={settingsStyles.empty}>Yükleniyor...</div>
        ) : drugs.length === 0 ? (
          <div className={settingsStyles.empty}>Henüz ilaç eklenmedi</div>
        ) : (
          drugs.map((d) => (
            <div key={d.id} className={styles.tableRow}>
              <div>{d.name}</div>
              <div className={settingsStyles.muted}>{d.activeIngredient || '—'}</div>
              <div>{d.isControlled && <Badge tone="warning">Kontrollü</Badge>}</div>
              <div className={settingsStyles.muted}>
                {d.interactingDrugIds.length > 0 ? `${d.interactingDrugIds.length} ilaç` : '—'}
              </div>
              <div>
                <Button variant="secondary" onClick={() => openEdit(d)}>
                  Düzenle
                </Button>
              </div>
            </div>
          ))
        )}
      </div>

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} width={560}>
        <form onSubmit={handleSubmit}>
          <div className={styles.modalTitle}>{editingId ? 'İlacı Düzenle' : 'Yeni İlaç'}</div>

          <div className={styles.formGrid}>
            <FieldWrap label="Ad">
              <Input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} required />
            </FieldWrap>
            <FieldWrap label="Etken Madde">
              <Input
                value={form.activeIngredient}
                onChange={(e) => setForm((f) => ({ ...f, activeIngredient: e.target.value }))}
              />
            </FieldWrap>
            <label className={styles.controlledRow}>
              <input
                type="checkbox"
                checked={form.isControlled}
                onChange={(e) => setForm((f) => ({ ...f, isControlled: e.target.checked }))}
              />
              Kontrollü ilaç
            </label>
          </div>

          {editingId && (
            <>
              <div className={styles.interactionsLabel}>Etkileşime Giren İlaçlar</div>
              <div className={styles.interactionsHint}>
                Sistem etkileşimleri kendisi belirlemez — burada işaretlenen ilaç çiftleri, reçete oluşturulurken
                hekime uyarı olarak gösterilir. Bilinen gerçek etkileşimleri kliniğin/hekimin girmesi gerekir.
              </div>
              <div className={styles.interactionList}>
                {otherDrugs.length === 0 ? (
                  <div className={styles.interactionEmpty}>Katalogda başka ilaç yok</div>
                ) : (
                  otherDrugs.map((d) => (
                    <label key={d.id} className={styles.interactionItem}>
                      <input
                        type="checkbox"
                        checked={form.interactingDrugIds.includes(d.id)}
                        onChange={() => toggleInteracting(d.id)}
                      />
                      {d.name}
                    </label>
                  ))
                )}
              </div>
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
