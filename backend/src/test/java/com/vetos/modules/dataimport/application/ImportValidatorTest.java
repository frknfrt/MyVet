package com.vetos.modules.dataimport.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.vetos.modules.dataimport.application.excel.ParsedRow;
import com.vetos.modules.dataimport.application.excel.ParsedWorkbook;
import com.vetos.modules.dataimport.domain.ExistingDataLookup;
import com.vetos.modules.dataimport.domain.ExistingDataLookup.BreedRef;
import com.vetos.modules.dataimport.domain.ExistingDataLookup.OwnerRef;
import com.vetos.modules.dataimport.domain.ExistingDataLookup.SpeciesRef;
import com.vetos.modules.dataimport.domain.ImportEntityType;
import com.vetos.modules.dataimport.domain.ImportReport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportValidatorTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String SYSTEM = "vetpro";
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-05T09:00:00Z"), ZoneId.of("Europe/Istanbul"));

    private final UUID dogId = UUID.randomUUID();
    private final FakeLookup lookup = new FakeLookup(dogId);
    private final ImportValidator validator = new ImportValidator(CLOCK);

    // ---- yardimcilar ----
    private static ParsedRow row(int n, String... kv) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            values.put(kv[i], kv[i + 1]);
        }
        return new ParsedRow(n, values, Map.of());
    }

    private static ParsedRow owner(int n, String id, String phone) {
        return phone == null
                ? row(n, "kaynak_id", id, "ad_soyad", "Ayşe " + id)
                : row(n, "kaynak_id", id, "ad_soyad", "Ayşe " + id, "telefon", phone);
    }

    private static ParsedRow patient(int n, String id, String ownerId, String species, String... extra) {
        List<String> kv = new ArrayList<>(List.of("kaynak_id", id, "sahip_kaynak_id", ownerId, "ad", "Pati" + id, "tur", species));
        kv.addAll(List.of(extra));
        return row(n, kv.toArray(new String[0]));
    }

    private ImportReport validate(List<ParsedRow> owners, List<ParsedRow> patients) {
        return validator.validate(TENANT, SYSTEM, new ParsedWorkbook(owners, patients, List.of()), lookup);
    }

    private static List<String> messages(List<ImportReport.RowIssue> issues) {
        return issues.stream().map(ImportReport.RowIssue::message).toList();
    }

    // ---- testler ----
    @Test
    void should_countCreatesForCleanFile() {
        ImportReport r = validate(
                List.of(owner(2, "1", "05321234567"), owner(3, "2", "05329999999")),
                List.of(patient(2, "p1", "1", "Köpek"), patient(3, "p2", "1", "Köpek"), patient(4, "p3", "2", "Köpek")));

        assertThat(r.totals().get(ImportEntityType.OWNER).toCreate()).isEqualTo(2);
        assertThat(r.totals().get(ImportEntityType.PATIENT).toCreate()).isEqualTo(3);
        assertThat(r.errors()).isEmpty();
        assertThat(r.canCommit()).isTrue();
    }

    @Test
    void should_matchExistingOwner_acrossPhoneFormats() {
        UUID existing = UUID.randomUUID();
        lookup.clinicOwners.add(new OwnerRef(existing, "Mevcut Sahip", "5321234567", "5321234567"));

        ImportReport r = validate(
                List.of(owner(2, "1", "0532 123 45 67"), owner(3, "2", "+90 532 123 4567")), List.of());

        assertThat(r.ownerMatches()).hasSize(2);
        assertThat(r.ownerMatches()).allMatch(m -> m.existingOwnerId().equals(existing));
        ImportReport.EntityTotals t = r.totals().get(ImportEntityType.OWNER);
        assertThat(t.matched()).isEqualTo(2);
        assertThat(t.toCreate()).isZero();
    }

    @Test
    void should_warnAndMatchFirst_whenSeveralClinicOwnersShareThePhone() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        lookup.clinicOwners.add(new OwnerRef(first, "İlk Sahip", "5321234567", "5321234567"));
        lookup.clinicOwners.add(new OwnerRef(second, "İkinci Sahip", "05321234567", "5321234567"));

        ImportReport r = validate(List.of(owner(2, "1", "0532 123 45 67")), List.of());

        assertThat(r.ownerMatches()).singleElement()
                .satisfies(m -> assertThat(m.existingOwnerId()).isEqualTo(first));
        assertThat(r.warnings()).singleElement().satisfies(w -> {
            assertThat(w.rowNumber()).isEqualTo(2);
            assertThat(w.sourceId()).isEqualTo("1");
            assertThat(w.message())
                    .isEqualTo("Klinikte bu telefonla birden fazla sahip var: 0532 123 45 67 (ilk kayıtla eşleştirildi)");
        });
    }

    @Test
    void should_matchSpecies_turkishInsensitive() {
        ImportReport r = validate(
                List.of(owner(2, "1", "5321234567")),
                List.of(patient(2, "a", "1", "köpek"), patient(3, "b", "1", "KÖPEK"), patient(4, "c", "1", "Kopek")));

        assertThat(r.errors()).isEmpty();
        assertThat(r.newSpecies()).isEmpty();
    }

    @Test
    void should_listUnknownSpeciesAsNew() {
        ImportReport r = validate(
                List.of(owner(2, "1", "5321234567")),
                List.of(patient(2, "a", "1", "Tavşan"), patient(3, "b", "1", "tavsan")));

        assertThat(r.errors()).isEmpty();
        assertThat(r.newSpecies()).containsExactly("Tavşan");
        assertThat(r.totals().get(ImportEntityType.PATIENT).toCreate()).isEqualTo(2);
    }

    @Test
    void should_listUnknownBreedsAsNew_oncePerSpecies() {
        lookup.breeds.add(new BreedRef(UUID.randomUUID(), dogId, "Golden Retriever"));

        ImportReport r = validate(
                List.of(owner(2, "1", "5321234567")),
                List.of(patient(2, "a", "1", "Köpek", "irk", "golden retriever"),
                        patient(3, "b", "1", "Köpek", "irk", "Karakaçan"),
                        patient(4, "c", "1", "köpek", "irk", "karakacan"),
                        patient(5, "d", "1", "Tavşan", "irk", "Hollanda Lop")));

        assertThat(r.errors()).isEmpty();
        assertThat(r.warnings()).isEmpty();
        assertThat(r.newBreeds()).containsExactly(
                new ImportReport.NewBreed("Köpek", "Karakaçan"),
                new ImportReport.NewBreed("Tavşan", "Hollanda Lop"));
        assertThat(r.totals().get(ImportEntityType.PATIENT).toCreate()).isEqualTo(4);
    }

    @Test
    void should_errorOnBadDate_andAcceptBothFormats() {
        ImportReport r = validate(
                List.of(owner(2, "1", "5321234567")),
                List.of(patient(2, "a", "1", "Köpek", "dogum_tarihi", "05.03.2019"),
                        patient(3, "b", "1", "Köpek", "dogum_tarihi", "2019-03-05"),
                        patient(4, "c", "1", "Köpek", "dogum_tarihi", "5 Mart"),
                        patient(5, "d", "1", "Köpek", "dogum_tarihi", "2026-11-06")));

        assertThat(r.errors()).extracting(ImportReport.RowIssue::rowNumber).containsExactly(4, 5);
        assertThat(r.errors().get(0).message()).isEqualTo("'dogum_tarihi' okunamadı: '5 Mart'");
        assertThat(r.errors().get(1).message()).isEqualTo("'dogum_tarihi' gelecekte");
        assertThat(r.errors()).allMatch(e -> e.sheet().equals("Hastalar"));
        assertThat(r.totals().get(ImportEntityType.PATIENT).toCreate()).isEqualTo(2);
        assertThat(r.totals().get(ImportEntityType.PATIENT).errorRows()).isEqualTo(2);
    }

    @Test
    void should_errorOnDuplicateSourceId() {
        ImportReport r = validate(
                List.of(owner(2, "1", "5321234567"), owner(3, "1", "5329999999")), List.of());

        assertThat(messages(r.errors())).contains("Aynı kaynak_id dosyada tekrar ediyor");
        assertThat(r.totals().get(ImportEntityType.OWNER).errorRows()).isEqualTo(2);
        assertThat(r.totals().get(ImportEntityType.OWNER).toCreate()).isZero();
    }

    @Test
    void should_cascadeOwnerErrorToPatients() {
        ImportReport r = validate(
                List.of(owner(2, "1", null)),
                List.of(patient(2, "a", "1", "Köpek")));

        assertThat(messages(r.errors())).contains("'telefon' boş", "Sahip satırı hatalı");
        assertThat(r.totals().get(ImportEntityType.PATIENT).errorRows()).isEqualTo(1);
        assertThat(r.canCommit()).isFalse();
    }

    @Test
    void should_errorOnMissingOwner_badPhone_badEnums() {
        ImportReport r = validate(
                List.of(owner(2, "1", "123")),
                List.of(patient(2, "a", "99", "Köpek", "cinsiyet", "X", "kisirlastirilmis", "belki"),
                        patient(3, "b", "99", "Köpek", "cinsiyet", "dişi", "kisirlastirilmis", "Hayır")));

        assertThat(messages(r.errors())).contains(
                "'telefon' geçersiz: '123' (10 haneli olmalı)",
                "Sahip bulunamadı: sahip_kaynak_id '99'",
                "'cinsiyet' tanınmıyor: 'X' (Erkek/Dişi bekleniyor)");
        assertThat(r.errors().stream().filter(e -> e.rowNumber() == 3 && e.sheet().equals("Hastalar")).count())
                .isEqualTo(1); // yalnizca eksik sahip hatasi; cinsiyet/kisirlastirilmis gecerli
    }

    @Test
    void should_resolvePatientOwnerFromPreviousImport() {
        lookup.imported.put(ImportEntityType.OWNER, new HashSet<>(Set.of("77")));

        ImportReport r = validate(List.of(), List.of(patient(2, "a", "77", "Köpek")));

        assertThat(r.errors()).isEmpty();
        assertThat(r.totals().get(ImportEntityType.PATIENT).toCreate()).isEqualTo(1);
    }

    @Test
    void should_skipAlreadyImportedRows() {
        lookup.imported.put(ImportEntityType.OWNER, new HashSet<>(Set.of("1")));

        ImportReport r = validate(
                List.of(owner(2, "1", "5321234567"), owner(3, "2", "5329999999")), List.of());

        ImportReport.EntityTotals t = r.totals().get(ImportEntityType.OWNER);
        assertThat(t.alreadyImported()).isEqualTo(1);
        assertThat(t.toCreate()).isEqualTo(1);
        assertThat(r.errors()).isEmpty();
    }

    @Test
    void should_warnOnDuplicatePhoneInFile() {
        ImportReport r = validate(
                List.of(owner(2, "1", "5321234567"), owner(3, "2", "0532 123 45 67")), List.of());

        assertThat(r.errors()).isEmpty();
        assertThat(r.warnings()).hasSize(2);
        assertThat(r.warnings().get(0).message()).startsWith("Aynı telefon dosyada birden fazla sahipte: ");
        assertThat(r.totals().get(ImportEntityType.OWNER).toCreate()).isEqualTo(2);
    }

    @Test
    void should_notCommit_whenStructuralErrors() {
        ImportReport r = validator.validate(TENANT, SYSTEM,
                new ParsedWorkbook(List.of(owner(2, "1", "5321234567")), List.of(), List.of("'Hastalar' sayfası yok")),
                lookup);

        assertThat(r.structuralErrors()).containsExactly("'Hastalar' sayfası yok");
        assertThat(r.canCommit()).isFalse();
    }

    @Test
    void should_chunkLookupCalls_atMost1000Ids() {
        List<ParsedRow> owners = new ArrayList<>();
        for (int i = 0; i < 2500; i++) {
            owners.add(owner(i + 2, "o" + i, String.format("532%07d", i)));
        }

        validate(owners, List.of());

        assertThat(lookup.ownerImportedCalls).hasSize(3);
        assertThat(lookup.ownerImportedCalls).allMatch(size -> size <= 1000);
        assertThat(lookup.phoneCalls).hasSize(3);
        assertThat(lookup.phoneCalls).allMatch(size -> size <= 1000);
    }

    // ---- sahte ExistingDataLookup ----
    private static final class FakeLookup implements ExistingDataLookup {
        final UUID dogId;
        final List<BreedRef> breeds = new ArrayList<>();
        final List<OwnerRef> clinicOwners = new ArrayList<>();
        final Map<ImportEntityType, Set<String>> imported = new java.util.EnumMap<>(ImportEntityType.class);
        final List<Integer> ownerImportedCalls = new ArrayList<>();
        final List<Integer> phoneCalls = new ArrayList<>();

        FakeLookup(UUID dogId) {
            this.dogId = dogId;
        }

        @Override
        public List<SpeciesRef> species() {
            return List.of(new SpeciesRef(dogId, "Köpek"), new SpeciesRef(UUID.randomUUID(), "Kedi"));
        }

        @Override
        public List<BreedRef> breeds(UUID speciesId) {
            return breeds.stream().filter(b -> b.speciesId().equals(speciesId)).toList();
        }

        @Override
        public List<OwnerRef> ownersByNormalizedPhones(UUID tenantId, Collection<String> normalizedPhones) {
            phoneCalls.add(normalizedPhones.size());
            return clinicOwners.stream().filter(o -> normalizedPhones.contains(o.normalizedPhone())).toList();
        }

        @Override
        public Set<String> alreadyImported(UUID tenantId, String sourceSystem, ImportEntityType type,
                                           Collection<String> sourceIds) {
            if (type == ImportEntityType.OWNER) {
                ownerImportedCalls.add(sourceIds.size());
            }
            Set<String> known = imported.getOrDefault(type, Set.of());
            Set<String> out = new HashSet<>();
            for (String id : sourceIds) {
                if (known.contains(id)) {
                    out.add(id);
                }
            }
            return out;
        }
    }
}
