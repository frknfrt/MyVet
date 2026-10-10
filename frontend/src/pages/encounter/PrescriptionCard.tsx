import { FormEvent, useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import {
  clinicalApi,
  DrugInteractionWarning,
  DrugRoute,
  DrugSummary,
  Prescription,
  PrescriptionItemPayload,
} from '../../api/clinicalApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select } from '../../components/ui/Field';
import { inventoryApi, InventoryItem } from '../../api/inventoryApi';
import encounterStyles from './EncounterPage.module.css';
import styles from './PrescriptionCard.module.css';
import { StockGivePicker } from './StockGivePicker';
import { TarbilDiseaseSelect } from './TarbilDiseaseSelect';

interface PrescriptionCardProps {
  /** null: hasta sayfasından muayenesiz reçete (spec 2026-10-09 S5.6). */
  encounterId: string | null;
  patientId: string;
  readOnly: boolean;
  onChanged?: () => void;
}

/** Form satırı: "Kliniğin stoğundan ver" işaretliyse stok kalemi + adet gönderilir. */
type ItemDraft = PrescriptionItemPayload & { giveFromStock: boolean };

const ROUTE_LABELS: Record<DrugRoute, string> = {
  ORAL: 'Ağızdan',
  TOPICAL: 'Topikal (Deri Üzeri)',
  INJECTABLE: 'Enjeksiyon',
};

const STATUS_LABELS: Record<Prescription['status'], string> = {
  ACTIVE: 'Aktif',
  FULFILLED: 'Karşılandı',
  CANCELLED: 'İptal Edildi',
};

function emptyItem(defaultDrugId: string): ItemDraft {
  return {
    drugId: defaultDrugId,
    dosage: '',
    frequency: '',
    durationDays: 7,
    route: 'ORAL',
    inventoryItemId: null,
    stockQuantity: null,
    giveFromStock: false,
  };
}

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

export function PrescriptionCard({ encounterId, patientId, readOnly, onChanged }: PrescriptionCardProps) {
  const [prescriptions, setPrescriptions] = useState<Prescription[]>([]);
  const [drugs, setDrugs] = useState<DrugSummary[]>([]);
  const [formOpen, setFormOpen] = useState(false);
  const [items, setItems] = useState<ItemDraft[]>([]);
  const [tarbilDiseaseId, setTarbilDiseaseId] = useState('');
  const [stockItems, setStockItems] = useState<InventoryItem[]>([]);
  const [confirmCancel, setConfirmCancel] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [interactionWarnings, setInteractionWarnings] = useState<DrugInteractionWarning[]>([]);

  function reload() {
    clinicalApi
      .listPrescriptionsByPatient(patientId)
      .then((all) => setPrescriptions(all.filter((p) => p.encounterId === encounterId)));
  }

  useEffect(() => {
    clinicalApi.listDrugs().then(setDrugs);
    inventoryApi.list().then(setStockItems).catch(() => setStockItems([]));
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [encounterId, patientId]);

  useEffect(() => {
    const drugIds = [...new Set(items.map((it) => it.drugId).filter(Boolean))];
    if (drugIds.length < 2) {
      setInteractionWarnings([]);
      return;
    }
    let cancelled = false;
    clinicalApi.checkDrugInteractions(drugIds).then((warnings) => {
      if (!cancelled) setInteractionWarnings(warnings);
    });
    return () => {
      cancelled = true;
    };
  }, [items]);

  function drugName(id: string) {
    return drugs.find((d) => d.id === id)?.name ?? id;
  }

  function stockName(id: string | null) {
    return stockItems.find((s) => s.id === id)?.name ?? 'Stok kalemi';
  }

  async function cancelPrescription(id: string) {
    if (confirmCancel !== id) {
      setConfirmCancel(id);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await clinicalApi.cancelPrescription(id);
      setConfirmCancel(null);
      reload();
      onChanged?.();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  function openForm() {
    setItems([emptyItem(drugs[0]?.id ?? '')]);
    setTarbilDiseaseId('');
    setFormOpen(true);
    setError(null);
  }

  function addItemRow() {
    setItems((prev) => [...prev, emptyItem(drugs[0]?.id ?? '')]);
  }

  function updateItem(index: number, patch: Partial<ItemDraft>) {
    setItems((prev) => prev.map((it, i) => (i === index ? { ...it, ...patch } : it)));
  }

  function removeItem(index: number) {
    setItems((prev) => prev.filter((_, i) => i !== index));
  }

  const givesStock = items.some((it) => it.giveFromStock);
  const stockIncomplete = givesStock && (!tarbilDiseaseId || items.some((it) => it.giveFromStock && !it.inventoryItemId));

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (busy || items.length === 0 || stockIncomplete) return;
    setBusy(true);
    setError(null);
    try {
      const controlledSubstance = items.some((it) => drugs.find((d) => d.id === it.drugId)?.isControlled);
      const payloadItems: PrescriptionItemPayload[] = items.map(({ giveFromStock, ...it }) =>
        giveFromStock ? it : { ...it, inventoryItemId: null, stockQuantity: null },
      );
      await clinicalApi.issuePrescription({
        patientId,
        encounterId,
        controlledSubstance,
        tarbilDiseaseId: givesStock ? tarbilDiseaseId : null,
        items: payloadItems,
      });
      setFormOpen(false);
      setItems([]);
      reload();
      onChanged?.();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={encounterStyles.card}>
      <div className={encounterStyles.cardTitle}>{encounterId ? 'Reçeteler' : 'Muayenesiz reçeteler'}</div>

      {error && <div className={encounterStyles.aiError}>{error}</div>}

      {prescriptions.length === 0 ? (
        <div className={styles.emptyNote}>
          {encounterId ? 'Bu muayene için henüz reçete oluşturulmadı.' : 'Muayenesiz reçete yok.'}
        </div>
      ) : (
        prescriptions.map((p) => (
          <div key={p.id} className={styles.prescriptionGroup}>
            <div className={styles.prescriptionHeader}>
              <span className={styles.prescriptionDate}>{new Date(p.issuedDate).toLocaleDateString('tr-TR')}</span>
              <Badge tone={p.status === 'ACTIVE' ? 'success' : p.status === 'CANCELLED' ? 'danger' : 'neutral'}>
                {STATUS_LABELS[p.status]}
              </Badge>
              {p.controlledSubstance && <Badge tone="warning">Kontrollü İlaç</Badge>}
              {!readOnly && p.status === 'ACTIVE' && (
                <button type="button" className={styles.removeBtn} disabled={busy} onClick={() => cancelPrescription(p.id)}>
                  {confirmCancel === p.id
                    ? "Emin misiniz? Stoktan verilenler TARBİL'de düşüldüyse reçeteyi TARBİL'de de iptal edin."
                    : 'İptal et'}
                </button>
              )}
            </div>
            {p.items.map((item, i) => (
              <div key={i} className={styles.itemLine}>
                {item.drugName ?? drugName(item.drugId)} — {item.dosage}, {item.frequency}, {item.durationDays} gün,{' '}
                <span>{ROUTE_LABELS[item.route as DrugRoute] ?? item.route}</span>
                {item.inventoryItemId && (
                  <span className={styles.stockNote}>
                    {' '}
                    · Stoktan: {stockName(item.inventoryItemId)} × {item.stockQuantity}
                  </span>
                )}
              </div>
            ))}
          </div>
        ))
      )}

      {!readOnly && !formOpen && (
        <div className={styles.addToggle}>
          <Button variant="secondary" onClick={openForm} disabled={drugs.length === 0}>
            + Yeni Reçete
          </Button>
        </div>
      )}

      {!readOnly && formOpen && (
        <form className={styles.form} onSubmit={handleSubmit}>
          {items.map((item, index) => (
            <div key={index} className={styles.itemRow}>
              <FieldWrap label="İlaç">
                <Select value={item.drugId} onChange={(e) => updateItem(index, { drugId: e.target.value })}>
                  {drugs.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.name}
                      {d.isControlled ? ' (kontrollü)' : ''}
                    </option>
                  ))}
                </Select>
              </FieldWrap>
              <FieldWrap label="Dozaj">
                <Input
                  value={item.dosage}
                  onChange={(e) => updateItem(index, { dosage: e.target.value })}
                  placeholder="Örn. 10 mg/kg"
                  required
                />
              </FieldWrap>
              <FieldWrap label="Sıklık">
                <Input
                  value={item.frequency}
                  onChange={(e) => updateItem(index, { frequency: e.target.value })}
                  placeholder="Örn. Günde 2 kez"
                  required
                />
              </FieldWrap>
              <FieldWrap label="Süre (gün)">
                <Input
                  type="number"
                  min={1}
                  value={item.durationDays}
                  onChange={(e) => updateItem(index, { durationDays: Number(e.target.value) })}
                  required
                />
              </FieldWrap>
              <FieldWrap label="Uygulama">
                <Select value={item.route} onChange={(e) => updateItem(index, { route: e.target.value as DrugRoute })}>
                  {(Object.keys(ROUTE_LABELS) as DrugRoute[]).map((route) => (
                    <option key={route} value={route}>
                      {ROUTE_LABELS[route]}
                    </option>
                  ))}
                </Select>
              </FieldWrap>
              <label className={styles.stockToggle}>
                <input
                  type="checkbox"
                  aria-label="Kliniğin stoğundan ver"
                  checked={item.giveFromStock}
                  onChange={(e) =>
                    updateItem(
                      index,
                      e.target.checked
                        ? { giveFromStock: true, stockQuantity: 1 }
                        : { giveFromStock: false, inventoryItemId: null, stockQuantity: null },
                    )
                  }
                />
                Kliniğin stoğundan ver
              </label>
              {item.giveFromStock && (
                <>
                  <StockGivePicker
                    value={item.inventoryItemId ?? null}
                    onChange={(id) => updateItem(index, { inventoryItemId: id })}
                  />
                  <FieldWrap label="Adet">
                    <Input
                      type="number"
                      min={1}
                      value={item.stockQuantity ?? 1}
                      onChange={(e) => updateItem(index, { stockQuantity: Number(e.target.value) })}
                      required
                    />
                  </FieldWrap>
                </>
              )}
              {items.length > 1 && (
                <button type="button" className={styles.removeBtn} onClick={() => removeItem(index)}>
                  Kaldır
                </button>
              )}
            </div>
          ))}

          {interactionWarnings.length > 0 && (
            <div className={styles.interactionWarning}>
              <Badge tone="warning">Olası Etkileşim</Badge>
              <ul>
                {interactionWarnings.map((w, i) => (
                  <li key={i}>
                    {w.drugAName} + {w.drugBName} — klinik kayıtlarına göre birlikte reçete edilmemesi önerilir,
                    devam etmeden önce gözden geçirin.
                  </li>
                ))}
              </ul>
            </div>
          )}

          {givesStock && (
            <div className={styles.tarbilDisease}>
              <TarbilDiseaseSelect value={tarbilDiseaseId} onChange={setTarbilDiseaseId} />
              <div className={styles.emptyNote}>Stoktan verilen ilaçlar TARBİL'de reçete onaylanınca stoktan düşer.</div>
            </div>
          )}

          <div className={styles.formActions}>
            <Button type="button" variant="tertiary" onClick={addItemRow}>
              + İlaç Ekle
            </Button>
            <div>
              <Button type="button" variant="secondary" onClick={() => setFormOpen(false)}>
                Vazgeç
              </Button>{' '}
              <Button type="submit" variant="primary" disabled={busy || stockIncomplete}>
                {busy ? 'Kaydediliyor...' : 'Reçete Oluştur'}
              </Button>
            </div>
          </div>
        </form>
      )}
    </div>
  );
}
