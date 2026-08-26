import { EncounterDetail } from '../../api/encounterApi';
import { Prescription } from '../../api/clinicalApi';
import { LabResultSummary } from '../../api/labApi';
import { ImagingRecordSummary } from '../../api/imagingApi';
import { VaccinationScheduleItem } from '../../api/vaccinationApi';

export type TimelineEntry =
  | { type: 'encounter'; date: string; record: EncounterDetail }
  | { type: 'vaccination'; date: string; record: VaccinationScheduleItem }
  | { type: 'prescription'; date: string; record: Prescription }
  | { type: 'lab'; date: string; record: LabResultSummary }
  | { type: 'imaging'; date: string; record: ImagingRecordSummary };

export function buildPatientTimeline(
  encounters: EncounterDetail[],
  vaccinations: VaccinationScheduleItem[],
  prescriptions: Prescription[],
  labResults: LabResultSummary[],
  imagingRecords: ImagingRecordSummary[]
): TimelineEntry[] {
  const entries: TimelineEntry[] = [
    ...encounters.map((record) => ({ type: 'encounter' as const, date: record.encounterDate, record })),
    ...vaccinations.map((record) => ({ type: 'vaccination' as const, date: record.administeredDate, record })),
    ...prescriptions.map((record) => ({ type: 'prescription' as const, date: record.issuedDate, record })),
    ...labResults.map((record) => ({ type: 'lab' as const, date: record.resultedAt ?? record.requestedAt, record })),
    ...imagingRecords.map((record) => ({ type: 'imaging' as const, date: record.resultedAt ?? record.requestedAt, record })),
  ];
  return entries.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
}

export interface WeightPoint {
  date: string;
  weightKg: number;
}

export function buildWeightTrend(encounters: EncounterDetail[]): WeightPoint[] {
  return encounters
    .filter((e): e is EncounterDetail & { weightKg: number } => e.weightKg != null)
    .map((e) => ({ date: e.encounterDate, weightKg: e.weightKg }))
    .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
}

export function shortDateLabel(dateStr: string): string {
  const d = new Date(dateStr);
  return `${d.getDate()}.${d.getMonth() + 1}`;
}
