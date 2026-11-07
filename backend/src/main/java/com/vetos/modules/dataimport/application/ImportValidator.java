package com.vetos.modules.dataimport.application;

import com.vetos.modules.dataimport.application.excel.ParsedRow;
import com.vetos.modules.dataimport.application.excel.ParsedWorkbook;
import com.vetos.modules.dataimport.application.excel.PhoneNormalizer;
import com.vetos.modules.dataimport.application.excel.TurkishText;
import com.vetos.modules.dataimport.domain.ExistingDataLookup;
import com.vetos.modules.dataimport.domain.ExistingDataLookup.BreedRef;
import com.vetos.modules.dataimport.domain.ExistingDataLookup.OwnerRef;
import com.vetos.modules.dataimport.domain.ExistingDataLookup.SpeciesRef;
import com.vetos.modules.dataimport.domain.ImportEntityType;
import com.vetos.modules.dataimport.domain.ImportReport;
import com.vetos.modules.dataimport.domain.ImportReport.EntityTotals;
import com.vetos.modules.dataimport.domain.ImportReport.NewBreed;
import com.vetos.modules.dataimport.domain.ImportReport.OwnerMatch;
import com.vetos.modules.dataimport.domain.ImportReport.RowIssue;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Aktarim dosyasini veritabanina yazmadan dogrular ve onizleme raporu uretir.
 * Oncelik (tek satir tek kovada): hata > daha once aktarilmis > eslesen > olusturulacak.
 */
@Component
public class ImportValidator {

