package com.vetos.modules.inventory.domain;

import com.vetos.modules.inventory.domain.PresentationUnitGuesser.UnitGuess;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.vetos.modules.inventory.domain.PresentationUnitGuesser.guess;
import static org.assertj.core.api.Assertions.assertThat;

/** Takdim seklinden kullanim birimi tahmini (spec 2026-10-24 §3.4); ornekler canli TARBIL verisinden. */
class PresentationUnitGuesserTest {

    private static final String MED = "VETILAC_MEDICINE";
    private static final String VAC = "HBSAPP_VACCINE";

    private static UnitGuess ml(String size) {
        return new UnitGuess(StockUnit.ML, new BigDecimal(size));
    }

    @Test
    void clavon_isMl50() {
        assertThat(guess(MED, "50 ml'lik renksiz cam şişeler", null)).isEqualTo(ml("50"));
    }

    @Test
    void tenidur_isMl20() {
        assertThat(guess(MED, "kutu içerisinde 20 ml amber renkli cam flakonlarda", null)).isEqualTo(ml("20"));
    }

    @Test
    void decimalAndUppercase_areRead() {
        assertThat(guess(MED, "Kutu içinde 50 ml.lik teflonize tıpalı, alüminyum kapşonlu kahverengi cam şişelerde.", null)).isEqualTo(ml("50"));
        assertThat(guess(MED, "Karton kutu içerisinde 50ml’lik beyaz flip-off kapak ile kapatılmış amber renkli tip I cam flakonlarda", null)).isEqualTo(ml("50"));
    }

    @Test
    void oridermyl_isG10() {
        assertThat(guess(MED, "10 g ’lık alüminyum tüplerde karton kutu içerisinde", null))
            .isEqualTo(new UnitGuess(StockUnit.G, new BigDecimal("10")));
    }

    @Test
    void pipette_isAdet_evenWithMl() {
        assertThat(guess(MED, "karton kutu içerisinde 3 adetlik 1,34 ml’lik pipetlerde", null).unit()).isEqualTo(StockUnit.ADET);
        assertThat(guess(MED, "0.25 ml polipropilen pipet içeren karton kutuda 4 pipet", null).unit()).isEqualTo(StockUnit.ADET);
    }

    @Test
    void tablet_isAdet() {
        assertThat(guess(MED, "Karton kutu içerisinde 10 tabletlik blisterlerde", null).unit()).isEqualTo(StockUnit.ADET);
    }

    @Test
    void syringe_isAdet() {
        assertThat(guess(MED, "10 ml beyaz LLDPE enjektörlerde karton kutuda", null).unit()).isEqualTo(StockUnit.ADET);
    }

    @Test
    void unknownPresentation_isAdet() {
        assertThat(guess(MED, null, null)).isEqualTo(new UnitGuess(StockUnit.ADET, null));
        assertThat(guess(MED, "Karton kutuda", null)).isEqualTo(new UnitGuess(StockUnit.ADET, null));
    }

    @Test
    void vaccine_usesDoseColumn() {
        assertThat(guess(VAC, "1 ml flakonda sulandırıcısı ile birlikte, Kutusunda 20 adet flakon", 20))
            .isEqualTo(new UnitGuess(StockUnit.FLAKON, new BigDecimal("20")));
    }

    @Test
    void vaccine_fallsBackToText() {
        assertThat(guess(VAC, "1 dozluk aşı içeren şişe ve 1 ml sulandırıcısı ile birlikte; plastik kutusunda 25 adet", null))
            .isEqualTo(new UnitGuess(StockUnit.FLAKON, new BigDecimal("25")));
    }

    @Test
    void vaccine_withoutCount_isAdet() {
        assertThat(guess(VAC, "Flakon", null)).isEqualTo(new UnitGuess(StockUnit.ADET, null));
    }
}
