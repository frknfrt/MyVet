package com.vetos.modules.integration.tarbil.application.dto;

import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.vetos.modules.integration.tarbil.domain.TarbilConfirmationMethod;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Eklentiye giden aktarim verisi -- sahip TC/adres/telefon BILINCLI olarak yok (spec S4). */
public record TarbilSubmissionView(
    UUID id, UUID vaccinationRecordId, TarbilSyncStatus status,
    String patientName, String microchipNumber, String passportNumber, UUID speciesId, String speciesName, String breedName,
    String sex, LocalDate birthDate,
    String vaccineName, String lotNumber, String tarbilProductName, LocalDate administeredDate,
    Instant submittedAt, TarbilConfirmationMethod confirmationMethod, String tarbilReference,
    String vaccineKey, String vaccineMappingJson, String speciesMappingJson,
    TarbilDocumentType documentType,
    PrescriptionPayload prescription,
    /** Asi: TARBIL stok penceresinde hangi liste (WHOLE = Butun Stok, OPEN = Parca Stok; spec 2026-10-24); stoksuzda null. */
    String stockSource
) {
    /** Stoktan dusen recete (spec 2026-10-09 S7); asida null. lines yalniz TARBIL dusumu bekleyen satirlar. */
    public record PrescriptionPayload(String sourceKind, UUID tarbilDiseaseId, String tarbilDiseaseName,
                                      LocalDate treatmentStartDate, List<Line> lines) {
        /**
         * quantity: TARBIL "Kullanilacak Adet" (ADET kalemde miktar; paketli kalemde 0 - TARBIL Adet alanini kapali tutar);
         * amount: "Kullanilacak Miktari" (kullanim biriminde); unit: ADET | ML | G | FLAKON; source: WHOLE | OPEN (spec 2026-10-24).
         */
        public record Line(String itemName, String tarbilProductName, String lotNumber, int quantity,
                           java.math.BigDecimal amount, String unit, String source) {}
    }
}
