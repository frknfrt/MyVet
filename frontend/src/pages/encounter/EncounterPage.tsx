import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { aiApi } from '../../api/aiApi';
import { ApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { encounterApi, EncounterDetail } from '../../api/encounterApi';
import { patientApi, PatientProfile } from '../../api/patientApi';
import { AppShell } from '../../components/layout/AppShell';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Textarea } from '../../components/ui/Field';
import { MaterialsUsedCard } from './MaterialsUsedCard';
import { PatientHeaderBar } from './PatientHeaderBar';
import { PhysicalExamCard } from './PhysicalExamCard';
import { PrescriptionCard } from './PrescriptionCard';
import styles from './EncounterPage.module.css';

type VitalsForm = { weightKg: string; temperatureC: string; heartRate: string; respiratoryRate: string };
type SoapForm = { subjective: string; objective: string; assessment: string; plan: string };

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function getSpeechRecognitionCtor(): (new () => any) | null {
  const w = window as any;
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

export function EncounterPage() {
  const { encounterId } = useParams<{ encounterId: string }>();
  const navigate = useNavigate();
  const { session } = useAuth();
  const [encounter, setEncounter] = useState<EncounterDetail | null>(null);
  const [profile, setProfile] = useState<PatientProfile | null>(null);
  const [soap, setSoap] = useState<SoapForm>({ subjective: '', objective: '', assessment: '', plan: '' });
  const [vitals, setVitals] = useState<VitalsForm>({ weightKg: '', temperatureC: '', heartRate: '', respiratoryRate: '' });
  const [savingSoap, setSavingSoap] = useState(false);
  const [savingVitals, setSavingVitals] = useState(false);
  const [finalizing, setFinalizing] = useState(false);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  const [transcript, setTranscript] = useState('');
  const [recording, setRecording] = useState(false);
  const [speechSupported] = useState(() => getSpeechRecognitionCtor() !== null);
  const [generatingDraft, setGeneratingDraft] = useState(false);
  const [draftError, setDraftError] = useState<string | null>(null);
  const [draft, setDraft] = useState<{ subjective: string; objective: string; assessment: string; plan: string; modelConnected: boolean } | null>(null);
  const [soapAiGenerated, setSoapAiGenerated] = useState(false);
  const recognitionRef = useRef<any>(null);

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
      setSoapAiGenerated(e.aiGenerated);
      patientApi.getProfile(e.patientId).then(setProfile);
    });
  }, [encounterId]);

  useEffect(() => {
    return () => {
      recognitionRef.current?.stop?.();
    };
  }, []);

  const isReadOnly =
    encounter?.status === 'FINALIZED' || encounter?.status === 'AMENDED' || session?.role === 'TECHNICIAN';

  function flashSaved(message: string) {
    setSavedMessage(message);
    setTimeout(() => setSavedMessage(null), 2000);
  }

  function toggleRecording() {
    if (recording) {
      recognitionRef.current?.stop?.();
      setRecording(false);
      return;
    }
    const Ctor = getSpeechRecognitionCtor();
    if (!Ctor) return;
    const recognition = new Ctor();
    recognition.lang = 'tr-TR';
    recognition.continuous = true;
    recognition.interimResults = false;
    recognition.onresult = (event: any) => {
      let addition = '';
      for (let i = event.resultIndex; i < event.results.length; i++) {
        if (event.results[i].isFinal) {
          addition += event.results[i][0].transcript + ' ';
        }
      }
      if (addition.trim()) {
        setTranscript((prev) => (prev ? prev.trim() + ' ' + addition.trim() : addition.trim()));
      }
    };
    recognition.onerror = () => setRecording(false);
    recognition.onend = () => setRecording(false);
    recognitionRef.current = recognition;
    recognition.start();
    setRecording(true);
  }

  async function handleGenerateDraft() {
    if (generatingDraft || !transcript.trim()) return;
    setGeneratingDraft(true);
    setDraftError(null);
    try {
      const result = await aiApi.generateSoapDraft(transcript.trim());
      setDraft(result);
    } catch (err) {
      setDraftError(errorMessageOf(err));
    } finally {
      setGeneratingDraft(false);
    }
  }

  function applyDraft() {
    if (!draft) return;
    setSoap({ subjective: draft.subjective, objective: draft.objective, assessment: draft.assessment, plan: draft.plan });
    setSoapAiGenerated(true);
  }

  async function handleSaveSoap() {
    if (!encounterId) return;
    setSavingSoap(true);
    try {
      await encounterApi.updateSoap(encounterId, { ...soap, aiGenerated: soapAiGenerated });
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
      await encounterApi.updateSoap(encounterId, { ...soap, aiGenerated: soapAiGenerated });
      await encounterApi.finalize(encounterId);
      const updated = await encounterApi.get(encounterId);
      setEncounter(updated);
    } finally {
      setFinalizing(false);
    }
  }

  if (!encounter || !profile) {
    return (
      <AppShell>
        <div>Yükleniyor...</div>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <PatientHeaderBar profile={profile} encounter={encounter} onBack={() => navigate('/hastalar')} />

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

          <PhysicalExamCard encounterId={encounter.id} initialFindings={encounter.physicalExamFindings} readOnly={isReadOnly} />

          <div className={styles.card}>
            <div className={styles.cardTitleRow}>
              <div className={styles.cardTitle}>SOAP Notu</div>
              {soapAiGenerated && <Badge tone="ai">AI Destekli</Badge>}
            </div>
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

          <PrescriptionCard encounterId={encounter.id} patientId={encounter.patientId} readOnly={isReadOnly} />
        </div>

        <div>
          <div className={styles.aiCard}>
            <div className={styles.aiTitle}>✦ AI Scribe</div>
            <p className={styles.aiCopy}>
              Muayeneyi sesli dikte edin veya not yazın; AI Scribe SOAP alanları için taslak oluşturur.
              Üretilen her taslak hekim onayına sunulur, alanlara siz onaylamadan uygulanmaz.
            </p>

            {!speechSupported && (
              <div className={styles.aiWarning}>
                Tarayıcınız sesli dikteyi desteklemiyor (Chrome/Edge önerilir). Metni elle yazabilirsiniz.
              </div>
            )}

            <FieldWrap label="Transkript / Not">
              <Textarea
                rows={5}
                value={transcript}
                onChange={(e) => setTranscript(e.target.value)}
                placeholder="Muayene sırasında konuşulanlar veya kısa notlar..."
                disabled={isReadOnly}
              />
            </FieldWrap>

            {!isReadOnly && (
              <div className={styles.aiActions}>
                {speechSupported && (
                  <Button variant="secondary" onClick={toggleRecording}>
                    {recording ? '⏹ Dikteyi Durdur' : '🎙 Sesle Dikte Et'}
                  </Button>
                )}
                <Button variant="ai" onClick={handleGenerateDraft} disabled={generatingDraft || !transcript.trim()}>
                  {generatingDraft ? 'Oluşturuluyor...' : 'SOAP Taslağı Oluştur'}
                </Button>
              </div>
            )}

            {draftError && <div className={styles.aiError}>{draftError}</div>}

            {draft && (
              <div className={styles.draftBox}>
                {!draft.modelConnected && (
                  <div className={styles.aiWarning}>
                    Gerçek AI modeli henüz bağlı değil — bu taslak, yazdığınız metnin Subjective alanına
                    aktarılmasından ibaret. Diğer alanları elle doldurun.
                  </div>
                )}
                <div className={styles.draftLabel}>Taslak önizleme</div>
                <div className={styles.draftPreview}>{draft.subjective || '—'}</div>
                {!isReadOnly && (
                  <Button variant="ai" onClick={applyDraft}>
                    Alanlara Uygula
                  </Button>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </AppShell>
  );
}
