package com.vetos.modules.inventory.domain;

import com.vetos.modules.inventory.domain.PresentationUnitGuesser.UnitGuess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static com.vetos.modules.inventory.domain.PresentationUnitGuesser.guess;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Takdim seklinden kullanim birimi tahmini (spec 2026-10-24 §3.4, 2026-10-25 §4.1). Ornekler canli TARBIL verisinden.
 * Tablet: kutudaki tablet sayisi. Pipet/tup: stok ml, paket = adet x parca ml'si (TARBIL pipeti ml olarak tutar).
 */
class PresentationUnitGuesserTest {

    private static final String MED = "VETILAC_MEDICINE";
    private static final String VAC = "HBSAPP_VACCINE";

    private static BigDecimal bd(String s) {
        if (s == null || s.isBlank()) return null;
        BigDecimal v = new BigDecimal(s.trim()).stripTrailingZeros();
        return v.scale() < 0 ? v.setScale(0) : v;
    }

    private static UnitGuess expected(String unit, String pkg, String piece, String label) {
        return new UnitGuess(StockUnit.valueOf(unit), bd(pkg), bd(piece), label == null || label.isBlank() ? null : label);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "50 ml'lik renksiz cam şişeler | ML | 50 | |",
        "kutu içerisinde 20 ml amber renkli cam flakonlarda | ML | 20 | |",
        "Kutu içinde 50 ml.lik teflonize tıpalı, alüminyum kapşonlu kahverengi cam şişelerde. | ML | 50 | |",
        "Karton kutu içerisinde 50ml’lik beyaz flip-off kapak ile kapatılmış amber renkli tip I cam flakonlarda | ML | 50 | |",
        "1 ml'de 50 mg içerir, 100 ml'lik amber şişe | ML | 100 | |",
        "10 g ’lık alüminyum tüplerde karton kutu içerisinde | G | 10 | |",
        "200 ml'lik beyaz aluminyum tüplerde | ML | 200 | |",
        "0.5 ml’lik pipetler 3 adetlik karton kutuda | ML | 1.5 | 0.5 | pipet",
        "Karton kutu içinde 0,8 ml'lik 4'er adet beyaz polipropilen tüp içeren blisterlerde | ML | 3.2 | 0.8 | tüp",
        "0.25 ml polipropilen pipet içeren karton kutuda 4 pipet | ML | 1.0 | 0.25 | pipet",
        "karton kutu içerisinde 3 adetlik 2.68 ml’lik pipetlerde | ML | 8.04 | 2.68 | pipet",
        "Karton Kutu içinde 0,4 ml. lik 3'er adet beyaz renkte propilen pipet | ML | 1.2 | 0.4 | pipet",
        "Karton kutu içerisinde 10 tabletlik blisterlerde | TABLET | 10 | |",
        "Her tablet alüminyum folyo poşet ile kaplı, 50 tabletlik karton kutularda | TABLET | 50 | |",
        "tablet | ADET | | |",
        "10 ml beyaz LLDPE enjektörlerde karton kutuda | ML | 10 | |",
        "5 ml sırınga | ML | 5 | |",
        "Karton kutuda | ADET | | |",
        // Değerlendirme C2: dozaj/ölçü pipetli şişe ve tek tüplü kutu parça değil
        "Karton kutuda 1 adet 100 ml'lik şişe ve 1 adet dozaj pipeti | ML | 100 | |",
        "100 ml'lik amber cam şişe, 1 adet ölçü pipeti ile | ML | 100 | |",
        "Karton kutuda 1 adet 10 ml tüp | ML | 10 | |",
        // Değerlendirme I2: paketi anlatan sayı seçilir, "1 ml'de" / "1 adet karton kutu" / "1 tablet 50 mg" değil
        "1 ml'de 100 mg içeren 0,5 ml'lik pipetler 3 adetlik karton kutuda | ML | 1.5 | 0.5 | pipet",
        "1 adet karton kutuda 4 adet 0,8 ml'lik pipet | ML | 3.2 | 0.8 | pipet",
        "1 tablet 50 mg içerir, 20 tabletlik blister | TABLET | 20 | |",
        "1 ml'de 50 mg içerir, 10 ml enjektör | ML | 10 | |",
        "10 adet tablet içeren kutu | TABLET | 10 | |",
    })
    void medicine(String presentation, String unit, String pkg, String piece, String label) {
        assertThat(guess(MED, presentation, null)).isEqualTo(expected(unit, pkg, piece, label));
    }

    @Test
    void unknownPresentation_isAdet() {
        assertThat(guess(MED, null, null)).isEqualTo(new UnitGuess(StockUnit.ADET, null));
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
        assertThat(guess(VAC, "1ml'lik flakonda, Plastik kutusunda 20 adet flakon", null))
            .isEqualTo(new UnitGuess(StockUnit.FLAKON, new BigDecimal("20")));
    }

    @Test
    void vaccine_withoutCount_isAdet() {
        assertThat(guess(VAC, "Flakon", null)).isEqualTo(new UnitGuess(StockUnit.ADET, null));
    }
}
