package com.vetos.modules.dataimport.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Onizleme raporu; import_batches.report JSONB sutununda saklanir. */
public record ImportReport(
    Map<ImportEntityType, EntityTotals> totals,
    List<RowIssue> errors,
    List<RowIssue> warnings,
    List<OwnerMatch> ownerMatches,
    List<String> newSpecies,
    List<NewBreed> newBreeds,
    List<String> structuralErrors
) {

    /** Irk listesi olmadan kurulan raporlar (ve bu alan eklenmeden once kaydedilmis JSON) icin. */
    public ImportReport(Map<ImportEntityType, EntityTotals> totals, List<RowIssue> errors, List<RowIssue> warnings,
                        List<OwnerMatch> ownerMatches, List<String> newSpecies, List<String> structuralErrors) {
        this(totals, errors, warnings, ownerMatches, newSpecies, List.of(), structuralErrors);
    }

    /** Aktarimda Vetly'nin ortak irk listesine eklenecek irk; species = Vetly'deki (ya da yeni eklenecek) tur adi. */
    public record NewBreed(String species, String name) {
    }

    public record EntityTotals(int rowsInFile, int toCreate, int matched, int alreadyImported, int errorRows) {
    }

    public record RowIssue(String sheet, int rowNumber, String sourceId, String message) {
    }

    public record OwnerMatch(String sourceId, String sourceName, String sourcePhone,
                             UUID existingOwnerId, String existingOwnerName) {
    }

    /**
     * KVKK: kisisel veri tasiyabilen satir hatalari/uyarilari ve sahip eslesmeleri bosaltilmis kopya.
     * Toplamlar, yeni turler/irklar ve yapisal hatalar (kisisel veri icermez) korunur.
     */
    public ImportReport withoutPersonalData() {
        return new ImportReport(totals, List.of(), List.of(), List.of(), newSpecies, newBreeds, structuralErrors);
    }

    /** Yapisal hata yoksa ve en az bir kayit olusturulacak/eslesecekse aktarim onaylanabilir. */
    public boolean canCommit() {
        if (structuralErrors != null && !structuralErrors.isEmpty()) {
            return false;
        }
        if (totals == null) {
            return false;
        }
        long actionable = totals.values().stream().mapToLong(t -> (long) t.toCreate() + t.matched()).sum();
        return actionable > 0;
    }
}
