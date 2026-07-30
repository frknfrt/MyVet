import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { encounterApi, EncounterDetail } from '../../api/encounterApi';
import { AppShell } from '../../components/layout/AppShell';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Textarea } from '../../components/ui/Field';
import { EncounterStatusBadge } from './encounterStatus';
import { MaterialsUsedCard } from './MaterialsUsedCard';
import styles from './EncounterPage.module.css';

type VitalsForm = { weightKg: string; temperatureC: string; heartRate: string; respiratoryRate: string };
type SoapForm = { subjective: string; objective: string; assessment: string; plan: string };

export function EncounterPage() {
  const { encounterId } = useParams<{ encounterId: string }>();
  const navigate = useNavigate();
  const [encounter, setEncounter] = useState<EncounterDetail | null>(null);
  const [soap, setSoap] = useState<SoapForm>({ subjective: '', objective: '', assessment: '', plan: '' });
  const [vitals, setVitals] = useState<VitalsForm>({ weightKg: '', temperatureC: '', heartRate: '', respiratoryRate: '' });
  const [savingSoap, setSavingSoap] = useState(false);
  const [savingVitals, setSavingVitals] = useState(false);
  const [finalizing, setFinalizing] = useState(false);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!encounterId) return;
    encounterApi.get(encounterId).then((e) => {
      setEncounter(e);
      setSoap({
        subjective: e.subjective ?? '',
        objective: e.objective ?? '',
        assessment: e.assessment ?? '',
        plan: e.plan ?? '',
      });
      setVitals({
        weightKg: e.weightKg?.toString() ?? '',
        temperatureC: e.temperatureC?.toString() ?? '',
        heartRate: e.heartRate?.toString() ?? '',
        respiratoryRate: e.respiratoryRate?.toString() ?? '',
      });
    });
  }, [encounterId]);

  const isReadOnly = encounter?.status === 'FINALIZED' || encounter?.status === 'AMENDED';

  function flashSaved(message: string) {
    setSavedMessage(message);
    setTimeout(() => setSavedMessage(null), 2000);
  }

  async function handleSaveSoap() {
    if (!encounterId) return;
    setSavingSoap(true);
    try {
      await encounterApi.updateSoap(encounterId, soap);
      flashSaved('SOAP kaydedildi');
    } finally {
      setSavingSoap(false);
    }
  }

  async function handleSaveVitals() {
    if (!encounterId) return;
    setSavingVitals(true);
    try {
      await encounterApi.updateVitals(encounterId, {
        weightKg: vitals.weightKg ? Number(vitals.weightKg) : null,
        temperatureC: vitals.temperatureC ? Number(vitals.temperatureC) : null,
        heartRate: vitals.heartRate ? Number(vitals.heartRate) : null,
        respiratoryRate: vitals.respiratoryRate ? Number(vitals.respiratoryRate) : null,
      });
      flashSaved('Vital bulgular kaydedildi');
    } finally {
      setSavingVitals(false);
    }
  }

  async function handleFinalize() {
    if (!encounterId) return;
    if (!window.confirm('Muayeneyi tamamlamak istediginizden emin misiniz? Tamamlandiktan sonra SOAP notu kilitlenir.')) {
      return;
    }
    setFinalizing(true);
    try {
      await encounterApi.updateSoap(encounterId, soap);
      await encounterApi.finalize(encounterId);
      const updated = await encounterApi.get(encounterId);
      setEncounter(updated);
    } finally {
      setFinalizing(false);
    }
  }

  if (!encounter) {
    return (
      <AppShell>
        <div>Yükleniyor...</div>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <div className={styles.topbar}>
        <div>
          <h1 className={styles.title}>{encounter.patientName} — SOAP Muayenesi</h1>
          <div className={styles.sub}>
            {encounter.staffName} · {new Date(encounter.encounterDate).toLocaleString('tr-TR')}
          </div>
        </div>
        <div className={styles.headerActions}>
          <EncounterStatusBadge status={encounter.status} />
          <Button variant="secondary" onClick={() => navigate('/hastalar')}>
            Hastalara Dön
          </Button>
        </div>
      </div>

      <div className={styles.layout}>
        <div>
          <div className={styles.card}>
            <div className={styles.cardTitle}>Vital Bulgular</div>
            <div className={styles.vitalsGrid}>
              <FieldWrap label="Ağırlık (kg)">
                <Input
                  type="number"
                  step="0.1"
                  value={vitals.weightKg}
                  disabled={isReadOnly}
                  onChange={(e) => setVitals((v) => ({ ...v, weightKg: e.target.value }))}
                />
              </FieldWrap>
              <FieldWrap label="Ateş (°C)">
                <Input
                  type="number"
                  step="0.1"
                  value={vitals.temperatureC}
                  disabled={isReadOnly}
                  onChange={(e) => setVitals((v) => ({ ...v, temperatureC: e.target.value }))}
                />
              </FieldWrap>
              <FieldWrap label="Nabız (bpm)">
                <Input
                  type="number"
                  value={vitals.heartRate}
                  disabled={isReadOnly}
                  onChange={(e) => setVitals((v) => ({ ...v, heartRate: e.target.value }))}
                />
              </FieldWrap>
              <FieldWrap label="Solunum (/dk)">
                <Input
                  type="number"
                  value={vitals.respiratoryRate}
                  disabled={isReadOnly}
                  onChange={(e) => setVitals((v) => ({ ...v, respiratoryRate: e.target.value }))}
                />
              </FieldWrap>
            </div>
            {!isReadOnly && (
              <div className={styles.saveRow}>
                {savedMessage && <span className={styles.savedNote}>{savedMessage}</span>}
                <Button variant="secondary" onClick={handleSaveVitals} disabled={savingVitals}>
                  {savingVitals ? 'Kaydediliyor...' : 'Vitalleri Kaydet'}
                </Button>
              </div>
            )}
          </div>

          <div className={styles.card}>
            <div className={styles.cardTitle}>SOAP Notu</div>
            <div className={styles.soapGrid}>
              <FieldWrap label="Subjective (S)">
                <Textarea
                  value={soap.subjective}
                  disabled={isReadOnly}
                  onChange={(e) => setSoap((s) => ({ ...s, subjective: e.target.value }))}
                  placeholder="Sahibin ifadesi, şikayetin öyküsü..."
                />
              </FieldWrap>
              <FieldWrap label="Objective (O)">
                <Textarea
                  value={soap.objective}
                  disabled={isReadOnly}
                  onChange={(e) => setSoap((s) => ({ ...s, objective: e.target.value }))}
                  placeholder="Fizik muayene bulguları..."
                />
              </FieldWrap>
              <FieldWrap label="Assessment (A)">
                <Textarea
                  value={soap.assessment}
                  disabled={isReadOnly}
                  onChange={(e) => setSoap((s) => ({ ...s, assessment: e.target.value }))}
                  placeholder="Değerlendirme / ön tanı..."
                />
              </FieldWrap>
              <FieldWrap label="Plan (P)">
                <Textarea
                  value={soap.plan}
                  disabled={isReadOnly}
                  onChange={(e) => setSoap((s) => ({ ...s, plan: e.target.value }))}
                  placeholder="Tedavi planı, reçete, kontrol tarihi..."
                />
              </FieldWrap>
            </div>
            {!isReadOnly && (
              <div className={styles.saveRow}>
                {savedMessage && <span className={styles.savedNote}>{savedMessage}</span>}
                <Button variant="secondary" onClick={handleSaveSoap} disabled={savingSoap}>
                  {savingSoap ? 'Kaydediliyor...' : 'SOAP Kaydet'}
                </Button>
                <Button variant="primary" onClick={handleFinalize} disabled={finalizing}>
                  {finalizing ? 'Tamamlanıyor...' : 'Muayeneyi Tamamla'}
                </Button>
              </div>
            )}
          </div>

          <MaterialsUsedCard encounterId={encounter.id} readOnly={isReadOnly} />
        </div>

        <div>
          <div className={styles.aiCard}>
            <div className={styles.aiTitle}>✦ AI Scribe</div>
            <p className={styles.aiCopy}>
              Muayene sesini dinleyip SOAP alanlarını otomatik dolduran AI Scribe, Faz 2'de gerçek model
              entegrasyonuna bağlanacak. Üretilen her taslak her zaman hekim onayına sunulur, otomatik
              uygulanmaz.
            </p>
            <Button variant="ai" disabled>
              Sesle SOAP Oluştur (Faz 2)
            </Button>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
