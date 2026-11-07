package com.vetos.modules.consentform.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormImageFormatTest {

    @Test
    void should_detectJpegPngWebp() {
        assertThat(FormImageFormat.detectContentType(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).contains("image/jpeg");
        assertThat(FormImageFormat.detectContentType(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0})).contains("image/png");
        assertThat(FormImageFormat.detectContentType("RIFF....WEBPVP8 ".getBytes())).contains("image/webp");
    }

    @Test
    void should_rejectPdfEmptyAndNull() {
        assertThat(FormImageFormat.detectContentType("%PDF-1.4 x".getBytes())).isEmpty();
        assertThat(FormImageFormat.detectContentType(new byte[0])).isEmpty();
        assertThat(FormImageFormat.detectContentType(null)).isEmpty();
    }
}
