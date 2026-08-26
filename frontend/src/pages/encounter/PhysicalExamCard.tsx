import { useEffect, useState } from 'react';
import { encounterApi, ExamBodySystem, ExamFindingStatus, PhysicalExamFinding } from '../../api/encounterApi';
import { Button } from '../../components/ui/Button';
import { Textarea } from '../../components/ui/Field';
import { EXAM_BODY_SYSTEMS, EXAM_BODY_SYSTEM_LABELS } from './examSystem';
import encounterStyles from './EncounterPage.module.css';
import styles from './PhysicalExamCard.module.css';

interface PhysicalExamCardProps {
  encounterId: string;
  initialFindings: PhysicalExamFinding[];
  readOnly: boolean;
}

type FindingsBySystem = Record<ExamBodySystem, { status: ExamFindingStatus; note: string }>;

function toFindingsBySystem(findings: PhysicalExamFinding[]): FindingsBySystem {
  const base = Object.fromEntries(
    EXAM_BODY_SYSTEMS.map((system) => [system, { status: 'NOT_EXAMINED' as ExamFindingStatus, note: '' }])
  ) as FindingsBySystem;
  findings.forEach((f) => {
    base[f.system] = { status: f.status, note: f.note ?? '' };
  });
  return base;
}

export function PhysicalExamCard({ encounterId, initialFindings, readOnly }: PhysicalExamCardProps) {
  const [findings, setFindings] = useState<FindingsBySystem>(() => toFindingsBySystem(initialFindings));
  const [saving, setSaving] = useState(false);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  useEffect(() => {
    setFindings(toFindingsBySystem(initialFindings));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [encounterId]);

  function setStatus(system: ExamBodySystem, status: ExamFindingStatus) {
    setFindings((prev) => ({ ...prev, [system]: { ...prev[system], status } }));
  }

  function setNote(system: ExamBodySystem, note: string) {
    setFindings((prev) => ({ ...prev, [system]: { ...prev[system], note } }));
  }

  async function handleSave() {
    setSaving(true);
    try {
      const payload: PhysicalExamFinding[] = EXAM_BODY_SYSTEMS.map((system) => ({
        system,
        status: findings[system].status,
        note: findings[system].note.trim() ? findings[system].note.trim() : null,
      }));
      await encounterApi.updatePhysicalExam(encounterId, payload);
      setSavedMessage('Fiziksel muayene kaydedildi');
      setTimeout(() => setSavedMessage(null), 2000);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className={encounterStyles.card}>
      <div className={encounterStyles.cardTitle}>Fiziksel Muayene</div>
      {EXAM_BODY_SYSTEMS.map((system) => {
        const { status, note } = findings[system];
        return (
          <div key={system} className={styles.row}>
            <div className={styles.systemLabel}>{EXAM_BODY_SYSTEM_LABELS[system]}</div>
            <div className={styles.toggleGroup}>
              <button
                type="button"
                disabled={readOnly}
                className={[styles.toggleBtn, status === 'NOT_EXAMINED' ? styles.toggleBtnActiveNeutral : ''].join(' ')}
                onClick={() => setStatus(system, 'NOT_EXAMINED')}
              >
                Muayene Edilmedi
              </button>
              <button
                type="button"
                disabled={readOnly}
                className={[styles.toggleBtn, status === 'NORMAL' ? styles.toggleBtnActiveNormal : ''].join(' ')}
                onClick={() => setStatus(system, 'NORMAL')}
              >
                Normal
              </button>
              <button
                type="button"
                disabled={readOnly}
                className={[styles.toggleBtn, status === 'ABNORMAL' ? styles.toggleBtnActiveAbnormal : ''].join(' ')}
                onClick={() => setStatus(system, 'ABNORMAL')}
              >
                Anormal
              </button>
            </div>
            {status === 'ABNORMAL' && (
              <div className={styles.noteWrap}>
                <Textarea
                  rows={2}
                  value={note}
                  disabled={readOnly}
                  placeholder="Anormal bulguyu açıklayın..."
                  onChange={(e) => setNote(system, e.target.value)}
                />
              </div>
            )}
          </div>
        );
      })}
      {!readOnly && (
        <div className={encounterStyles.saveRow}>
          {savedMessage && <span className={encounterStyles.savedNote}>{savedMessage}</span>}
          <Button variant="secondary" onClick={handleSave} disabled={saving}>
            {saving ? 'Kaydediliyor...' : 'Fiziksel Muayeneyi Kaydet'}
          </Button>
        </div>
      )}
    </div>
  );
}
