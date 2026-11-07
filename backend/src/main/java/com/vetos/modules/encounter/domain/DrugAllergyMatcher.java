package com.vetos.modules.encounter.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Hastanin serbest metin "kritik uyari"si ile secilen ilac adlari arasinda kural tabanli alerji eslesmesi.
 *
 * Iki kural var:
 * 1. Ilac grubu: uyari metni bir grubu anariyorsa (or. "penisilin"), o grubun etken madde kokleri ilac adinda aranir
 *    (or. "amoksi", "klavulan"). Gruplar yaygin veteriner antibiyotik/NSAII siniflaridir; marka adlari BILINCLI olarak
 *    listede yok -- etken madde bilgisi olmayan stok urunleri icin arayuz zaten her zaman onay ister.
 * 2. Ad gecmesi: ilac adindaki anlamli bir kelime (5+ harf, form kelimesi degil) uyarida geciyorsa (or. "Baytril'e").
 *
 * Eslesme yoksa bu "alerji yok" demek DEGILDIR; yalnizca kirmizi uyari ve gerekce zorunlulugu tetiklenmez.
 */
public final class DrugAllergyMatcher {

    private DrugAllergyMatcher() {}

    public record Match(String drugLabel, String reason) {}

    /** alertTerms uyari metninde, drugTerms ilac adinda aranir; "^" ile baslayan terim kelime basi demektir. */
    private record Group(String label, List<String> alertTerms, List<String> drugTerms) {}

    private static final List<Group> GROUPS = List.of(
        new Group("Penisilin grubu",
            List.of("penisilin", "penicillin", "beta laktam", "betalaktam", "amoksisilin", "amoxicillin", "ampisilin", "ampicillin"),
            List.of("penisilin", "penicillin", "amoksi", "amoxi", "ampisi", "ampici", "klavulan", "clavulan", "kloksasil", "cloxacil",
                "oksasil", "oxacil", "benzatin", "benzathin", "tikarsil", "ticarcil", "piperasil", "piperacil")),
        new Group("Sefalosporin grubu",
            List.of("sefalosporin", "cephalosporin", "beta laktam", "betalaktam", "sefaleksin", "cefalexin", "seftiofur", "ceftiofur",
                "sefovesin", "cefovecin"),
            List.of("^sef", "^cef", "cepha")),
        new Group("Sülfonamid grubu",
            List.of("sulfonamid", "sulfa", "trimetoprim", "trimethoprim"),
            List.of("sulfa", "trimetoprim", "trimethoprim")),
        new Group("NSAİİ (steroid olmayan ağrı kesici)",
            List.of("nsaii", "nsaid", "nsai", "steroid olmayan", "non steroid", "meloksikam", "meloxicam", "karprofen", "carprofen"),
            List.of("meloksi", "meloxi", "karprofen", "carprofen", "robenakoks", "robenacox", "firokoks", "firocox", "mavakoks",
                "mavacox", "ketoprofen", "fluniksin", "flunixin", "tolfenam")),
        new Group("Florokinolon grubu",
            List.of("florokinolon", "fluorokinolon", "fluoroquinolon", "kinolon", "quinolon", "enrofloksasin", "enrofloxacin",
                "marbofloksasin", "marbofloxacin"),
            List.of("floksasin", "floxacin")),
        new Group("Tetrasiklin grubu",
            List.of("tetrasiklin", "tetracyclin", "doksisiklin", "doxycyclin", "oksitetrasiklin", "oxytetracyclin"),
            List.of("siklin", "cyclin")),
        new Group("Makrolid grubu",
            List.of("makrolid", "macrolid", "eritromisin", "erythromycin", "tilozin", "tylosin", "azitromisin", "azithromycin"),
            List.of("tromisin", "thromycin", "tilozin", "tylosin", "tilmikosin", "tilmicosin")),
        new Group("Aminoglikozid grubu",
            List.of("aminoglikozid", "aminoglycosid", "gentamisin", "gentamicin", "neomisin", "neomycin", "streptomisin",
                "streptomycin", "amikasin", "amikacin"),
            List.of("gentami", "neomis", "neomyc", "streptomis", "streptomyc", "amikas", "amikac", "tobramis", "tobramyc"))
    );

    /** Ilac adinda sik gecen ama ilaci tanimlamayan kelimeler: ad gecmesi kuralinda yok sayilir. */
    private static final Set<String> FORM_WORDS = Set.of(
        "tablet", "kapsul", "sprey", "flakon", "enjeksiyon", "enjektabl", "damla", "krem", "merhem", "pomad", "solusyon",
        "suspansiyon", "surup", "kedi", "kopek", "flavour", "forte", "plus", "ihrac", "ruhsatli", "adet", "pipet", "ampul"
    );

    public static List<Match> match(String alertText, List<String> drugLabels) {
        if (alertText == null || alertText.isBlank()) return List.of();
        String alert = normalize(alertText);
        List<String> alertWords = words(alert);
        List<Group> named = GROUPS.stream().filter(g -> g.alertTerms().stream().anyMatch(alert::contains)).toList();

        List<Match> matches = new ArrayList<>();
        for (String label : drugLabels) {
            if (label == null || label.isBlank()) continue;
            String drug = normalize(label);
            String reason = named.stream()
                .filter(g -> g.drugTerms().stream().anyMatch(t -> containsTerm(drug, t)))
                .map(Group::label)
                .findFirst()
                .orElse(null);
            if (reason == null && mentionedInAlert(drug, alertWords)) reason = "Uyarıda adı geçiyor";
            if (reason != null) matches.add(new Match(label, reason));
        }
        return matches;
    }

    private static boolean containsTerm(String drug, String term) {
        if (!term.startsWith("^")) return drug.contains(term);
        String prefix = term.substring(1);
        return words(drug).stream().anyMatch(w -> w.startsWith(prefix));
    }

    /** Turkce ekler icin uyari kelimesinin ilac kelimesiyle BASLAMASI yeterli ("clavonla" ~ "clavon"). */
    private static boolean mentionedInAlert(String drug, List<String> alertWords) {
        return words(drug).stream()
            .filter(w -> w.length() >= 5 && !FORM_WORDS.contains(w) && w.chars().noneMatch(Character::isDigit))
            .anyMatch(w -> alertWords.stream().anyMatch(a -> a.startsWith(w)));
    }

    private static List<String> words(String normalized) {
        return Arrays.stream(normalized.split(" ")).filter(w -> !w.isEmpty()).toList();
    }

    static String normalize(String s) {
        String lower = s.toLowerCase(Locale.forLanguageTag("tr"))
            .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g').replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
            .replace('â', 'a').replace('î', 'i').replace('û', 'u')
            .replace("i̇", "i");
        return lower.replaceAll("[^a-z0-9]+", " ").trim();
    }
}