    static final int LOOKUP_CHUNK_SIZE = 1000;
    private static final String OWNERS_SHEET = "Sahipler";
    private static final String PATIENTS_SHEET = "Hastalar";
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DOTTED_DATE =
            DateTimeFormatter.ofPattern("d.M.uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final Set<String> GENDERS = Set.of("erkek", "disi", "e", "d");
    private static final Set<String> YES_NO = Set.of("evet", "hayir", "e", "h");

    private final Clock clock;

    @Autowired
    public ImportValidator() {
        this(Clock.system(ISTANBUL));
    }

    public ImportValidator(Clock clock) {
        this.clock = clock;
    }

    public ImportReport validate(UUID tenantId, String sourceSystem, ParsedWorkbook wb, ExistingDataLookup lookup) {
        List<RowIssue> errors = new ArrayList<>();
        List<RowIssue> warnings = new ArrayList<>();
        List<OwnerMatch> ownerMatches = new ArrayList<>();
        Map<String, String> newSpecies = new LinkedHashMap<>();

        // ---- Sahipler ----
        List<ParsedRow> owners = wb.owners();
        Map<String, Integer> ownerIdCounts = countSourceIds(owners);
        Set<String> importedOwners = alreadyImportedChunked(
                lookup, tenantId, sourceSystem, ImportEntityType.OWNER, ownerIdCounts.keySet());

        Map<String, List<ParsedRow>> byPhone = new LinkedHashMap<>();
        Map<ParsedRow, String> phoneOf = new IdentityHashMap<>();
        Map<ParsedRow, List<String>> ownerErrors = new IdentityHashMap<>();
        for (ParsedRow row : owners) {
            List<String> rowErrors = new ArrayList<>();
            required(row, "kaynak_id", rowErrors);
            required(row, "ad_soyad", rowErrors);
            String sourceId = row.values().get("kaynak_id");
            if (sourceId != null && ownerIdCounts.get(sourceId) > 1) {
                rowErrors.add("Aynı kaynak_id dosyada tekrar ediyor");
            }
            String rawPhone = row.values().get("telefon");
            if (rawPhone == null) {
                rowErrors.add("'telefon' boş");
            } else {
                String normalized = PhoneNormalizer.normalize(rawPhone);
                if (normalized.length() != 10) {
                    rowErrors.add("'telefon' geçersiz: '" + rawPhone + "' (10 haneli olmalı)");
                } else {
                    phoneOf.put(row, normalized);
                    byPhone.computeIfAbsent(normalized, k -> new ArrayList<>()).add(row);
                }
            }
            ownerErrors.put(row, rowErrors);
        }

        Map<String, List<OwnerRef>> clinicOwners = new HashMap<>();
        for (OwnerRef ref : ownersByPhonesChunked(lookup, tenantId, byPhone.keySet())) {
            clinicOwners.computeIfAbsent(ref.normalizedPhone(), k -> new ArrayList<>()).add(ref);
        }

        Set<String> okOwnerIds = new HashSet<>();
        Set<String> badOwnerIds = new HashSet<>();
        int ownerToCreate = 0;
        int ownerMatched = 0;
        int ownerAlready = 0;
        int ownerErrorRows = 0;
        for (ParsedRow row : owners) {
            String sourceId = row.values().get("kaynak_id");
            List<String> rowErrors = ownerErrors.get(row);
            if (!rowErrors.isEmpty()) {
                ownerErrorRows++;
                addIssues(errors, OWNERS_SHEET, row, sourceId, rowErrors);
                if (sourceId != null) {
                    badOwnerIds.add(sourceId);
                }
                continue;
            }
            okOwnerIds.add(sourceId);
            String phone = phoneOf.get(row);
            if (byPhone.get(phone).size() > 1) {
                warnings.add(new RowIssue(OWNERS_SHEET, row.rowNumber(), sourceId,
                        "Aynı telefon dosyada birden fazla sahipte: " + row.values().get("telefon")));
            }
            if (importedOwners.contains(sourceId)) {
                ownerAlready++;
                continue;
            }
            List<OwnerRef> existing = clinicOwners.get(phone);
            if (existing != null && !existing.isEmpty()) {
                // Lookup sirali doner (created_at, id): birden fazla aday varsa en eski kayit secilir ve admin uyarilir.
                OwnerRef match = existing.get(0);
                if (existing.size() > 1) {
                    warnings.add(new RowIssue(OWNERS_SHEET, row.rowNumber(), sourceId,
                            "Klinikte bu telefonla birden fazla sahip var: " + row.values().get("telefon")
                                    + " (ilk kayıtla eşleştirildi)"));
                }
                ownerMatched++;
                ownerMatches.add(new OwnerMatch(sourceId, row.values().get("ad_soyad"), row.values().get("telefon"),
                        match.id(), match.fullName()));
            } else {
                ownerToCreate++;
            }
        }

        // ---- Hastalar ----
        List<ParsedRow> patients = wb.patients();
        Map<String, Integer> patientIdCounts = countSourceIds(patients);
        Set<String> importedPatients = alreadyImportedChunked(
                lookup, tenantId, sourceSystem, ImportEntityType.PATIENT, patientIdCounts.keySet());

        // Dosyada olmayan sahip referanslari daha once aktarilmis mi?
        Set<String> unknownOwnerRefs = new LinkedHashSet<>();
        for (ParsedRow row : patients) {
            String ref = row.values().get("sahip_kaynak_id");
            if (ref != null && !okOwnerIds.contains(ref) && !badOwnerIds.contains(ref)) {
                unknownOwnerRefs.add(ref);
            }
        }
        Set<String> previouslyImportedOwners = alreadyImportedChunked(
                lookup, tenantId, sourceSystem, ImportEntityType.OWNER, unknownOwnerRefs);

        Map<String, UUID> speciesIdByFold = new HashMap<>();
        Map<String, String> speciesNameByFold = new HashMap<>();
        for (SpeciesRef s : lookup.species()) {
            String fold = TurkishText.fold(s.name()).strip();
            speciesIdByFold.putIfAbsent(fold, s.id());
            speciesNameByFold.putIfAbsent(fold, s.name());
        }
        Map<UUID, Set<String>> breedFoldsBySpecies = new HashMap<>();
        // anahtar: "<tur fold>|<irk fold>" -> ilk gorulen yazim; ayni irk farkli yazilsa da bir kez eklenir.
        Map<String, NewBreed> newBreeds = new LinkedHashMap<>();

        int patientToCreate = 0;
        int patientAlready = 0;
        int patientErrorRows = 0;
        for (ParsedRow row : patients) {
            String sourceId = row.values().get("kaynak_id");
            List<String> rowErrors = new ArrayList<>();
            required(row, "kaynak_id", rowErrors);
            required(row, "sahip_kaynak_id", rowErrors);
            required(row, "ad", rowErrors);
            required(row, "tur", rowErrors);
            if (sourceId != null && patientIdCounts.get(sourceId) > 1) {
                rowErrors.add("Aynı kaynak_id dosyada tekrar ediyor");
            }

            String ownerRef = row.values().get("sahip_kaynak_id");
            if (ownerRef != null) {
                if (badOwnerIds.contains(ownerRef)) {
                    rowErrors.add("Sahip satırı hatalı");
                } else if (!okOwnerIds.contains(ownerRef) && !previouslyImportedOwners.contains(ownerRef)) {
                    rowErrors.add("Sahip bulunamadı: sahip_kaynak_id '" + ownerRef + "'");
                }
            }

            String gender = row.values().get("cinsiyet");
            if (gender != null && !GENDERS.contains(TurkishText.fold(gender).strip())) {
                rowErrors.add("'cinsiyet' tanınmıyor: '" + gender + "' (Erkek/Dişi bekleniyor)");
            }
            String neutered = row.values().get("kisirlastirilmis");
            if (neutered != null && !YES_NO.contains(TurkishText.fold(neutered).strip())) {
                rowErrors.add("'kisirlastirilmis' tanınmıyor: '" + neutered + "' (Evet/Hayır bekleniyor)");
            }
            String birth = row.values().get("dogum_tarihi");
            if (birth != null) {
                LocalDate date = parseDate(birth);
                if (date == null) {
                    rowErrors.add("'dogum_tarihi' okunamadı: '" + birth + "'");
                } else if (date.isAfter(LocalDate.now(clock))) {
                    rowErrors.add("'dogum_tarihi' gelecekte");
                }
            }

            if (!rowErrors.isEmpty()) {
                patientErrorRows++;
                addIssues(errors, PATIENTS_SHEET, row, sourceId, rowErrors);
                continue;
            }
            if (importedPatients.contains(sourceId)) {
                patientAlready++;
                continue;
            }
            patientToCreate++;

            String species = row.values().get("tur").strip();
            String speciesFold = TurkishText.fold(species).strip();
            UUID speciesId = speciesIdByFold.get(speciesFold);
            if (speciesId == null) {
                newSpecies.putIfAbsent(speciesFold, species);
            }
            String breed = row.values().get("irk");
            if (breed != null) {
                boolean found = false;
                if (speciesId != null) {
                    Set<String> folds = breedFoldsBySpecies.computeIfAbsent(speciesId, id -> {
                        Set<String> set = new HashSet<>();
                        for (BreedRef b : lookup.breeds(id)) {
                            set.add(TurkishText.fold(b.name()).strip());
                        }
                        return set;
                    });
                    found = folds.contains(TurkishText.fold(breed).strip());
                }
                if (!found) {
                    String speciesName = speciesNameByFold.getOrDefault(speciesFold, newSpecies.get(speciesFold));
                    newBreeds.putIfAbsent(speciesFold + "|" + TurkishText.fold(breed).strip(),
                            new NewBreed(speciesName, breed.strip()));
                }
            }
        }

        Map<ImportEntityType, EntityTotals> totals = new EnumMap<>(ImportEntityType.class);
        totals.put(ImportEntityType.OWNER,
                new EntityTotals(owners.size(), ownerToCreate, ownerMatched, ownerAlready, ownerErrorRows));
        totals.put(ImportEntityType.PATIENT,
                new EntityTotals(patients.size(), patientToCreate, 0, patientAlready, patientErrorRows));
        return new ImportReport(totals, errors, warnings, ownerMatches,
                new ArrayList<>(newSpecies.values()), new ArrayList<>(newBreeds.values()),
                new ArrayList<>(wb.structuralErrors()));
    }

    private static void required(ParsedRow row, String column, List<String> rowErrors) {
        if (row.values().get(column) == null) {
            rowErrors.add("'" + column + "' boş");
        }
    }

    private static void addIssues(List<RowIssue> target, String sheet, ParsedRow row, String sourceId,
                                  List<String> messages) {
        for (String message : messages) {
            target.add(new RowIssue(sheet, row.rowNumber(), sourceId, message));
        }
    }

    private static Map<String, Integer> countSourceIds(List<ParsedRow> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ParsedRow row : rows) {
            String id = row.values().get("kaynak_id");
            if (id != null) {
                counts.merge(id, 1, Integer::sum);
            }
        }
        return counts;
    }

