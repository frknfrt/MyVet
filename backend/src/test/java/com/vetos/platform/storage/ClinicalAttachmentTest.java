package com.vetos.platform.storage;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ClinicalAttachmentTest {
    @Test
    void pdfIsDownloadOnlyAndMismatchedTypeIsRejected() {
        byte[] content = "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertThat(ClinicalAttachment.validate("application/pdf", content)).isEqualTo("application/pdf");
        assertThatThrownBy(() -> ClinicalAttachment.validate("image/png", content))
            .isInstanceOf(ClinicalAttachment.InvalidAttachmentException.class);
        assertThat(ClinicalAttachment.download("test.pdf", content).getHeaders().getContentDisposition().isAttachment()).isTrue();
    }

    @Test
    void unknownLegacyAttachmentIsOnlyServedAsBinaryDownload() {
        var headers = ClinicalAttachment.download("old", new byte[]{1, 2, 3}).getHeaders();
        assertThat(headers.getContentType()).isEqualTo(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
        assertThat(headers.getContentDisposition().isAttachment()).isTrue();
    }
}
