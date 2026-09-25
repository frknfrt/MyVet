package com.vetos.modules.lab.application.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LabResultFileContentTest {

    @Test
    void should_beEqual_when_sameFieldsAndDifferentButIdenticalContentArrayInstances() {
        // Iki ayri byte[] ornegi, ayni icerikle -- FileStoragePort'un iki farkli
        // retrieve() cagrisinin dondurdugu ayri array'leri temsil eder.
        LabResultFileContent a = new LabResultFileContent("sonuc.pdf", "application/pdf", new byte[] {1, 2, 3});
        LabResultFileContent b = new LabResultFileContent("sonuc.pdf", "application/pdf", new byte[] {1, 2, 3});

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void should_notBeEqual_when_contentDiffers() {
        LabResultFileContent a = new LabResultFileContent("sonuc.pdf", "application/pdf", new byte[] {1, 2, 3});
        LabResultFileContent b = new LabResultFileContent("sonuc.pdf", "application/pdf", new byte[] {1, 2, 4});

        assertThat(a).isNotEqualTo(b);
    }
}
