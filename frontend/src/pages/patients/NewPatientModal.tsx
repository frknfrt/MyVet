import { FormEvent, useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { BreedItem, patientApi, Sex, SpeciesItem } from '../../api/patientApi';
import { Button } from '../../components/ui/Button';
import { FieldWrap, Input, Select } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import styles from './NewPatientModal.module.css';

interface NewPatientModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: (patientId: string) => void;
}

export function NewPatientModal({ open, onClose, onCreated }: NewPatientModalProps) {
  const [species, setSpecies] = useState<SpeciesItem[]>([]);
  const [breeds, setBreeds] = useState<BreedItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [ownerFullName, setOwnerFullName] = useState('');
  const [ownerPhone, setOwnerPhone] = useState('');
  const [ownerEmail, setOwnerEmail] = useState('');
  const [patientName, setPatientName] = useState('');
  const [speciesId, setSpeciesId] = useState('');
  const [breedId, setBreedId] = useState('');
  const [sex, setSex] = useState<Sex>('UNKNOWN');
  const [birthDate, setBirthDate] = useState('');

  useEffect(() => {
    if (!open) return;
    patientApi.listSpecies().then((list) => {
      setSpecies(list);
      if (list.length > 0) setSpeciesId(list[0].id);
    });
  }, [open]);

  useEffect(() => {
    if (!speciesId) {
      setBreeds([]);
      return;
    }
    patientApi.listBreeds(speciesId).then(setBreeds);
    setBreedId('');
  }, [speciesId]);

  function resetAndClose() {
    setOwnerFullName('');
    setOwnerPhone('');
    setOwnerEmail('');
    setPatientName('');
    setBreedId('');
    setSex('UNKNOWN');
    setBirthDate('');
    setError(null);
    onClose();
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (loading) return;
    setLoading(true);
    setError(null);
    try {
      const owner = await patientApi.registerOwner({
        fullName: ownerFullName,
        phone: ownerPhone,
        email: ownerEmail || undefined,
        marketingConsent: false,
      });
      const patient = await patientApi.register({
        ownerId: owner.id,
        speciesId,
        breedId: breedId || undefined,
        name: patientName,
        sex,
        birthDate: birthDate || undefined,
      });
      onCreated(patient.id);
      resetAndClose();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Kayit olusturulamadi, tekrar deneyin');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Modal open={open} onClose={resetAndClose} width={480}>
      <form onSubmit={handleSubmit}>
        <h2 className={styles.title}>Yeni hasta ve sahip kaydı</h2>
        <p className={styles.lede}>Sahip bilgileriyle birlikte ilk hayvanını kaydedin</p>

        {error && <div className={styles.errorBanner}>{error}</div>}

        <div className={styles.sectionLabel}>Sahip bilgileri</div>
        <FieldWrap label="Adı soyadı">
          <Input value={ownerFullName} onChange={(e) => setOwnerFullName(e.target.value)} required />
        </FieldWrap>
        <div className={styles.row}>
          <FieldWrap label="Telefon">
            <Input value={ownerPhone} onChange={(e) => setOwnerPhone(e.target.value)} required />
          </FieldWrap>
          <FieldWrap label="E-posta (opsiyonel)">
            <Input type="email" value={ownerEmail} onChange={(e) => setOwnerEmail(e.target.value)} />
          </FieldWrap>
        </div>

        <div className={styles.sectionLabel}>Hasta bilgileri</div>
        <FieldWrap label="Adı">
          <Input value={patientName} onChange={(e) => setPatientName(e.target.value)} required />
        </FieldWrap>
        <div className={styles.row}>
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
        </div>
        <div className={styles.row}>
          <FieldWrap label="Cinsiyet">
            <Select value={sex} onChange={(e) => setSex(e.target.value as Sex)}>
              <option value="FEMALE">Dişi</option>
              <option value="MALE">Erkek</option>
              <option value="UNKNOWN">Bilinmiyor</option>
            </Select>
          </FieldWrap>
          <FieldWrap label="Doğum tarihi (opsiyonel)">
            <Input type="date" value={birthDate} onChange={(e) => setBirthDate(e.target.value)} />
          </FieldWrap>
        </div>

        <div className={styles.actions}>
          <Button type="button" variant="secondary" onClick={resetAndClose}>
            Vazgeç
          </Button>
          <Button type="submit" variant="primary" disabled={loading}>
            {loading ? 'Kaydediliyor...' : 'Kaydet'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
