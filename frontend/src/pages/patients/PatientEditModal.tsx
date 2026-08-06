import { FormEvent, useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { BreedItem, patientApi, PatientProfile, Sex, SpeciesItem } from '../../api/patientApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select, Textarea } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import styles from './PatientEditModal.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

interface PatientEditModalProps {
  open: boolean;
  profile: PatientProfile | null;
  onClose: () => void;
  onSaved: () => void;
}

export function PatientEditModal({ open, profile, onClose, onSaved }: PatientEditModalProps) {
  const [species, setSpecies] = useState<SpeciesItem[]>([]);
  const [breeds, setBreeds] = useState<BreedItem[]>([]);
  const [breedId, setBreedId] = useState('');
  const [sex, setSex] = useState<Sex>('UNKNOWN');
  const [birthDate, setBirthDate] = useState('');
  const [neutered, setNeutered] = useState(false);
  const [color, setColor] = useState('');
  const [temperament, setTemperament] = useState('');
  const [distinguishingMarks, setDistinguishingMarks] = useState('');
  const [aggressive, setAggressive] = useState(false);
  const [bloodType, setBloodType] = useState('');
  const [foodBrand, setFoodBrand] = useState('');
  const [microchipNumber, setMicrochipNumber] = useState('');
  const [tarbilAnimalId, setTarbilAnimalId] = useState('');
  const [rabiesTag, setRabiesTag] = useState('');
  const [protocolNumber, setProtocolNumber] = useState('');
  const [criticalAlert, setCriticalAlert] = useState('');
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open || !profile) return;
    patientApi.listSpecies().then(setSpecies);
    setBreedId('');
    setSex(profile.sex ?? 'UNKNOWN');
    setBirthDate(profile.birthDate ?? '');
    setNeutered(profile.neutered);
    setColor(profile.color ?? '');
    setTemperament(profile.temperament ?? '');
    setDistinguishingMarks(profile.distinguishingMarks ?? '');
    setAggressive(profile.aggressive);
    setBloodType(profile.bloodType ?? '');
    setFoodBrand(profile.foodBrand ?? '');
    setMicrochipNumber(profile.microchipNumber ?? '');
    setTarbilAnimalId(profile.tarbilAnimalId ?? '');
    setRabiesTag(profile.rabiesTag ?? '');
    setProtocolNumber(profile.protocolNumber ?? '');
    setCriticalAlert(profile.criticalAlert ?? '');
    setNotes(profile.notes ?? '');
    setError(null);
  }, [open, profile]);

  const speciesId = species.find((s) => s.name === profile?.speciesName)?.id;

  useEffect(() => {
    if (!speciesId) {
      setBreeds([]);
      return;
    }
    patientApi.listBreeds(speciesId).then((list) => {
      setBreeds(list);
      const match = list.find((b) => b.name === profile?.breedName);
      setBreedId(match?.id ?? '');
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [speciesId]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (busy || !profile) return;
    setBusy(true);
    setError(null);
    try {
      await patientApi.update(profile.id, {
        name: profile.name,
        breedId: breedId || undefined,
        sex,
        birthDate: birthDate || undefined,
        neutered,
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
      await patientApi.updateIdentification(profile.id, {
        microchipNumber: microchipNumber || undefined,
        tarbilAnimalId: tarbilAnimalId || undefined,
      });
      onSaved();
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  if (!profile) return null;

  return (
    <Modal open={open} onClose={onClose} width={620}>
      <form onSubmit={handleSubmit}>
        <h2 className={styles.title}>{profile.name} — Hasta bilgilerini düzenle</h2>

        {error && <div className={styles.errorBanner}>{error}</div>}

        <div className={styles.sectionLabel}>Kimlik</div>
        <div className={styles.row3}>
          <FieldWrap label="Irk">
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
          <FieldWrap label="Doğum Tarihi">
            <Input type="date" value={birthDate} onChange={(e) => setBirthDate(e.target.value)} />
          </FieldWrap>
        </div>
        <div className={styles.row3}>
          <FieldWrap label="Renk">
            <Input value={color} onChange={(e) => setColor(e.target.value)} />
          </FieldWrap>
          <FieldWrap label="Kan Grubu">
            <Input value={bloodType} onChange={(e) => setBloodType(e.target.value)} />
          </FieldWrap>
          <FieldWrap label="Kullanılan Mama">
            <Input value={foodBrand} onChange={(e) => setFoodBrand(e.target.value)} />
          </FieldWrap>
        </div>
        <label className={styles.checkboxRow}>
          <input type="checkbox" checked={neutered} onChange={(e) => setNeutered(e.target.checked)} />
          Kısırlaştırılmış
        </label>
        <label className={styles.checkboxRow}>
          <input type="checkbox" checked={aggressive} onChange={(e) => setAggressive(e.target.checked)} />
          Saldırgan / dikkatli yaklaşılmalı
        </label>

        <div className={styles.sectionLabel}>Tanımlama Numaraları</div>
        <div className={styles.row3}>
          <FieldWrap label="Mikroçip No">
            <Input value={microchipNumber} onChange={(e) => setMicrochipNumber(e.target.value)} />
          </FieldWrap>
          <FieldWrap label="TARBİL Kimlik No">
            <Input value={tarbilAnimalId} onChange={(e) => setTarbilAnimalId(e.target.value)} />
          </FieldWrap>
          <FieldWrap label="Kuduz Küpe No">
            <Input value={rabiesTag} onChange={(e) => setRabiesTag(e.target.value)} />
          </FieldWrap>
        </div>
        <FieldWrap label="Protokol No">
          <Input value={protocolNumber} onChange={(e) => setProtocolNumber(e.target.value)} />
        </FieldWrap>

        <div className={styles.sectionLabel}>Davranış ve Notlar</div>
        <div className={styles.row2}>
          <FieldWrap label="Huyu">
            <Input value={temperament} onChange={(e) => setTemperament(e.target.value)} placeholder="Örn. Sakin, oyuncu" />
          </FieldWrap>
          <FieldWrap label="Ayırt Edici Özelliği">
            <Input value={distinguishingMarks} onChange={(e) => setDistinguishingMarks(e.target.value)} />
          </FieldWrap>
        </div>
        <FieldWrap label="Kritik Uyarı (kırmızı banner ile gösterilir)">
          <Textarea rows={2} value={criticalAlert} onChange={(e) => setCriticalAlert(e.target.value)} />
        </FieldWrap>
        <FieldWrap label="Genel Notlar">
          <Textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </FieldWrap>

        <div className={styles.actions}>
          <Button type="button" variant="secondary" onClick={onClose}>
            Vazgeç
          </Button>
          <Button type="submit" variant="primary" disabled={busy}>
            {busy ? 'Kaydediliyor...' : 'Kaydet'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
