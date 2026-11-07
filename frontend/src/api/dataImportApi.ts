import { apiClient } from './client';
import { platformAdminClient } from '../platformAdmin/platformAdminClient';

export type LegacyEntityType = 'OWNER' | 'PATIENT';

export interface LegacyData {
  sourceSystem: string;
  sourceId: string;
  importedAt: string;
  data: Record<string, string>;
}

export const dataImportApi = {
  /** Kayit eski sistemden aktarilmadiysa sunucu 204 doner; bu durumda null. */
  getLegacyData: async (entityType: LegacyEntityType, id: string): Promise<LegacyData | null> => {
    const result = await apiClient.get<LegacyData | undefined>(
      `/api/v1/legacy-data?entityType=${entityType}&id=${encodeURIComponent(id)}`
    );
    return result ?? null;
  },
};

// ---------------------------------------------------------------------------
// Platform admin: klinik veri aktarimi (platformAdminClient kullanir)
// ---------------------------------------------------------------------------

export type ImportBatchStatus = 'PREVIEWED' | 'COMMITTING' | 'COMMITTED' | 'FAILED' | 'ROLLED_BACK' | 'DISCARDED';

export interface ImportEntityTotals {
  rowsInFile: number;
  toCreate: number;
  matched: number;
  alreadyImported: number;
  errorRows: number;
}

export interface ImportRowIssue {
  sheet: string;
  rowNumber: number;
  sourceId: string | null;
  message: string;
}

export interface ImportOwnerMatch {
  sourceId: string;
  sourceName: string | null;
  sourcePhone: string | null;
  existingOwnerId: string;
  existingOwnerName: string | null;
}

export interface ImportReport {
  totals: Partial<Record<LegacyEntityType, ImportEntityTotals>>;
  errors: ImportRowIssue[];
  warnings: ImportRowIssue[];
  ownerMatches: ImportOwnerMatch[];
  newSpecies: string[];
  /** Eski raporlarda (bu alan eklenmeden once kaydedilenler) bulunmayabilir. */
  newBreeds?: { species: string; name: string }[];
  structuralErrors: string[];
  /** Sunucu gondermeyebilir; istemcide canCommitReport ile hesaplanir. */
  canCommit?: boolean;
}

export interface ImportBatch {
  id: string;
  tenantId: string;
  sourceSystem: string;
  fileName: string;
  status: ImportBatchStatus;
  report: ImportReport | null;
  totalRows: number;
  processedRows: number;
  errorMessage: string | null;
  createdAt: string;
  committedAt: string | null;
  rolledBackAt: string | null;
  /** "Geri al" sunulabilir mi: COMMITTED ya da kayitlari hala duran FAILED (sunucu hesaplar). */
  rollbackable: boolean;
}

export interface ImportRollbackBlocker {
  table: string;
  rowId: string | null;
  column: string;
}

export interface ImportRollbackResult {
  rolledBack: boolean;
  blockers: ImportRollbackBlocker[];
}

/** Yapisal hata yoksa ve en az bir kayit olusturulacak/eslesecekse aktarim onaylanabilir. */
export function canCommitReport(report: ImportReport | null | undefined): boolean {
  if (!report) return false;
  if (report.structuralErrors && report.structuralErrors.length > 0) return false;
  const actionable = Object.values(report.totals ?? {}).reduce(
    (sum, t) => sum + (t ? t.toCreate + t.matched : 0),
    0
  );
  return actionable > 0;
}

const adminImportsPath = (tenantId: string) => `/api/v1/platform-admin/tenants/${tenantId}/imports`;

export const platformAdminImportApi = {
  list: (tenantId: string) => platformAdminClient.get<ImportBatch[]>(adminImportsPath(tenantId)),
  get: (tenantId: string, batchId: string) =>
    platformAdminClient.get<ImportBatch>(`${adminImportsPath(tenantId)}/${batchId}`),
  preview: (tenantId: string, file: File, sourceSystem: string) => {
    const form = new FormData();
    form.append('file', file);
    form.append('sourceSystem', sourceSystem);
    return platformAdminClient.postMultipart<ImportBatch>(adminImportsPath(tenantId), form);
  },
  discard: (tenantId: string, batchId: string) =>
    platformAdminClient.post<void>(`${adminImportsPath(tenantId)}/${batchId}/discard`),
  commit: (tenantId: string, batchId: string, rejectedMatchSourceIds: string[]) =>
    platformAdminClient.post<ImportBatch>(`${adminImportsPath(tenantId)}/${batchId}/commit`, { rejectedMatchSourceIds }),
  rollback: (tenantId: string, batchId: string) =>
    platformAdminClient.post<ImportRollbackResult>(`${adminImportsPath(tenantId)}/${batchId}/rollback`),
  downloadTemplate: () => platformAdminClient.getBlob('/api/v1/platform-admin/imports/template.xlsx'),
};
