package com.vetos.modules.ai.domain;

import org.junit.jupiter.api.Test;

import static com.vetos.modules.ai.domain.OcrImageFormatDetector.Format.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma -- REVIZE (2026-10-31).
 * Gercek kullanici bulgusu: declare edilen Content-Type'a guvenmek yerine GERCEK bayt
 * formatina gore AI saglayicisina (Claude/Ollama) gonderilip gonderilmeyecegine karar
 * verilmeli (bkz. OcrImageFormatDetector javadoc'u).
 */
class OcrImageFormatDetectorTest {

    @Test
    void detectsJpegFromMagicBytes() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
        assertThat(OcrImageFormatDetector.detect(jpeg)).isEqualTo(JPEG);
        assertThat(OcrImageFormatDetector.isSupportedByVisionApis(JPEG)).isTrue();
        assertThat(OcrImageFormatDetector.mediaTypeFor(JPEG)).isEqualTo("image/jpeg");
    }

    @Test
    void detectsPngFromMagicBytes() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertThat(OcrImageFormatDetector.detect(png)).isEqualTo(PNG);
        assertThat(OcrImageFormatDetector.isSupportedByVisionApis(PNG)).isTrue();
        assertThat(OcrImageFormatDetector.mediaTypeFor(PNG)).isEqualTo("image/png");
    }

    @Test
    void detectsGifFromMagicBytes() {
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0};
        assertThat(OcrImageFormatDetector.detect(gif)).isEqualTo(GIF);
        assertThat(OcrImageFormatDetector.isSupportedByVisionApis(GIF)).isTrue();
    }

    @Test
    void detectsWebpFromMagicBytes() {
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        assertThat(OcrImageFormatDetector.detect(webp)).isEqualTo(WEBP);
        assertThat(OcrImageFormatDetector.isSupportedByVisionApis(WEBP)).isTrue();
    }

    @Test
    void detectsHeicFromIsoBmffFtypBox_notSupportedByVisionApis() {
        // iPhone kamera fotograflarinin varsayilan formati -- Claude/Ollama islemez.
        byte[] heic = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0};
        assertThat(OcrImageFormatDetector.detect(heic)).isEqualTo(HEIC);
        assertThat(OcrImageFormatDetector.isSupportedByVisionApis(HEIC)).isFalse();
    }

    @Test
    void unknownOrTooShortContent_isUnknownAndUnsupported() {
        assertThat(OcrImageFormatDetector.detect("not-an-image".getBytes())).isEqualTo(UNKNOWN);
        assertThat(OcrImageFormatDetector.detect(new byte[]{1, 2, 3})).isEqualTo(UNKNOWN);
        assertThat(OcrImageFormatDetector.detect(null)).isEqualTo(UNKNOWN);
        assertThat(OcrImageFormatDetector.isSupportedByVisionApis(UNKNOWN)).isFalse();
    }

    @Test
    void mediaTypeFor_throwsForUnsupportedFormats() {
        assertThatThrownBy(() -> OcrImageFormatDetector.mediaTypeFor(HEIC)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OcrImageFormatDetector.mediaTypeFor(UNKNOWN)).isInstanceOf(IllegalArgumentException.class);
    }
}
