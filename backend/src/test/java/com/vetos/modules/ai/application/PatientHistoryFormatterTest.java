package com.vetos.modules.ai.application;

import com.vetos.modules.ai.application.PatientHistory.Section;
import com.vetos.modules.encounter.domain.DrugRoute;
import com.vetos.modules.encounter.domain.PastEncounterSummary;
import com.vetos.modules.encounter.domain.PrescriptionHistoryEntry;
import com.vetos.modules.encounter.domain.VaccinationHistoryEntry;
import com.vetos.modules.imaging.domain.ImagingHistoryEntry;
import com.vetos.modules.imaging.domain.ImagingModality;
import com.vetos.modules.lab.domain.LabResultHistoryEntry;
import com.vetos.modules.lab.domain.LabValueFlag;
import com.vetos.modules.patient.domain.PatientClinicalProfile;
import com.vetos.modules.patient.domain.Sex;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PatientHistoryFormatterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    private static final UUID CURRENT = UUID.randomUUID();
    private static final PatientClinicalProfile PROFILE = new PatientClinicalProfile(
        "Köpek", "Golden", Sex.FEMALE, true, LocalDate.of(2018, 7, 1), new BigDecimal("31.5"), "DEA 1.1+", "Penisilin alerjisi");

    private static <T> Section<T> of(List<T> items) { return new Section<>(items, false, false); }

    private static PatientHistory empty(PatientClinicalProfile profile, BigDecimal encounterWeight) {
        return new PatientHistory(TODAY, profile, encounterWeight, of(List.of()), of(List.of()), of(List.of()),
            of(List.of()), of(List.of()), CURRENT);
    }

    @Test
    void formatsFullHistoryWithAllSections() {
        PatientHistory h = new PatientHistory(TODAY, PROFILE, null,
            of(List.of(new PastEncounterSummary(Instant.parse("2026-03-01T09:00:00Z"), "Gastrit", "Diyet"))),
            of(List.of(new PrescriptionHistoryEntry(CURRENT, LocalDate.of(2026, 10, 1), List.of(
                new PrescriptionHistoryEntry.Item("Metacam", "1.5 mg", "günde 1", 5, DrugRoute.ORAL))))),
            of(List.of(new VaccinationHistoryEntry("Nobivac DHPPi", LocalDate.of(2026, 2, 10), LocalDate.of(2027, 2, 10)))),
            of(List.of(new LabResultHistoryEntry("Biyokimya", Instant.parse("2026-03-01T09:00:00Z"), "Böbrek değerleri yüksek",
                List.of(new LabResultHistoryEntry.Value("BUN", "45", "mg/dL", "7-27", LabValueFlag.HIGH),
                        new LabResultHistoryEntry.Value("ALT", "40", "U/L", "10-100", LabValueFlag.NORMAL))))),
            of(List.of(new ImagingHistoryEntry(ImagingModality.XRAY, "Toraks", Instant.parse("2026-03-01T09:00:00Z"), null))),
            CURRENT);

        String text = PatientHistoryFormatter.format(h);

        assertThat(text)
            .contains("HASTA BİLGİSİ")
            .contains("Tür: Köpek | Irk: Golden | Yaş: 8 yıl 3 ay | Cinsiyet: Dişi, kısırlaştırılmış | Kilo: 31.5 kg | Kan grubu: DEA 1.1+")
            .contains("Kritik uyarı: Penisilin alerjisi")
            .contains("GEÇMİŞ MUAYENELER (son 1 yıl, en yeniden eskiye)")
            .contains("- 2026-03-01 | Değerlendirme: Gastrit | Plan: Diyet")
            .contains("İLAÇLAR / REÇETELER (son 1 yıl)")
            .contains("- 2026-10-01 (bu muayene): Metacam 1.5 mg, günde 1, 5 gün, Oral")
            .contains("AŞILAR (son 1 yıl)")
            .contains("- 2026-02-10 Nobivac DHPPi (sonraki: 2027-02-10)")
            .contains("KAN / LAB SONUÇLARI (son 1 yıl)")
            .contains("- 2026-03-01 Biyokimya — özet: Böbrek değerleri yüksek")
            .contains("  * BUN: 45 mg/dL (ref 7-27) YÜKSEK")
            .contains("  * ALT: 40 U/L (ref 10-100)\n")
            .contains("GÖRÜNTÜLEME (son 1 yıl)")
            .contains("- 2026-03-01 Röntgen, Toraks: bulgu yazılmamış");
    }

    @Test
    void formatsMissingProfileFieldsAsNotSpecified() {
        String text = PatientHistoryFormatter.format(empty(
            new PatientClinicalProfile(null, null, null, false, null, null, null, "  "), null));

        assertThat(text)
            .contains("Tür: belirtilmemiş | Irk: belirtilmemiş | Yaş: belirtilmemiş | Cinsiyet: belirtilmemiş, kısırlaştırılmamış | Kilo: belirtilmemiş | Kan grubu: belirtilmemiş")
            .doesNotContain("Kritik uyarı");
    }

    @Test
    void unreadableProfileSaysSo() {
        assertThat(PatientHistoryFormatter.format(empty(null, null))).contains("HASTA BİLGİSİ\nokunamadı");
    }

    @Test
    void emptySectionsSayNoRecords() {
        String text = PatientHistoryFormatter.format(empty(PROFILE, null));

        assertThat(text).contains("GEÇMİŞ MUAYENELER (son 1 yıl, en yeniden eskiye)\nkayıt yok")
            .contains("AŞILAR (son 1 yıl)\nkayıt yok")
            .contains("GÖRÜNTÜLEME (son 1 yıl)\nkayıt yok");
    }

    @Test
    void failedSectionSaysUnreadable() {
        PatientHistory h = new PatientHistory(TODAY, PROFILE, null, of(List.of()), of(List.of()), of(List.of()),
            Section.unreadable(), of(List.of()), CURRENT);

        assertThat(PatientHistoryFormatter.format(h)).contains("KAN / LAB SONUÇLARI (son 1 yıl)\nokunamadı");
    }

    @Test
    void truncatedSectionShowsNote() {
        PatientHistory h = new PatientHistory(TODAY, PROFILE, null, of(List.of()), of(List.of()),
            new Section<>(List.of(new VaccinationHistoryEntry("Kuduz", LocalDate.of(2026, 5, 1), null)), true, false),
            of(List.of()), of(List.of()), CURRENT);

        assertThat(PatientHistoryFormatter.format(h))
            .contains("- 2026-05-01 Kuduz\n(daha eski kayıtlar kısaltıldı)");
    }

    @Test
    void weightPrefersEncounterOverCard() {
        assertThat(PatientHistoryFormatter.format(empty(PROFILE, new BigDecimal("30.20")))).contains("Kilo: 30.2 kg");
    }

    @Test
    void weightFallsBackToCard() {
        assertThat(PatientHistoryFormatter.format(empty(PROFILE, null))).contains("Kilo: 31.5 kg");
    }

    @Test
    void ageUnderOneYearShowsMonths() {
        var kitten = new PatientClinicalProfile("Kedi", null, Sex.MALE, false, LocalDate.of(2026, 3, 15), null, null, null);

        assertThat(PatientHistoryFormatter.format(empty(kitten, null))).contains("Yaş: 6 ay").contains("Cinsiyet: Erkek, kısırlaştırılmamış");
    }

    @Test
    void pastPrescriptionIsNotMarkedAsCurrent() {
        PatientHistory h = new PatientHistory(TODAY, PROFILE, null, of(List.of()),
            of(List.of(new PrescriptionHistoryEntry(UUID.randomUUID(), LocalDate.of(2026, 4, 1), List.of(
                new PrescriptionHistoryEntry.Item("Clavon", "1 ml", "günde 2", 7, DrugRoute.INJECTABLE))))),
            of(List.of()), of(List.of()), of(List.of()), CURRENT);

        assertThat(PatientHistoryFormatter.format(h)).contains("- 2026-04-01: Clavon 1 ml, günde 2, 7 gün, Enjeksiyon")
            .doesNotContain("(bu muayene)");
    }

    @Test
    void hidesContactDetailsWrittenIntoFreeTextFields() {
        var profile = new PatientClinicalProfile("Kedi", null, Sex.MALE, false, null, null, null,
            "Penisilin alerjisi - acil durumda sahibi 0555 123 45 67");
        PatientHistory h = new PatientHistory(TODAY, profile, null,
            of(List.of(new PastEncounterSummary(Instant.parse("2026-03-01T09:00:00Z"), "Gastrit", "Sonuç ali@ornek.com adresine"))),
            of(List.of()), of(List.of()), of(List.of()), of(List.of()), CURRENT);

        String text = PatientHistoryFormatter.format(h);

        assertThat(text).contains("Kritik uyarı: Penisilin alerjisi - acil durumda sahibi [telefon gizlendi]")
            .contains("Plan: Sonuç [e-posta gizlendi] adresine")
            .doesNotContain("0555").doesNotContain("ali@ornek.com");
    }
}
