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
  /** Reçete aktarımında null. */
  administeredDate: string | null;
  submittedAt: string | null;
  confirmationMethod: ConfirmationMethod | null;
  tarbilReference: string | null;
  vaccineKey: string;
  vaccineMapping: Record<string, unknown> | null;
  speciesMapping: Record<string, unknown> | null;
  /** Backend P0'dan itibaren gelir; eski yanitlarda yoksa VACCINATION sayilir. */
  documentType?: DocumentType;
  /** Aşı: stok penceresinde hangi liste (OPEN = Parça Stok; spec 2026-10-24); eski sunucu göndermez. */
  stockSource?: StockSource | null;
  /** Stoktan düşen reçete (spec 2026-10-09 S7); aşıda yok/null. */
  prescription?: PrescriptionPayload | null;
}

export interface PrescriptionPayload {
  sourceKind: 'ENCOUNTER' | 'PRESCRIPTION';
  tarbilDiseaseId: string | null;
  tarbilDiseaseName: string | null;
  treatmentStartDate: string;
  /** Yalnız TARBİL düşümü bekleyen satırlar. */
  /**
   * quantity: TARBİL "Kullanılacak Adet" (paketli kalemde 0); amount: "Kullanılacak Miktarı" (kullanım biriminde);
   * source: stok penceresinde hangi liste (spec 2026-10-24). Eski sunucu amount/unit/source göndermez.
   */
  lines: { itemName: string; tarbilProductName: string | null; lotNumber: string | null; quantity: number;
    amount?: number; unit?: string; source?: StockSource }[];
}

export type PlannedLine = PrescriptionPayload['lines'][number];

/** TARBIL'de kaydedilmis recete satiri; onayla birlikte Vetly'ye gider (POST .../submitted lines). */
/** TARBİL stok penceresindeki liste: Bütün Stok ya da Açık Stok (aşıda Parça Stok). */
export type StockSource = 'WHOLE' | 'OPEN';

export interface ConfirmedLine {
  productName: string;
  serialNumber: string;
  quantity: number;
  /** TARBİL'e girilen "Kullanılacak Miktarı" ve "Stok Tipi" (spec 2026-10-24). */
  amount?: number;
  source?: StockSource | null;
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
  /** Asi sayfasindaki "Parca Stok Ara" tablosundan (2026-10-21); yalniz parca stok satirlarinda true gonderilir. */
  partStock?: boolean;
  /** Aşı bütün stok: kutudaki flakon (spec 2026-10-24). */
  dosesPerPackage?: number | null;
  /** İlaç: açık ürün son kullanma tarihi, yyyy-MM-dd (spec 2026-10-25). */
  openedExpiryDate?: string | null;
}

/** Stok sayfasi karsilastirmasi: urun+lot gruplari sayilir, hicbir sey kaydedilmez. */
export interface StockComparison {
  newCount: number;
  quantityDiffersCount: number;
  matchedCount: number;
}
