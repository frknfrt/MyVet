package com.vetos.modules.ai.application;

import com.vetos.modules.ai.application.PatientHistory.Section;
import com.vetos.modules.encounter.domain.DrugRoute;
import com.vetos.modules.encounter.domain.PrescriptionHistoryEntry;
import com.vetos.modules.imaging.domain.ImagingModality;
import com.vetos.modules.lab.domain.LabValueFlag;
import com.vetos.modules.patient.domain.PatientClinicalProfile;
import com.vetos.modules.patient.domain.Sex;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Period;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** PatientHistory'yi AI'ya gidecek düz metne çevirir (spec 2026-10-25-ai-hasta-gecmisi). Claude ve Ollama aynı metni kullanır. */
public final class PatientHistoryFormatter {

    private static final String NONE = "belirtilmemiş";

    private PatientHistoryFormatter() {}

    public static String format(PatientHistory h) {
        StringBuilder sb = new StringBuilder();
        sb.append("HASTA BİLGİSİ\n");
        appendProfile(sb, h);

        appendSection(sb, "GEÇMİŞ MUAYENELER (son 1 yıl, en yeniden eskiye)", h.encounters(),
            e -> "- " + day(e.date()) + " | Değerlendirme: " + text(e.assessment()) + " | Plan: " + text(e.plan()));
        appendSection(sb, "İLAÇLAR / REÇETELER (son 1 yıl)", h.prescriptions(),
            p -> "- " + p.issuedDate() + (p.encounterId() != null && p.encounterId().equals(h.currentEncounterId()) ? " (bu muayene)" : "")
                + ": " + (p.items().isEmpty() ? "kalem yok" : p.items().stream().map(PatientHistoryFormatter::item).collect(Collectors.joining("; "))));
        appendSection(sb, "AŞILAR (son 1 yıl)", h.vaccinations(),
            v -> "- " + v.administeredDate() + " " + text(v.vaccineName()) + (v.nextDueDate() != null ? " (sonraki: " + v.nextDueDate() + ")" : ""));
        appendSection(sb, "KAN / LAB SONUÇLARI (son 1 yıl)", h.labs(), l -> {
            StringBuilder line = new StringBuilder("- " + day(l.date()) + " " + text(l.testName()));
            if (!blank(l.resultSummary())) line.append(" — özet: ").append(l.resultSummary().trim());
            for (var v : l.values()) {
                line.append("\n  * ").append(text(v.parameterName())).append(": ").append(text(v.value()));
                if (!blank(v.unit())) line.append(' ').append(v.unit().trim());
                if (!blank(v.referenceRange())) line.append(" (ref ").append(v.referenceRange().trim()).append(')');
                String flag = flag(v.flag());
                if (flag != null) line.append(' ').append(flag);
            }
            return line.toString();
        });
        appendSection(sb, "GÖRÜNTÜLEME (son 1 yıl)", h.imaging(),
            i -> "- " + day(i.date()) + " " + modality(i.modality()) + ", " + text(i.bodyRegion()) + ": "
                + (blank(i.findings()) ? "bulgu yazılmamış" : i.findings().trim()));
        // Serbest metne yazılmış telefon/TC/e-posta AI'ya gitmesin (2026-10-25).
        return PersonalDataRedactor.redact(sb.toString().trim());
    }

    private static void appendProfile(StringBuilder sb, PatientHistory h) {
        PatientClinicalProfile p = h.profile();
        if (p == null) {
            sb.append("okunamadı\n");
            return;
        }
        BigDecimal weight = h.encounterWeightKg() != null ? h.encounterWeightKg() : p.weightKg();
        sb.append("Tür: ").append(text(p.speciesName()))
            .append(" | Irk: ").append(text(p.breedName()))
            .append(" | Yaş: ").append(age(p, h))
            .append(" | Cinsiyet: ").append(sex(p.sex())).append(", ").append(p.neutered() ? "kısırlaştırılmış" : "kısırlaştırılmamış")
            .append(" | Kilo: ").append(weight == null ? NONE : weight.stripTrailingZeros().toPlainString() + " kg")
            .append(" | Kan grubu: ").append(text(p.bloodType()))
            .append('\n');
        if (!blank(p.criticalAlert())) sb.append("Kritik uyarı: ").append(p.criticalAlert().trim()).append('\n');
    }

    private static <T> void appendSection(StringBuilder sb, String title, Section<T> section, Function<T, String> line) {
        sb.append('\n').append(title).append('\n');
        if (section.failed()) {
            sb.append("okunamadı\n");
            return;
        }
        if (section.items().isEmpty()) {
            sb.append("kayıt yok\n");
            return;
        }
        for (T item : section.items()) sb.append(line.apply(item)).append('\n');
        if (section.truncated()) sb.append("(daha eski kayıtlar kısaltıldı)\n");
    }

    private static String item(PrescriptionHistoryEntry.Item i) {
        List<String> parts = new java.util.ArrayList<>();
        parts.add(text(i.drugName()) + (blank(i.dosage()) ? "" : " " + i.dosage().trim()));
        if (!blank(i.frequency())) parts.add(i.frequency().trim());
        if (i.durationDays() > 0) parts.add(i.durationDays() + " gün");
        if (i.route() != null) parts.add(route(i.route()));
        return String.join(", ", parts);
    }

    private static String age(PatientClinicalProfile p, PatientHistory h) {
        if (p.birthDate() == null || h.encounterDate() == null || p.birthDate().isAfter(h.encounterDate())) return NONE;
        Period period = Period.between(p.birthDate(), h.encounterDate());
        if (period.getYears() == 0) return period.getMonths() + " ay";
        return period.getYears() + " yıl" + (period.getMonths() > 0 ? " " + period.getMonths() + " ay" : "");
    }

    private static String sex(Sex sex) {
        if (sex == null) return NONE;
        return switch (sex) {
            case MALE -> "Erkek";
            case FEMALE -> "Dişi";
            case UNKNOWN -> "Bilinmiyor";
        };
    }

    private static String modality(ImagingModality m) {
        if (m == null) return NONE;
        return switch (m) {
            case XRAY -> "Röntgen";
            case ULTRASOUND -> "Ultrason";
            case DICOM -> "DICOM";
            case OTHER -> "Diğer";
        };
    }

    private static String route(DrugRoute r) {
        return switch (r) {
            case ORAL -> "Oral";
            case TOPICAL -> "Topikal";
            case INJECTABLE -> "Enjeksiyon";
        };
    }

    private static String flag(LabValueFlag f) {
        if (f == null) return null;
        return switch (f) {
            case LOW -> "DÜŞÜK";
            case HIGH -> "YÜKSEK";
            case ABNORMAL -> "ANORMAL";
            case NORMAL -> null;
        };
    }

    private static String day(Instant instant) {
        return instant == null ? NONE : instant.atZone(PatientHistoryAssembler.ISTANBUL).toLocalDate().toString();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String text(String s) {
        return blank(s) ? NONE : s.trim();
    }
}
