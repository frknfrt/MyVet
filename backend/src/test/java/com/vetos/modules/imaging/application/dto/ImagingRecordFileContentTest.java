package com.vetos.modules.imaging.application.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImagingRecordFileContentTest {

    @Test
    void should_beEqual_when_sameFieldsAndDifferentButIdenticalContentArrayInstances() {
        // Iki ayri byte[] ornegi, ayni icerikle -- FileStoragePort'un iki farkli
        // retrieve() cagrisinin dondurdugu ayri array'leri temsil eder.
        ImagingRecordFileContent a = new ImagingRecordFileContent("xray.png", "image/png", new byte[] {1, 2, 3});
        ImagingRecordFileContent b = new ImagingRecordFileContent("xray.png", "image/png", new byte[] {1, 2, 3});

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void should_notBeEqual_when_contentDiffers() {
        ImagingRecordFileContent a = new ImagingRecordFileContent("xray.png", "image/png", new byte[] {1, 2, 3});
        ImagingRecordFileContent b = new ImagingRecordFileContent("xray.png", "image/png", new byte[] {1, 2, 4});

        assertThat(a).isNotEqualTo(b);
    }
}
