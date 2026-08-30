import { ReactNode, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { AppShell } from '../../components/layout/AppShell';
import { ApiError } from '../../api/client';
import { encounterApi, EncounterDetail } from '../../api/encounterApi';
import { clinicalApi, Prescription } from '../../api/clinicalApi';
import { labApi, LabResultSummary } from '../../api/labApi';
import { imagingApi, ImagingRecordSummary } from '../../api/imagingApi';
import { vaccinationApi, VaccinationScheduleItem } from '../../api/vaccinationApi';
import { patientApi, PatientProfile } from '../../api/patientApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { LineChart } from '../../components/ui/LineChart';
import { colorFor, initialsOf } from '../dashboard/avatarColor';
import { ageLabelFrom, formatDate } from '../encounter/ageUtils';
import { EncounterStatusBadge } from '../encounter/encounterStatus';
import { ImagingRecordDetailModal } from '../imaging/ImagingRecordDetailModal';
import { ImagingStatusBadge, MODALITY_LABELS } from '../imaging/imagingStatus';
import { LabResultDetailModal } from '../laboratory/LabResultDetailModal';
import { LabResultStatusBadge } from '../laboratory/labResultStatus';
import { VaccinationStatusBadge } from '../vaccinations/vaccinationStatus';
import { PatientEditModal } from './PatientEditModal';
import { buildPatientTimeline, buildWeightTrend, shortDateLabel, TimelineEntry } from './patientTimeline';
import { PatientStatusBadge } from './statusBadge';
import styles from './PatientDetailPage.module.css';

type Tab = 'genel-bakis' | 'muayeneler' | 'asilar' | 'receteler' | 'lab' | 'goruntuleme';

const TIMELINE_TYPE_LABELS: Record<TimelineEntry['type'], string> = {
  encounter: 'Muayene',
  vaccination: 'Aşı',
  prescription: 'Reçete',
  lab: 'Lab',
  imaging: 'Görüntüleme',
};

const SEX_LABELS: Record<string, string> = { MALE: 'Erkek', FEMALE: 'Dişi', UNKNOWN: 'Bilinmiyor' };

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

function InfoItem({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className={styles.infoItem}>
      <div className={styles.infoLabel}>{label}</div>
      <div className={styles.infoValue}>{value}</div>
    </div>
  );
}

export function PatientDetailPage() {
  const { patientId } = useParams<{ patientId: string }>();
  const navigate = useNavigate();
  const { session } = useAuth();
  // PatientsController /patients yazma -- VET/RECEPTIONIST/ADMIN (TECHNICIAN yok).
  const canWritePatient = session ? ['VET', 'RECEPTIONIST', 'ADMIN'].includes(session.role) : false;
  // EncountersController POST /encounters -- sadece VET/ADMIN.
  const canStartEncounter = session?.role === 'VET' || session?.role === 'ADMIN';

  const [profile, setProfile] = useState<PatientProfile | null>(null);
  const [tab, setTab] = useState<Tab>('genel-bakis');
  const [encounters, setEncounters] = useState<EncounterDetail[]>([]);
  const [vaccinations, setVaccinations] = useState<VaccinationScheduleItem[] | null>(null);
  const [prescriptions, setPrescriptions] = useState<Prescription[] | null>(null);
  const [prescriptionsForbidden, setPrescriptionsForbidden] = useState(false);
  const [labResults, setLabResults] = useState<LabResultSummary[] | null>(null);
  const [selectedLabResultId, setSelectedLabResultId] = useState<string | null>(null);
  const [imagingRecords, setImagingRecords] = useState<ImagingRecordSummary[] | null>(null);
  const [selectedImagingRecordId, setSelectedImagingRecordId] = useState<string | null>(null);
  const [draftEncounterId, setDraftEncounterId] = useState<string | null>(null);
  const [startingEncounter, setStartingEncounter] = useState(false);
  const [busy, setBusy] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function load() {
    if (!patientId) return;
    patientApi.getProfile(patientId).then(setProfile);
    encounterApi.listByPatient(patientId).then((list) => {
      setEncounters(list);
      setDraftEncounterId(list.find((e) => e.status === 'DRAFT')?.id ?? null);
    });
    vaccinationApi.list(patientId).then(setVaccinations);
    clinicalApi
      .listPrescriptionsByPatient(patientId)
      .then(setPrescriptions)
      .catch((err) => {
        if (err instanceof ApiError && err.status === 403) setPrescriptionsForbidden(true);
      });
    labApi.list(patientId).then(setLabResults);
    imagingApi.list(patientId).then(setImagingRecords);
  }

  useEffect(() => {
    setError(null);
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [patientId]);

  async function handleStartEncounter() {
    if (!patientId) return;
    setStartingEncounter(true);
    setError(null);
    try {
      const encounterId = draftEncounterId ?? (await encounterApi.start({ patientId }));
      navigate(`/muayene/${encounterId}`);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setStartingEncounter(false);
    }
  }

  async function handleMarkDeceased() {
    if (!patientId) return;
    setBusy(true);
    setError(null);
    try {
      await patientApi.markDeceased(patientId);
      load();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  if (!patientId) return null;

  if (!profile) {
    return (
      <AppShell>
        <div className={styles.loading}>Yükleniyor...</div>
      </AppShell>
    );
  }

  const age = ageLabelFrom(profile.birthDate);
  const weightTrend = buildWeightTrend(encounters);
  const latestVitalsEncounter = encounters.find(
    (e) => e.weightKg != null || e.temperatureC != null || e.heartRate != null || e.respiratoryRate != null
  );
  const timeline = buildPatientTimeline(
    encounters,
    vaccinations ?? [],
    prescriptions ?? [],
    labResults ?? [],
    imagingRecords ?? []
  );

  return (
    <AppShell>
      <button className={styles.backLink} onClick={() => navigate('/hastalar')}>
        ← Hastalar &amp; Sahipler
      </button>

      {profile.criticalAlert && <div className={styles.criticalAlert}>⚠ {profile.criticalAlert}</div>}
      {error && <div className={styles.errorBanner}>{error}</div>}

      <div className={styles.headerCard}>
        <div className={styles.headerTop}>
          <div className={styles.avatar} style={{ background: colorFor(profile.id) }}>
            {initialsOf(profile.name)}
          </div>
          <div className={styles.identity}>
            <div className={styles.nameRow}>
              <span className={styles.name}>{profile.name}</span>
              <PatientStatusBadge status={profile.status} />
              {profile.aggressive && <Badge tone="danger">Saldırgan</Badge>}
            </div>
            <button className={styles.ownerLink} onClick={() => navigate(`/musteriler/${profile.ownerId}`)}>
              {profile.ownerFullName} · {profile.ownerPhone}
            </button>
          </div>
          <div className={styles.spacer} />
          <div className={styles.headerActions}>
            {canWritePatient && (
              <Button variant="secondary" onClick={() => setEditOpen(true)}>
                Düzenle
              </Button>
            )}
            {profile.status === 'ACTIVE' && canStartEncounter && (
              <Button variant="primary" onClick={handleStartEncounter} disabled={startingEncounter}>
                {startingEncounter ? 'Açılıyor...' : draftEncounterId ? 'Muayeneye Devam Et' : 'Muayene Başlat'}
              </Button>
            )}
          </div>
        </div>

        <div className={styles.infoGrid}>
          <InfoItem label="Tür / Irk" value={[profile.speciesName, profile.breedName].filter(Boolean).join(' · ') || '—'} />
          <InfoItem label="Cinsiyet" value={SEX_LABELS[profile.sex ?? 'UNKNOWN']} />
          <InfoItem
            label="Doğum Tarihi"
            value={profile.birthDate ? `${formatDate(profile.birthDate)}${age ? ` · ${age}` : ''}` : '—'}
          />
          <InfoItem label="Renk" value={profile.color ?? '—'} />
          <InfoItem label="Kısırlaştırma" value={profile.neutered ? 'Evet' : 'Hayır'} />
          <InfoItem label="Ağırlık" value={profile.weightKg != null ? `${profile.weightKg} kg` : '—'} />
          <InfoItem label="Mikroçip No" value={profile.microchipNumber ?? '—'} />
          <InfoItem label="TARBİL Kimlik No" value={profile.tarbilAnimalId ?? '—'} />
          <InfoItem label="Kuduz Küpe No" value={profile.rabiesTag ?? '—'} />
          <InfoItem label="Kan Grubu" value={profile.bloodType ?? '—'} />
          <InfoItem label="Kullanılan Mama" value={profile.foodBrand ?? '—'} />
          <InfoItem label="Protokol No" value={profile.protocolNumber ?? '—'} />
          <InfoItem label="Huyu" value={profile.temperament ?? '—'} />
          <InfoItem label="Ayırt Edici Özelliği" value={profile.distinguishingMarks ?? '—'} />
        </div>

        {profile.notes && (
          <div className={styles.notesBlock}>
            <div className={styles.infoLabel}>Notlar</div>
            <div className={styles.notesText}>{profile.notes}</div>
          </div>
        )}

        {profile.status === 'ACTIVE' && canWritePatient && (
          <div className={styles.dangerRow}>
            <Button variant="danger" onClick={handleMarkDeceased} disabled={busy}>
              {busy ? 'İşleniyor...' : 'Vefat etti olarak işaretle'}
            </Button>
          </div>
        )}
      </div>

      <div className={styles.tabs}>
        <div className={`${styles.tab} ${tab === 'genel-bakis' ? styles.tabActive : ''}`} onClick={() => setTab('genel-bakis')}>
          Genel Bakış
        </div>
        <div className={`${styles.tab} ${tab === 'muayeneler' ? styles.tabActive : ''}`} onClick={() => setTab('muayeneler')}>
          Muayene Geçmişi
        </div>
        <div className={`${styles.tab} ${tab === 'asilar' ? styles.tabActive : ''}`} onClick={() => setTab('asilar')}>
          Aşılar
        </div>
        <div className={`${styles.tab} ${tab === 'receteler' ? styles.tabActive : ''}`} onClick={() => setTab('receteler')}>
          Reçeteler
        </div>
        <div className={`${styles.tab} ${tab === 'lab' ? styles.tabActive : ''}`} onClick={() => setTab('lab')}>
          Lab Sonuçları
        </div>
        <div className={`${styles.tab} ${tab === 'goruntuleme' ? styles.tabActive : ''}`} onClick={() => setTab('goruntuleme')}>
          Görüntüleme
        </div>
      </div>

      {tab === 'genel-bakis' && (
        <>
          <div className={styles.tableCard}>
            <div className={styles.summaryHead}>Sağlık Özeti</div>
            <div className={styles.summaryBody}>
              {latestVitalsEncounter ? (
                <div className={styles.vitalsSummaryGrid}>
                  <InfoItem
                    label="Son Kilo"
                    value={latestVitalsEncounter.weightKg != null ? `${latestVitalsEncounter.weightKg} kg` : '—'}
                  />
                  <InfoItem
                    label="Son Ateş"
                    value={latestVitalsEncounter.temperatureC != null ? `${latestVitalsEncounter.temperatureC} °C` : '—'}
                  />
                  <InfoItem
                    label="Son Nabız"
                    value={latestVitalsEncounter.heartRate != null ? `${latestVitalsEncounter.heartRate} bpm` : '—'}
                  />
                  <InfoItem
                    label="Son Solunum"
                    value={latestVitalsEncounter.respiratoryRate != null ? `${latestVitalsEncounter.respiratoryRate} /dk` : '—'}
                  />
                </div>
              ) : (
                <div className={styles.empty}>Henüz vital bulgu kaydı yok</div>
              )}
              {weightTrend.length >= 2 ? (
                <div className={styles.chartWrap}>
                  <div className={styles.infoLabel}>Kilo Trendi</div>
                  <LineChart
                    labels={weightTrend.map((p) => shortDateLabel(p.date))}
                    values={weightTrend.map((p) => p.weightKg)}
                    height={140}
                  />
                </div>
              ) : (
                <div className={styles.chartWrap}>
                  <div className={styles.infoLabel}>Kilo Trendi</div>
                  <div className={styles.empty}>Trend için en az 2 muayenede kilo kaydı gerekiyor</div>
                </div>
              )}
            </div>
          </div>

          <div className={`${styles.tableCard} ${styles.timelineCard}`}>
            <div className={styles.summaryHead}>Zaman Çizelgesi</div>
            {timeline.length === 0 ? (
              <div className={styles.empty}>Henüz klinik kayıt yok</div>
            ) : (
              timeline.map((entry) => {
                const clickable = entry.type === 'encounter' || entry.type === 'lab' || entry.type === 'imaging';
                return (
                  <div
                    key={`${entry.type}-${entry.record.id}`}
                    className={`${styles.row} ${styles.timelineRow} ${clickable ? '' : styles.timelineRowStatic}`}
                    onClick={() => {
                      if (entry.type === 'encounter') navigate(`/muayene/${entry.record.id}`);
                      if (entry.type === 'lab') setSelectedLabResultId(entry.record.id);
                      if (entry.type === 'imaging') setSelectedImagingRecordId(entry.record.id);
                    }}
                  >
                    <div className={styles.muted}>{new Date(entry.date).toLocaleDateString('tr-TR')}</div>
                    <div className={styles.timelineType}>{TIMELINE_TYPE_LABELS[entry.type]}</div>
                    <div>
                      {entry.type === 'encounter' && (entry.record.assessment || `Dr. ${entry.record.staffName}`)}
                      {entry.type === 'vaccination' && entry.record.vaccineName}
                      {entry.type === 'prescription' && `${entry.record.items.length} ilaç`}
                      {entry.type === 'lab' && entry.record.testName}
                      {entry.type === 'imaging' &&
                        `${MODALITY_LABELS[entry.record.modality]}${entry.record.bodyRegion ? ` · ${entry.record.bodyRegion}` : ''}`}
                    </div>
                    <div>
                      {entry.type === 'encounter' && <EncounterStatusBadge status={entry.record.status} />}
                      {entry.type === 'vaccination' && <VaccinationStatusBadge status={entry.record.status} />}
                      {entry.type === 'lab' && <LabResultStatusBadge status={entry.record.status} />}
                      {entry.type === 'imaging' && <ImagingStatusBadge status={entry.record.status} />}
                      {entry.type === 'prescription' && (
                        <Badge tone={entry.record.status === 'ACTIVE' ? 'success' : entry.record.status === 'FULFILLED' ? 'neutral' : 'danger'}>
                          {entry.record.status === 'ACTIVE' ? 'Aktif' : entry.record.status === 'FULFILLED' ? 'Kullanıldı' : 'İptal'}
                        </Badge>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </>
      )}

      {tab === 'muayeneler' && (
        <div className={styles.tableCard}>
          <div className={`${styles.tableHead} ${styles.encountersHead}`}>
            <div>Tarih</div>
            <div>Hekim</div>
            <div>Durum</div>
            <div>Değerlendirme</div>
          </div>
          {encounters.length === 0 ? (
            <div className={styles.empty}>Muayene kaydı yok</div>
          ) : (
            encounters.map((e) => (
              <div
                key={e.id}
                className={`${styles.row} ${styles.encountersRow}`}
                onClick={() => navigate(`/muayene/${e.id}`)}
              >
                <div>{new Date(e.encounterDate).toLocaleDateString('tr-TR')}</div>
                <div className={styles.muted}>{e.staffName}</div>
                <div>
                  <EncounterStatusBadge status={e.status} />
                </div>
                <div className={styles.muted}>{e.assessment ?? '—'}</div>
              </div>
            ))
          )}
        </div>
      )}

      {tab === 'asilar' && (
        <div className={styles.tableCard}>
          <div className={`${styles.tableHead} ${styles.vaccinesHead}`}>
            <div>Aşı</div>
            <div>Tarih</div>
            <div>Sonraki Tarih</div>
            <div>Durum</div>
          </div>
          {vaccinations === null ? (
            <div className={styles.empty}>Yükleniyor...</div>
          ) : vaccinations.length === 0 ? (
            <div className={styles.empty}>Aşı kaydı yok</div>
          ) : (
            vaccinations.map((v) => (
              <div key={v.id} className={`${styles.row} ${styles.vaccinesRow}`}>
                <div>{v.vaccineName}</div>
                <div className={styles.muted}>{new Date(v.administeredDate).toLocaleDateString('tr-TR')}</div>
                <div className={styles.muted}>{v.nextDueDate ? new Date(v.nextDueDate).toLocaleDateString('tr-TR') : '—'}</div>
                <div>
                  <VaccinationStatusBadge status={v.status} />
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {tab === 'receteler' && (
        <div className={styles.tableCard}>
          {prescriptionsForbidden ? (
            <div className={styles.empty}>Bu bölümü görüntüleme yetkiniz yok</div>
          ) : prescriptions === null ? (
            <div className={styles.empty}>Yükleniyor...</div>
          ) : prescriptions.length === 0 ? (
            <div className={styles.empty}>Reçete kaydı yok</div>
          ) : (
            <div className={styles.prescriptionList}>
              {prescriptions.map((p) => (
                <div key={p.id} className={styles.prescriptionCard}>
                  <div className={styles.prescriptionHead}>
                    <span>{new Date(p.issuedDate).toLocaleDateString('tr-TR')}</span>
                    {p.controlledSubstance && <Badge tone="warning">Kontrollü</Badge>}
                    <span className={styles.spacer} />
                    <Badge tone={p.status === 'ACTIVE' ? 'success' : p.status === 'FULFILLED' ? 'neutral' : 'danger'}>
                      {p.status === 'ACTIVE' ? 'Aktif' : p.status === 'FULFILLED' ? 'Kullanıldı' : 'İptal'}
                    </Badge>
                  </div>
                  <ul className={styles.prescriptionItems}>
                    {p.items.map((item, i) => (
                      <li key={i}>
                        <strong>{item.drugName ?? 'İlaç'}</strong> — {item.dosage}, {item.frequency}, {item.durationDays} gün ({item.route})
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {tab === 'lab' && (
        <div className={styles.tableCard}>
          <div className={`${styles.tableHead} ${styles.labHead}`}>
            <div>Test</div>
            <div>İstek Tarihi</div>
            <div>Sonuç Tarihi</div>
            <div>Durum</div>
          </div>
          {labResults === null ? (
            <div className={styles.empty}>Yükleniyor...</div>
          ) : labResults.length === 0 ? (
            <div className={styles.empty}>Laboratuvar sonucu yok</div>
          ) : (
            labResults.map((r) => (
              <div key={r.id} className={`${styles.row} ${styles.labRow}`} onClick={() => setSelectedLabResultId(r.id)}>
                <div>{r.testName}</div>
                <div className={styles.muted}>{new Date(r.requestedAt).toLocaleDateString('tr-TR')}</div>
                <div className={styles.muted}>{r.resultedAt ? new Date(r.resultedAt).toLocaleDateString('tr-TR') : '—'}</div>
                <div>
                  <LabResultStatusBadge status={r.status} />
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {tab === 'goruntuleme' && (
        <div className={styles.tableCard}>
          <div className={`${styles.tableHead} ${styles.imagingHead}`}>
            <div>Modalite / Bölge</div>
            <div>İstek Tarihi</div>
            <div>Sonuç Tarihi</div>
            <div>Durum</div>
          </div>
          {imagingRecords === null ? (
            <div className={styles.empty}>Yükleniyor...</div>
          ) : imagingRecords.length === 0 ? (
            <div className={styles.empty}>Görüntüleme kaydı yok</div>
          ) : (
            imagingRecords.map((r) => (
              <div key={r.id} className={`${styles.row} ${styles.imagingRow}`} onClick={() => setSelectedImagingRecordId(r.id)}>
                <div>
                  {MODALITY_LABELS[r.modality]}
                  {r.bodyRegion ? ` · ${r.bodyRegion}` : ''}
                </div>
                <div className={styles.muted}>{new Date(r.requestedAt).toLocaleDateString('tr-TR')}</div>
                <div className={styles.muted}>{r.resultedAt ? new Date(r.resultedAt).toLocaleDateString('tr-TR') : '—'}</div>
                <div>
                  <ImagingStatusBadge status={r.status} />
                </div>
              </div>
            ))
          )}
        </div>
      )}

      <PatientEditModal
        open={editOpen}
        profile={profile}
        onClose={() => setEditOpen(false)}
        onSaved={() => {
          setEditOpen(false);
          load();
        }}
      />
      <LabResultDetailModal resultId={selectedLabResultId} onClose={() => setSelectedLabResultId(null)} onChanged={load} />
      <ImagingRecordDetailModal
        recordId={selectedImagingRecordId}
        onClose={() => setSelectedImagingRecordId(null)}
        onChanged={load}
      />
    </AppShell>
  );
}
