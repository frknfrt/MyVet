import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { aiApi, DiagnosisSuggestion, TreatmentRecommendation } from '../../api/aiApi';
import { useFeatures } from '../../auth/FeatureContext';
import { ApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { encounterApi, EncounterDetail } from '../../api/encounterApi';
import { patientApi, PatientProfile } from '../../api/patientApi';
import { AppShell } from '../../components/layout/AppShell';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Textarea } from '../../components/ui/Field';
import { MaterialsUsedCard } from './MaterialsUsedCard';
import { CriticalAlertBanner } from './AllergyConfirm';
import { PatientHeaderBar } from './PatientHeaderBar';
import { FindingsBySystem, PhysicalExamCard, toFindingsBySystem, toFindingsPayload } from './PhysicalExamCard';
import { PrescriptionCard } from './PrescriptionCard';
import { TarbilDeductionCard } from './TarbilDeductionCard';
import { Autosave, useAutosave } from './useAutosave';
import styles from './EncounterPage.module.css';

type VitalsForm = { weightKg: string; temperatureC: string; heartRate: string; respiratoryRate: string };
type SoapForm = { subjective: string; objective: string; assessment: string; plan: string };

function toVitalsPayload(v: VitalsForm) {
  return {
    weightKg: v.weightKg ? Number(v.weightKg) : null,
    temperatureC: v.temperatureC ? Number(v.temperatureC) : null,
    heartRate: v.heartRate ? Number(v.heartRate) : null,
    respiratoryRate: v.respiratoryRate ? Number(v.respiratoryRate) : null,
  };
}

/** Alt çubuktaki kayıt durumu: bölümlerin (SOAP, vital, fizik muayene) birleşik hali. */
function saveStatusOf(parts: Autosave[]): { text: string; error: boolean } {
  if (parts.some((p) => p.status === 'error')) return { text: 'Kaydedilemedi — tekrar deneniyor', error: true };
  if (parts.some((p) => p.status === 'pending' || p.status === 'saving')) return { text: 'Kaydediliyor…', error: false };
  const times = parts.map((p) => p.lastSavedAt?.getTime() ?? 0);
  const last = Math.max(...times);
  if (last > 0) {
    const hhmm = new Date(last).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
    return { text: `✓ Kaydedildi · ${hhmm}`, error: false };
  }
  return { text: 'Yazdıklarınız otomatik kaydedilir', error: false };
}

// AI SOAP taslağı önizlemesinde gösterilen alanlar (Muayene Notu kartıyla aynı başlıklar).
const DRAFT_SECTIONS: { key: 'subjective' | 'objective' | 'assessment' | 'plan'; label: string }[] = [
  { key: 'subjective', label: 'Şikâyet / Anamnez (S)' },
  { key: 'objective', label: 'Muayene Bulguları (O)' },
  { key: 'assessment', label: 'Ön Tanı (A)' },
  { key: 'plan', label: 'Tedavi Planı (P)' },
];

