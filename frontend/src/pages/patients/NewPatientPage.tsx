import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { AppShell } from '../../components/layout/AppShell';
import { ApiError } from '../../api/client';
import { BreedItem, OwnerSearchResult, patientApi, Sex, SpeciesItem } from '../../api/patientApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select, Textarea } from '../../components/ui/Field';
import styles from './NewPatientPage.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

export function NewPatientPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const presetOwnerId = searchParams.get('ownerId');

  const [ownerId, setOwnerId] = useState<string | null>(null);
  const [ownerLabel, setOwnerLabel] = useState<string | null>(null);
  const [ownerQuery, setOwnerQuery] = useState('');
  const [ownerResults, setOwnerResults] = useState<OwnerSearchResult[]>([]);
  const [ownerSearchOpen, setOwnerSearchOpen] = useState(false);

  const [species, setSpecies] = useState<SpeciesItem[]>([]);
  const [breeds, setBreeds] = useState<BreedItem[]>([]);
  const [name, setName] = useState('');
  const [speciesId, setSpeciesId] = useState('');
  const [breedId, setBreedId] = useState('');
  const [sex, setSex] = useState<Sex>('UNKNOWN');
  const [birthDate, setBirthDate] = useState('');
  const [color, setColor] = useState('');
  const [temperament, setTemperament] = useState('');
  const [distinguishingMarks, setDistinguishingMarks] = useState('');
  const [aggressive, setAggressive] = useState(false);
  const [bloodType, setBloodType] = useState('');
  const [foodBrand, setFoodBrand] = useState('');
  const [protocolNumber, setProtocolNumber] = useState('');
  const [rabiesTag, setRabiesTag] = useState('');
  const [criticalAlert, setCriticalAlert] = useState('');
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    patientApi.listSpecies().then((list) => {
      setSpecies(list);
      if (list.length > 0) setSpeciesId(list[0].id);
    });
  }, []);

  useEffect(() => {
    if (!speciesId) {
      setBreeds([]);
      return;
    }
    patientApi.listBreeds(speciesId).then(setBreeds);
    setBreedId('');
  }, [speciesId]);

  useEffect(() => {
    if (!presetOwnerId) return;
    patientApi.getOwnerProfile(presetOwnerId).then((profile) => {
      setOwnerId(profile.id);
      setOwnerLabel(`${profile.fullName} · ${profile.phone}`);
    });
  }, [presetOwnerId]);

  useEffect(() => {
    if (presetOwnerId || !ownerSearchOpen) return;
    const handle = setTimeout(() => {
      patientApi.searchOwners(ownerQuery).then(setOwnerResults);
    }, 250);
    return () => clearTimeout(handle);
  }, [ownerQuery, ownerSearchOpen, presetOwnerId]);

  function selectOwner(o: OwnerSearchResult) {
    setOwnerId(o.id);
    setOwnerLabel(`${o.fullName} · ${o.phone}`);
    setOwnerSearchOpen(false);
    setOwnerQuery('');
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (busy || !ownerId) return;
    setBusy(true);
    setError(null);
    try {
      const patient = await patientApi.register({
        ownerId,
        speciesId,
        breedId: breedId || undefined,
        name,
        sex,
        birthDate: birthDate || undefined,
        color: color || undefined,
        temperament: temperament || undefined,
        distinguishingMarks: distinguishingMarks || undefined,
        aggressive,
        bloodType: bloodType || undefined,
        foodBrand: foodBrand || undefined,
        criticalAlert: criticalAlert || undefined,
        notes: notes || undefined,
        protocolNumber: protocolNumber || undefined,
        rabiesTag: rabiesTag || undefined,
      });
      navigate(`/hastalar/${patient.id}`);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AppShell>
      <button className={styles.backLink} onClick={() => navigate('/hastalar')}>
        ← Hastalar &amp; Sahipler
      </button>

      <div className={styles.card}>
        <h1 className={styles.title}>Yeni Hasta</h1>
        <p className={styles.lede}>
          Bir müşteriye bağlı yeni hasta kaydı oluşturun. Müşteri henüz kayıtlı değilse{' '}
          <Link to="/musteriler/yeni" className={styles.inlineLink}>
            önce müşteri oluşturun
          </Link>
          .
        </p>

        <form onSubmit={handleSubmit}>
          {error && <div className={styles.errorBanner}>{error}</div>}

          <div className={styles.sectionLabel}>Müşteri</div>
          {ownerId ? (
            <div className={styles.selectedOwner}>
              <span>{ownerLabel}</span>
              {!presetOwnerId && (
                <button
                  type="button"
                  className={styles.changeOwnerBtn}
                  onClick={() => {
                    setOwnerId(null);
                    setOwnerLabel(null);
                  }}
                >
                  Değiştir
                </button>
              )}
            </div>
          ) : (
            <div className={styles.ownerPicker}>
              <Input
                placeholder="Müşteri adı veya telefonuyla arayın..."
                value={ownerQuery}
                onFocus={() => setOwnerSearchOpen(true)}
                onBlur={() => setTimeout(() => setOwnerSearchOpen(false), 150)}
                onChange={(e) => {
                  setOwnerQuery(e.target.value);
                  setOwnerSearchOpen(true);
                }}
              />
              {ownerSearchOpen && ownerResults.length > 0 && (
                <div className={styles.ownerDropdown} onMouseDown={(e) => e.preventDefault()}>
                  {ownerResults.map((o) => (
                    <div key={o.id} className={styles.ownerOption} onClick={() => selectOwner(o)}>
                      <span className={styles.ownerOptionName}>{o.fullName}</span>
                      <span className={styles.ownerOptionPhone}>{o.phone}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          <div className={styles.sectionLabel}>Hasta Bilgileri</div>
          <div className={styles.row4}>
            <FieldWrap label="Adı">
              <Input value={name} onChange={(e) => setName(e.target.value)} required />
            </FieldWrap>
            <FieldWrap label="Tür">
              <Select value={speciesId} onChange={(e) => setSpeciesId(e.target.value)} required>
                {species.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </Select>
            </FieldWrap>
            <FieldWrap label="Irk (opsiyonel)">
              <Select value={breedId} onChange={(e) => setBreedId(e.target.value)} disabled={breeds.length === 0}>
                <option value="">Seçilmedi</option>
                {breeds.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </Select>
            </FieldWrap>
            <FieldWrap label="Cinsiyet">
              <Select value={sex} onChange={(e) => setSex(e.target.value as Sex)}>
                <option value="FEMALE">Dişi</option>
                <option value="MALE">Erkek</option>
                <option value="UNKNOWN">Bilinmiyor</option>
              </Select>
            </FieldWrap>
          </div>
          <div className={styles.row3}>
            <FieldWrap label="Doğum tarihi (opsiyonel)">
              <Input type="date" value={birthDate} onChange={(e) => setBirthDate(e.target.value)} />
            </FieldWrap>
            <FieldWrap label="Renk">
              <Input value={color} onChange={(e) => setColor(e.target.value)} />
            </FieldWrap>
            <FieldWrap label="Kan Grubu">
              <Input value={bloodType} onChange={(e) => setBloodType(e.target.value)} />
            </FieldWrap>
          </div>
          <div className={styles.row3}>
            <FieldWrap label="Kullanılan Mama">
              <Input value={foodBrand} onChange={(e) => setFoodBrand(e.target.value)} />
            </FieldWrap>
            <FieldWrap label="Protokol No">
              <Input value={protocolNumber} onChange={(e) => setProtocolNumber(e.target.value)} />
            </FieldWrap>
            <FieldWrap label="Kuduz Küpe No">
              <Input value={rabiesTag} onChange={(e) => setRabiesTag(e.target.value)} />
            </FieldWrap>
          </div>
          <div className={styles.row2}>
            <FieldWrap label="Huyu">
              <Input value={temperament} onChange={(e) => setTemperament(e.target.value)} placeholder="Örn. Sakin, oyuncu" />
            </FieldWrap>
            <FieldWrap label="Ayırt Edici Özelliği">
              <Input value={distinguishingMarks} onChange={(e) => setDistinguishingMarks(e.target.value)} />
            </FieldWrap>
          </div>
          <label className={styles.checkboxRow}>
            <input type="checkbox" checked={aggressive} onChange={(e) => setAggressive(e.target.checked)} />
            Saldırgan / dikkatli yaklaşılmalı
          </label>

          <div className={styles.sectionLabel}>Notlar</div>
          <div className={styles.row2}>
            <FieldWrap label="Kritik Uyarı (kırmızı banner ile gösterilir)">
              <Textarea rows={2} value={criticalAlert} onChange={(e) => setCriticalAlert(e.target.value)} />
            </FieldWrap>
            <FieldWrap label="Genel Notlar">
              <Textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
            </FieldWrap>
          </div>

          <div className={styles.actions}>
            <Button type="button" variant="secondary" onClick={() => navigate('/hastalar')}>
              Vazgeç
            </Button>
            <Button type="submit" variant="primary" disabled={busy || !ownerId}>
              {busy ? 'Kaydediliyor...' : 'Hastayı Kaydet'}
            </Button>
          </div>
        </form>
      </div>
    </AppShell>
  );
}
