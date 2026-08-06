import { FormEvent, useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { patientApi, SpeciesItem, BreedItem } from '../../api/patientApi';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Field';
import styles from './SettingsPage.module.css';

function errorMessageOf(err: unknown): string {
  return err instanceof ApiError ? err.message : err instanceof Error ? err.message : 'Beklenmeyen bir hata oluştu';
}

export function SpeciesBreedsPanel() {
  const [species, setSpecies] = useState<SpeciesItem[]>([]);
  const [selectedSpeciesId, setSelectedSpeciesId] = useState<string | null>(null);
  const [breeds, setBreeds] = useState<BreedItem[]>([]);
  const [newSpeciesName, setNewSpeciesName] = useState('');
  const [newBreedName, setNewBreedName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function loadSpecies() {
    patientApi.listSpecies().then((list) => {
      setSpecies(list);
      setSelectedSpeciesId((current) => current ?? (list.length > 0 ? list[0].id : null));
    });
  }

  useEffect(() => {
    loadSpecies();
  }, []);

  useEffect(() => {
    if (!selectedSpeciesId) {
      setBreeds([]);
      return;
    }
    patientApi.listBreeds(selectedSpeciesId).then(setBreeds);
  }, [selectedSpeciesId]);

  async function handleAddSpecies(e: FormEvent) {
    e.preventDefault();
    if (busy || !newSpeciesName.trim()) return;
    setBusy(true);
    setError(null);
    try {
      const created = await patientApi.createSpecies(newSpeciesName.trim());
      setNewSpeciesName('');
      loadSpecies();
      setSelectedSpeciesId(created.id);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleAddBreed(e: FormEvent) {
    e.preventDefault();
    if (busy || !selectedSpeciesId || !newBreedName.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await patientApi.createBreed(selectedSpeciesId, newBreedName.trim());
      setNewBreedName('');
      patientApi.listBreeds(selectedSpeciesId).then(setBreeds);
    } catch (err) {
      setError(errorMessageOf(err));
    } finally {
      setBusy(false);
    }
  }

  const selectedSpecies = species.find((s) => s.id === selectedSpeciesId) ?? null;

  return (
    <div>
      {error && <div className={styles.errorBanner}>{error}</div>}
      <div className={styles.catalogGrid}>
        <div className={styles.catalogCard}>
          <div className={styles.catalogCardTitle}>Türler</div>
          <div className={styles.catalogList}>
            {species.map((s) => (
              <div
                key={s.id}
                className={`${styles.catalogListItem} ${s.id === selectedSpeciesId ? styles.catalogListItemActive : ''}`}
                onClick={() => setSelectedSpeciesId(s.id)}
              >
                {s.name}
              </div>
            ))}
          </div>
          <form className={styles.catalogAddForm} onSubmit={handleAddSpecies}>
            <Input
              placeholder="Yeni tür adı (örn. Kemirgen)"
              value={newSpeciesName}
              onChange={(e) => setNewSpeciesName(e.target.value)}
            />
            <Button type="submit" variant="secondary" disabled={busy || !newSpeciesName.trim()}>
              Ekle
            </Button>
          </form>
        </div>

        <div className={styles.catalogCard}>
          <div className={styles.catalogCardTitle}>
            {selectedSpecies ? `${selectedSpecies.name} — Irklar` : 'Irklar'}
          </div>
          {!selectedSpeciesId ? (
            <div className={styles.catalogEmpty}>Önce soldan bir tür seçin</div>
          ) : (
            <>
              <div className={styles.catalogList}>
                {breeds.length === 0 ? (
                  <div className={styles.catalogEmpty}>Bu tür için henüz ırk eklenmedi</div>
                ) : (
                  breeds.map((b) => (
                    <div key={b.id} className={styles.catalogListItem}>
                      {b.name}
                    </div>
                  ))
                )}
              </div>
              <form className={styles.catalogAddForm} onSubmit={handleAddBreed}>
                <Input
                  placeholder="Yeni ırk adı (örn. Golden Retriever)"
                  value={newBreedName}
                  onChange={(e) => setNewBreedName(e.target.value)}
                />
                <Button type="submit" variant="secondary" disabled={busy || !newBreedName.trim()}>
                  Ekle
                </Button>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