const SOAP_NOT_SAVED_MESSAGE =
  'Not henüz kaydedilemedi (bağlantıyı kontrol edin). AI önerisi kayıtlı nota göre hazırlanır; kayıt tamamlanınca tekrar deneyin.';

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
  const { hasFeature } = useFeatures();
  const canUseAi = hasFeature('AI_ASSISTANT');
  const canUseStock = hasFeature('INVENTORY');
  const [encounter, setEncounter] = useState<EncounterDetail | null>(null);
  const [profile, setProfile] = useState<PatientProfile | null>(null);
  const [soap, setSoap] = useState<SoapForm>({ subjective: '', objective: '', assessment: '', plan: '' });
  const [vitals, setVitals] = useState<VitalsForm>({ weightKg: '', temperatureC: '', heartRate: '', respiratoryRate: '' });
  const [findings, setFindings] = useState<FindingsBySystem>(() => toFindingsBySystem([]));
  const [finalizing, setFinalizing] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const [transcript, setTranscript] = useState('');
  const [recording, setRecording] = useState(false);
  const [speechSupported] = useState(() => getSpeechRecognitionCtor() !== null);
  const [generatingDraft, setGeneratingDraft] = useState(false);
  const [draftError, setDraftError] = useState<string | null>(null);
  const [draft, setDraft] = useState<{ subjective: string; objective: string; assessment: string; plan: string; modelConnected: boolean } | null>(null);
  const [soapAiGenerated, setSoapAiGenerated] = useState(false);
  const [draftApplied, setDraftApplied] = useState(false);
  const [generatingRecommendation, setGeneratingRecommendation] = useState(false);
  const [recommendationError, setRecommendationError] = useState<string | null>(null);
  const [recommendation, setRecommendation] = useState<TreatmentRecommendation | null>(null);
  const [recommendationDecided, setRecommendationDecided] = useState(false);
  const [decidingRecommendation, setDecidingRecommendation] = useState(false);
  const [generatingDiagnosis, setGeneratingDiagnosis] = useState(false);
  const [diagnosisError, setDiagnosisError] = useState<string | null>(null);
  const [diagnosis, setDiagnosis] = useState<DiagnosisSuggestion | null>(null);
  const [diagnosisDecided, setDiagnosisDecided] = useState(false);
  const [decidingDiagnosis, setDecidingDiagnosis] = useState(false);
  const recognitionRef = useRef<any>(null);

  const isReadOnly =
    encounter?.status === 'FINALIZED' ||
    encounter?.status === 'AMENDED' ||
    encounter?.status === 'CANCELLED' ||
    session?.role === 'TECHNICIAN';
  const canEdit = encounter !== null && !isReadOnly;

  // Otomatik kayıt: SOAP ve vital yazmaya 1,5 sn ara verince, fizik muayene seçimi hemen kaydedilir.
  const soapPayload = useMemo(() => ({ ...soap, aiGenerated: soapAiGenerated }), [soap, soapAiGenerated]);
  const vitalsPayload = useMemo(() => toVitalsPayload(vitals), [vitals]);
  const examPayload = useMemo(() => toFindingsPayload(findings), [findings]);
  const soapSave = useAutosave(soapPayload, (v) => encounterApi.updateSoap(encounterId!, v), {
    delayMs: 1500,
    enabled: canEdit,
  });
  const vitalsSave = useAutosave(vitalsPayload, (v) => encounterApi.updateVitals(encounterId!, v), {
    delayMs: 1500,
    enabled: canEdit,
  });
  const examSave = useAutosave(examPayload, (v) => encounterApi.updatePhysicalExam(encounterId!, v), {
    delayMs: 400,
    enabled: canEdit,
  });
  const autosaves = [soapSave, vitalsSave, examSave];
  const anyDirty = autosaves.some((a) => a.dirty);
  const saveStatus = saveStatusOf(autosaves);

  async function flushAll() {
    await Promise.all(autosaves.map((a) => a.flush()));
  }

  useEffect(() => {
    if (!encounterId) return;
    encounterApi.get(encounterId).then((e) => {
      setEncounter(e);
      const loaded = {
        subjective: e.subjective ?? '',
        objective: e.objective ?? '',
        assessment: e.assessment ?? '',
        plan: e.plan ?? '',
      };
      const loadedVitals = {
        weightKg: e.weightKg?.toString() ?? '',
        temperatureC: e.temperatureC?.toString() ?? '',
        heartRate: e.heartRate?.toString() ?? '',
        respiratoryRate: e.respiratoryRate?.toString() ?? '',
      };
      const loadedFindings = toFindingsBySystem(e.physicalExamFindings);
      setSoap(loaded);
      setVitals(loadedVitals);
      setFindings(loadedFindings);
      setSoapAiGenerated(e.aiGenerated);
      // Yüklenen değerler "kayıtlı" sayılır; yalnız sonradan yapılan değişiklikler gönderilir.
      soapSave.markSaved({ ...loaded, aiGenerated: e.aiGenerated });
      vitalsSave.markSaved(toVitalsPayload(loadedVitals));
      examSave.markSaved(toFindingsPayload(loadedFindings));
      patientApi.getProfile(e.patientId).then(setProfile);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [encounterId]);

  // Kaydedilmemiş değişiklik varken sekme kapatılırsa tarayıcı uyarsın.
  useEffect(() => {
    if (!anyDirty) return;
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [anyDirty]);

  useEffect(() => {
    return () => {
      recognitionRef.current?.stop?.();
    };
  }, []);

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
    // Her yeni dikte boş not alanıyla başlar (eski metnin devamına eklenmez).
    setTranscript('');
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
    setDraftApplied(true);
    setTimeout(() => setDraftApplied(false), 3000);
  }

  async function handleGenerateRecommendation() {
    if (generatingRecommendation || !soap.assessment.trim() || !encounterId) return;
    try {
      await soapSave.flush();
    } catch {
      setRecommendationError(SOAP_NOT_SAVED_MESSAGE);
      return;
    }
    setGeneratingRecommendation(true);
    setRecommendationError(null);
    try {
      const result = await aiApi.generateTreatmentRecommendation(encounterId);
      setRecommendation(result);
      setRecommendationDecided(false);
    } catch (err) {
      setRecommendationError(errorMessageOf(err));
    } finally {
      setGeneratingRecommendation(false);
    }
  }

  async function acceptRecommendation() {
    if (!recommendation || decidingRecommendation) return;
    setDecidingRecommendation(true);
    try {
      await aiApi.decideTreatmentRecommendation(recommendation.aiJobId, 'ACCEPTED_AS_IS');
      setSoap((s) => ({ ...s, plan: recommendation.suggestionText }));
      setSoapAiGenerated(true);
      setRecommendationDecided(true);
      setTimeout(() => setRecommendation(null), 3000);
    } catch (err) {
      setRecommendationError(errorMessageOf(err));
    } finally {
      setDecidingRecommendation(false);
    }
  }

  async function rejectRecommendation() {
    if (!recommendation || decidingRecommendation) return;
    setDecidingRecommendation(true);
    try {
      await aiApi.decideTreatmentRecommendation(recommendation.aiJobId, 'REJECTED');
      setRecommendation(null);
    } catch (err) {
      setRecommendationError(errorMessageOf(err));
    } finally {
      setDecidingRecommendation(false);
    }
  }

  async function handleGenerateDiagnosis() {
    const hasFindings = soap.subjective.trim() || soap.objective.trim();
    if (generatingDiagnosis || !hasFindings || !encounterId) return;
    try {
      await flushAll();
    } catch {
      setDiagnosisError(SOAP_NOT_SAVED_MESSAGE);
      return;
    }
    setGeneratingDiagnosis(true);
    setDiagnosisError(null);
    try {
      const result = await aiApi.generateDiagnosisSuggestion(encounterId);
      setDiagnosis(result);
      setDiagnosisDecided(false);
    } catch (err) {
      setDiagnosisError(errorMessageOf(err));
    } finally {
      setGeneratingDiagnosis(false);
    }
  }

  async function acceptDiagnosis() {
    if (!diagnosis || decidingDiagnosis) return;
    setDecidingDiagnosis(true);
    try {
      await aiApi.decideDiagnosisSuggestion(diagnosis.aiJobId, 'ACCEPTED_AS_IS');
      setSoap((s) => ({ ...s, assessment: diagnosis.suggestionText }));
      setSoapAiGenerated(true);
      setDiagnosisDecided(true);
      setTimeout(() => setDiagnosis(null), 3000);
    } catch (err) {
      setDiagnosisError(errorMessageOf(err));
    } finally {
      setDecidingDiagnosis(false);
    }
  }

  async function rejectDiagnosis() {
    if (!diagnosis || decidingDiagnosis) return;
    setDecidingDiagnosis(true);
    try {
      await aiApi.decideDiagnosisSuggestion(diagnosis.aiJobId, 'REJECTED');
      setDiagnosis(null);
    } catch (err) {
      setDiagnosisError(errorMessageOf(err));
    } finally {
      setDecidingDiagnosis(false);
    }
  }

  async function handleFinalize() {
    if (!encounterId) return;
    if (!window.confirm('Muayeneyi tamamlamak istediğinizden emin misiniz? Tamamlandıktan sonra not kilitlenir.')) {
      return;
    }
    setFinalizing(true);
    setActionError(null);
    try {
      // Önce bekleyen değişiklikler gönderilir; kaydedilemeyen not ile kesinleştirilmez.
      await flushAll();
      await encounterApi.finalize(encounterId);
      const updated = await encounterApi.get(encounterId);
      setEncounter(updated);
    } catch (err) {
      setActionError(errorMessageOf(err));
    } finally {
      setFinalizing(false);
    }
  }

  async function handleCancel() {
    if (!encounterId || !encounter) return;
    const name = profile?.name ?? 'Bu hasta';
    if (
      !window.confirm(
        `${name} için açılan muayene iptal edilecek. Yazılan notlar hasta geçmişinde görünmeyecek, ` +
          'iptal işlem geçmişine kaydedilir. Devam edilsin mi?',
      )
    ) {
      return;
    }
    setCancelling(true);
    setActionError(null);
    try {
      await flushAll().catch(() => undefined);
      await encounterApi.cancel(encounterId);
      navigate(`/hastalar/${encounter.patientId}`);
    } catch (err) {
      setActionError(errorMessageOf(err));
      setCancelling(false);
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
      <CriticalAlertBanner text={profile.criticalAlert} />

      {encounter.status === 'CANCELLED' && (
        <div className={styles.cancelledBanner}>
          Bu muayene iptal edildi. Kayıt yalnızca görüntülenebilir; hasta geçmişinde gösterilmez.
        </div>
      )}

      <div className={styles.layout}>
        <div>
          <div className={styles.card}>
            <div className={styles.cardTitleRow}>
              <div className={styles.cardTitle}>Muayene Notu (SOAP)</div>
              {soapAiGenerated && <Badge tone="ai">AI Destekli</Badge>}
            </div>
            <div className={styles.soapGrid}>
              <FieldWrap label="Şikâyet / Anamnez (S)">
                <Textarea
                  value={soap.subjective}
                  disabled={isReadOnly}
                  onChange={(e) => setSoap((s) => ({ ...s, subjective: e.target.value }))}
                  placeholder="Sahibin ifadesi, şikayetin öyküsü..."
                />
              </FieldWrap>
              <FieldWrap label="Muayene Bulguları (O)">
                <Textarea
                  value={soap.objective}
                  disabled={isReadOnly}
                  onChange={(e) => setSoap((s) => ({ ...s, objective: e.target.value }))}
                  placeholder="Fizik muayene bulguları..."
                />
              </FieldWrap>
              <FieldWrap label="Ön Tanı (A)">
                <Textarea
                  value={soap.assessment}
                  disabled={isReadOnly}
                  onChange={(e) => setSoap((s) => ({ ...s, assessment: e.target.value }))}
                  placeholder="Değerlendirme / ön tanı..."
                />
              </FieldWrap>
              <FieldWrap label="Tedavi Planı (P)">
                <Textarea
                  value={soap.plan}
                  disabled={isReadOnly}
                  onChange={(e) => setSoap((s) => ({ ...s, plan: e.target.value }))}
                  placeholder="Tedavi planı, reçete, kontrol tarihi..."
                />
              </FieldWrap>
            </div>
          </div>

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
          </div>

          <PhysicalExamCard findings={findings} onChange={setFindings} readOnly={isReadOnly} />

          {canUseAi && (
            <div className={styles.aiCard}>
              <div className={styles.aiTitle}>✦ AI Tanı Desteği</div>
              <p className={styles.aiCopy}>
                Şikâyet ve Muayene Bulguları alanlarına, vital bulgulara ve fiziksel muayeneye dayanarak olası
                tanı/ayırıcı tanı önerisi üretir. Öneri hekim onayına sunulur, Ön Tanı alanına siz
                onaylamadan uygulanmaz — kesin tanı koymaz.
              </p>

              {!(soap.subjective.trim() || soap.objective.trim()) && (
                <p className={styles.aiCopy}>Önce Şikâyet veya Muayene Bulguları alanını doldurun.</p>
              )}

              {!isReadOnly && (
                <div className={styles.aiActions}>
                  <Button
                    variant="ai"
                    onClick={handleGenerateDiagnosis}
                    disabled={generatingDiagnosis || !(soap.subjective.trim() || soap.objective.trim())}
                  >
                    {generatingDiagnosis ? 'Oluşturuluyor...' : 'Tanı Desteği Al'}
                  </Button>
                </div>
              )}

              {generatingDiagnosis && (
                <p className={styles.aiCopy}>AI önerisi hazırlanıyor, bu işlem ~30-60 saniye sürebilir…</p>
              )}

              {diagnosisError && <div className={styles.aiError}>{diagnosisError}</div>}

              {diagnosis && (
                <div className={styles.draftBox}>
                  <div className={styles.aiWarning}>
                    Bu bir yapay zekâ taslağıdır; tıbbi literatürden kaynak göstermez ve kesin tanı yerine geçmez.
                    Son karar hekimindir.
                  </div>
                  {!diagnosis.modelConnected && (
                    <div className={styles.aiWarning}>
                      Gerçek AI modeli henüz bağlı değil — bu, kullanılamaz bir yer tutucu metindir. Lütfen
                      Ön Tanı alanını elle girin.
                    </div>
                  )}
                  <div className={styles.draftLabel}>Öneri önizleme</div>
                  <div className={styles.draftPreview}>{diagnosis.suggestionText || '—'}</div>
                  {!isReadOnly && !diagnosisDecided && (
                    <div className={styles.aiActions}>
                      {diagnosis.modelConnected && (
                        <Button variant="ai" onClick={acceptDiagnosis} disabled={decidingDiagnosis}>
                          {decidingDiagnosis ? 'İşleniyor...' : 'Aynen Kabul Et'}
                        </Button>
                      )}
                      <Button variant="secondary" onClick={rejectDiagnosis} disabled={decidingDiagnosis}>
                        Reddet
                      </Button>
                    </div>
                  )}
                  {diagnosisDecided && (
                    <p className={styles.aiCopy}>
                      ✓ Ön Tanı alanına uygulandı ve otomatik kaydedilecek — kontrol edin.
                    </p>
                  )}
                </div>
              )}
            </div>
          )}

          {canUseAi && (
            <div className={styles.aiCard}>
              <div className={styles.aiTitle}>✦ AI Tedavi Önerisi</div>
              <p className={styles.aiCopy}>
                Ön Tanı alanına ve hastanın son muayenelerine dayanarak bir tedavi planı önerisi üretir.
                Öneri hekim onayına sunulur, Tedavi Planı alanına siz onaylamadan uygulanmaz.
              </p>

              {!soap.assessment.trim() && (
                <p className={styles.aiCopy}>Önce Ön Tanı alanını doldurun.</p>
              )}

              {!isReadOnly && (
                <div className={styles.aiActions}>
                  <Button
                    variant="ai"
                    onClick={handleGenerateRecommendation}
                    disabled={generatingRecommendation || !soap.assessment.trim()}
                  >
                    {generatingRecommendation ? 'Oluşturuluyor...' : 'Tedavi Önerisi Al'}
                  </Button>
                </div>
              )}

              {generatingRecommendation && (
                <p className={styles.aiCopy}>AI önerisi hazırlanıyor, bu işlem ~30-60 saniye sürebilir…</p>
              )}

              {recommendationError && <div className={styles.aiError}>{recommendationError}</div>}

              {recommendation && (
                <div className={styles.draftBox}>
                  <div className={styles.aiWarning}>
                    Bu bir yapay zekâ taslağıdır; tıbbi literatürden veya ilaç rehberinden kaynak göstermez.
                    Doz ve ilaç seçimini kontrol edin; son karar hekimindir.
                  </div>
                  {!recommendation.modelConnected && (
                    <div className={styles.aiWarning}>
                      Gerçek AI modeli henüz bağlı değil — bu, kullanılamaz bir yer tutucu metindir. Lütfen tedavi
                      planını elle girin.
                    </div>
                  )}
                  <div className={styles.draftLabel}>Öneri önizleme</div>
                  <div className={styles.draftPreview}>{recommendation.suggestionText || '—'}</div>
                  {!isReadOnly && !recommendationDecided && (
                    <div className={styles.aiActions}>
                      {recommendation.modelConnected && (
                        <Button variant="ai" onClick={acceptRecommendation} disabled={decidingRecommendation}>
                          {decidingRecommendation ? 'İşleniyor...' : 'Aynen Kabul Et'}
                        </Button>
                      )}
                      <Button variant="secondary" onClick={rejectRecommendation} disabled={decidingRecommendation}>
                        Reddet
                      </Button>
                    </div>
                  )}
                  {recommendationDecided && (
                    <p className={styles.aiCopy}>
                      ✓ Tedavi Planı alanına uygulandı ve otomatik kaydedilecek — kontrol edin.
                    </p>
                  )}
                </div>
              )}
            </div>
          )}

          {canUseStock && <MaterialsUsedCard encounterId={encounter.id} patientId={encounter.patientId} readOnly={isReadOnly} />}

          {canUseStock && (encounter.status === 'FINALIZED' || encounter.status === 'AMENDED') && (
            <TarbilDeductionCard encounterId={encounter.id} canAct={session?.role === 'VET' || session?.role === 'ADMIN'} />
          )}

          <PrescriptionCard encounterId={encounter.id} patientId={encounter.patientId} readOnly={isReadOnly} />
        </div>

        <div>
          {canUseAi && (
            <div className={styles.aiCard}>
              <div className={styles.aiTitle}>✦ Sesli Not</div>
              <p className={styles.aiCopy}>
                Muayeneyi sesli dikte edin veya not yazın; yapay zekâ bu nottan SOAP alanları için taslak oluşturur.
                Üretilen her taslak hekim onayına sunulur, alanlara siz onaylamadan uygulanmaz.
              </p>

              {!speechSupported && (
                <div className={styles.aiWarning}>
                  Tarayıcınız sesli dikteyi desteklemiyor (Chrome/Edge önerilir). Metni elle yazabilirsiniz.
                </div>
              )}

              <FieldWrap label="Not">
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

              {generatingDraft && (
                <p className={styles.aiCopy}>AI taslağı hazırlanıyor, bu işlem ~30-40 saniye sürebilir…</p>
              )}

              {draftError && <div className={styles.aiError}>{draftError}</div>}

              {draft && (
                <div className={styles.draftBox}>
                  {!draft.modelConnected && (
                    <div className={styles.aiWarning}>
                      Gerçek AI modeli henüz bağlı değil — bu taslak, yazdığınız metnin Şikâyet alanına
                      aktarılmasından ibaret. Diğer alanları elle doldurun.
                    </div>
                  )}
                  <div className={styles.draftLabel}>Taslak önizleme</div>
                  <div className={styles.draftPreview}>
                    {DRAFT_SECTIONS.map(({ key, label }) => (
                      <div key={key} className={styles.draftSection}>
                        <div className={styles.draftSectionTitle}>{label}</div>
                        <div>{draft[key] || '—'}</div>
                      </div>
                    ))}
                  </div>
                  {!isReadOnly && (
                    <Button variant="ai" onClick={applyDraft}>
                      Alanlara Uygula
                    </Button>
                  )}
                  {draftApplied && (
                    <p className={styles.aiCopy}>
                      ✓ "Muayene Notu" kartına uygulandı ve otomatik kaydedilecek — kontrol edin.
                    </p>
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {canEdit && (
        <div className={styles.stickyFooter}>
          <span className={saveStatus.error ? styles.saveStatusError : styles.saveStatus}>{saveStatus.text}</span>
          {actionError && <span className={styles.footerError}>{actionError}</span>}
          <div className={styles.footerActions}>
            <Button variant="tertiary" onClick={handleCancel} disabled={cancelling || finalizing}>
              {cancelling ? 'İptal ediliyor...' : 'Muayeneyi İptal Et'}
            </Button>
            <Button variant="primary" onClick={handleFinalize} disabled={finalizing || cancelling}>
              {finalizing ? 'Tamamlanıyor...' : 'Muayeneyi Tamamla'}
            </Button>
          </div>
        </div>
      )}
    </AppShell>
  );
}