    /** ISO (2020-03-01) ya da gg.aa.yyyy; okunamazsa null. Isci de ayni kurali kullanir. */
    static LocalDate parseDate(String text) {
        String s = text.strip();
        try {
            return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            // dd.MM.yyyy dene
        }
        try {
            return LocalDate.parse(s, DOTTED_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Set<String> alreadyImportedChunked(ExistingDataLookup lookup, UUID tenantId, String sourceSystem,
                                                      ImportEntityType type, Collection<String> ids) {
        Set<String> result = new HashSet<>();
        for (List<String> chunk : chunks(ids)) {
            result.addAll(lookup.alreadyImported(tenantId, sourceSystem, type, chunk));
        }
        return result;
    }

    private static List<OwnerRef> ownersByPhonesChunked(ExistingDataLookup lookup, UUID tenantId,
                                                        Collection<String> phones) {
        List<OwnerRef> result = new ArrayList<>();
        for (List<String> chunk : chunks(phones)) {
            result.addAll(lookup.ownersByNormalizedPhones(tenantId, chunk));
        }
        return result;
    }

    private static List<List<String>> chunks(Collection<String> values) {
        List<String> all = new ArrayList<>(values);
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i += LOOKUP_CHUNK_SIZE) {
            out.add(all.subList(i, Math.min(all.size(), i + LOOKUP_CHUNK_SIZE)));
        }
        return out;
    }
}
