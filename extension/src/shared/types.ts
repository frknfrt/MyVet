export type ConfirmationMethod = 'AUTO' | 'MANUAL';
export type SubmissionStatus = 'PENDING' | 'SUBMITTED' | 'DISMISSED';

export interface Submission {
  id: string;
  vaccinationRecordId: string;
  status: SubmissionStatus;
  patientName: string;
  microchipNumber: string | null;
  passportNumber: string | null;
  speciesId: string | null;
  speciesName: string | null;
  breedName: string | null;
  sex: 'MALE' | 'FEMALE' | 'UNKNOWN' | null;
  birthDate: string | null;
  vaccineName: string;
  lotNumber: string | null;
  /** Stoktan secilen asinin TARBIL urun adi (spec 2026-10-04 P2); stok baglantisi yoksa null. */
  tarbilProductName: string | null;
  administeredDate: string;
  submittedAt: string | null;
  confirmationMethod: ConfirmationMethod | null;
  tarbilReference: string | null;
  vaccineKey: string;
  vaccineMapping: Record<string, unknown> | null;
  speciesMapping: Record<string, unknown> | null;
  /** Backend P0'dan itibaren gelir; eski yanitlarda yoksa VACCINATION sayilir. */
  documentType?: DocumentType;
}

export interface ExtensionProfile {
  clinicName: string;
  staffName: string;
}

export type DocumentType = 'VACCINATION' | 'PRESCRIPTION' | 'STOCK_RECEIPT';

export type StockSystem = 'HBSAPP_VACCINE' | 'VETILAC_MEDICINE';

/** TARBIL stok tablosundan okunan satir (spec 2026-10-04 S13). */
export interface StockSnapshotLine {
  productName: string;
  presentation: string | null;
  lotNumber: string | null;
  expiryDate: string | null;
  quantity: number;
  openedQuantity: number | null;
}

/** Stok sayfasi karsilastirmasi: urun+lot gruplari sayilir, hicbir sey kaydedilmez. */
export interface StockComparison {
  newCount: number;
  quantityDiffersCount: number;
  matchedCount: number;
}
