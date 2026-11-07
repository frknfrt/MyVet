package com.vetos.modules.encounter.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DrugAllergyMatcherTest {

    private static final String PENISILIN = "Penisilin grubu antibiyotiklere karsi bilinen alerjisi var.";

    @Test
    void should_matchPenicillinGroup_byActiveIngredient() {
        List<DrugAllergyMatcher.Match> matches = DrugAllergyMatcher.match(PENISILIN, List.of("Amoksisilin", "Meloksikam"));

        assertThat(matches).extracting(DrugAllergyMatcher.Match::drugLabel).containsExactly("Amoksisilin");
        assertThat(matches.get(0).reason()).isEqualTo("Penisilin grubu");
    }

    @Test
    void should_matchAmoxicillinClavulanate_whenAlertNamesBetaLactams() {
        assertThat(DrugAllergyMatcher.match("Beta-laktam alerjisi", List.of("Synulox (amoksisilin + klavulanik asit)")))
            .hasSize(1);
    }

    @Test
    void should_matchCephalosporins_byWordPrefix_withoutDiacritics() {
        assertThat(DrugAllergyMatcher.match("SEFALOSPORİNLERE reaksiyon verdi", List.of("Sefaleksin 500 mg", "Seftiofur", "Spirotek")))
            .extracting(DrugAllergyMatcher.Match::drugLabel)
            .containsExactly("Sefaleksin 500 mg", "Seftiofur");
    }

    @Test
    void should_matchNsaids_andFluoroquinolones() {
        assertThat(DrugAllergyMatcher.match("NSAİİ kullanılmamalı, böbrek yetmezliği", List.of("BAVET MELOXİCAM = Bavet Meloxicam")))
            .extracting(DrugAllergyMatcher.Match::reason).containsExactly("NSAİİ (steroid olmayan ağrı kesici)");
        assertThat(DrugAllergyMatcher.match("Enrofloksasin sonrası kusma", List.of("Marbofloksasin")))
            .extracting(DrugAllergyMatcher.Match::reason).containsExactly("Florokinolon grubu");
    }

    @Test
    void should_matchBrandName_whenAlertMentionsIt() {
        assertThat(DrugAllergyMatcher.match("Baytril'e karşı ciltte döküntü", List.of("BAYTRİL Flavour 50 mg")))
            .extracting(DrugAllergyMatcher.Match::reason).containsExactly("Uyarıda adı geçiyor");
    }

    @Test
    void should_notMatch_onGenericFormWords() {
        assertThat(DrugAllergyMatcher.match("Tabletleri yutmuyor, ısırabilir", List.of("Tenifen tablet"))).isEmpty();
    }

    @Test
    void should_returnEmpty_forBlankAlertOrUnrelatedDrugs() {
        assertThat(DrugAllergyMatcher.match(null, List.of("Amoksisilin"))).isEmpty();
        assertThat(DrugAllergyMatcher.match("  ", List.of("Amoksisilin"))).isEmpty();
        assertThat(DrugAllergyMatcher.match(PENISILIN, List.of("Ketamin", "Spirotek"))).isEmpty();
    }
}
