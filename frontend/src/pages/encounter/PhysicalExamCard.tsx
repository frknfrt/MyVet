import { useEffect, useState } from 'react';
import { ExamBodySystem, ExamFindingStatus, PhysicalExamFinding } from '../../api/encounterApi';
import { Textarea } from '../../components/ui/Field';
import { EXAM_BODY_SYSTEMS, EXAM_BODY_SYSTEM_LABELS } from './examSystem';
import encounterStyles from './EncounterPage.module.css';
import styles from './PhysicalExamCard.module.css';

export type FindingsBySystem = Record<ExamBodySystem, { status: ExamFindingStatus; note: string }>;

export function toFindingsBySystem(findings: PhysicalExamFinding[]): FindingsBySystem {
  const base = Object.fromEntries(
    EXAM_BODY_SYSTEMS.map((system) => [system, { status: 'NOT_EXAMINED' as ExamFindingStatus, note: '' }])
  ) as FindingsBySystem;
  findings.forEach((f) => {
    base[f.system] = { status: f.status, note: f.note ?? '' };
  });
  return base;
}

export function toFindingsPayload(findings: FindingsBySystem): PhysicalExamFinding[] {
  return EXAM_BODY_SYSTEMS.map((system) => ({
    system,
    status: findings[system].status,
    note: findings[system].note.trim() ? findings[system].note.trim() : null,
  }));
}

interface PhysicalExamCardProps {
  findings: FindingsBySystem;
  onChange: (next: FindingsBySystem) => void;
  readOnly: boolean;
}

/** Kaydetme sayfanın otomatik kaydıyla yapılır (EncounterPage). */
export function summarizeFindings(findings: FindingsBySystem) {
  const statuses = EXAM_BODY_SYSTEMS.map((system) => findings[system].status);
  return {
    abnormal: statuses.filter((s) => s === 'ABNORMAL').length,
    normal: statuses.filter((s) => s === 'NORMAL').length,
    notExamined: statuses.filter((s) => s === 'NOT_EXAMINED').length,
  };
}

/** Muayene edilmemiş sistemleri normal yapar; anormal işaretlenenlere ve notlara dokunmaz. */
export function markRemainingNormal(findings: FindingsBySystem): FindingsBySystem {
  return Object.fromEntries(
    EXAM_BODY_SYSTEMS.map((system) => [
      system,
      findings[system].status === 'NOT_EXAMINED' ? { ...findings[system], status: 'NORMAL' as ExamFindingStatus } : findings[system],
    ]),
  ) as FindingsBySystem;
}

export function PhysicalExamCard({ findings, onChange, readOnly }: PhysicalExamCardProps) {
  const summary = summarizeFindings(findings);
  // Anormal bulgu varsa açık başlar; kayıt sonradan yüklenirse de açılır (kendiliğinden kapanmaz).
  const [expanded, setExpanded] = useState(summary.abnormal > 0);
  useEffect(() => {
    if (summary.abnormal > 0) setExpanded(true);
  }, [summary.abnormal]);

  function setStatus(system: ExamBodySystem, status: ExamFindingStatus) {
    onChange({ ...findings, [system]: { ...findings[system], status } });
  }

  function setNote(system: ExamBodySystem, note: string) {
    onChange({ ...findings, [system]: { ...findings[system], note } });
  }

  return (
    <div className={encounterStyles.card}>
      <div className={styles.header}>
        <div>
          <div className={encounterStyles.cardTitle}>Fiziksel Muayene</div>
          <div className={styles.summary}>
            {summary.abnormal > 0 && <span className={styles.summaryAbnormal}>{summary.abnormal} anormal</span>}
            <span>{summary.normal} normal</span>
            <span>{summary.notExamined} muayene edilmedi</span>
          </div>
        </div>
        <div className={styles.headerActions}>
          {!readOnly && summary.notExamined > 0 && (
            <button type="button" className={styles.headerBtn} onClick={() => onChange(markRemainingNormal(findings))}>
              Kalanları normal işaretle
            </button>
          )}
          <button
            type="button"
            className={styles.headerBtn}
            aria-expanded={expanded}
            aria-controls="physical-exam-rows"
            onClick={() => setExpanded((v) => !v)}
          >
            {expanded ? 'Gizle' : 'Sistemleri göster'}
          </button>
        </div>
      </div>
      <div id="physical-exam-rows" className={styles.rows} hidden={!expanded}>
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
      </div>
    </div>
  );
}
