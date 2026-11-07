package com.vetos.modules.consentform.api;

import com.vetos.modules.consentform.application.dto.RenderedConsentPdf;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/** PDF indirme yaniti: ASCII-guvenli filename + UTF-8 filename* (Spring ContentDisposition). */
final class ConsentPdfResponses {

    private ConsentPdfResponses() {}

    static ResponseEntity<byte[]> attachment(RenderedConsentPdf pdf) {
        String base = pdf.title() + "-" + pdf.date();
        String ascii = asciiSafe(base) + ".pdf";
        String utf8 = ContentDisposition.attachment().filename(base + ".pdf", StandardCharsets.UTF_8).build().toString();
        // utf8 = attachment; filename*=UTF-8''... ; ASCII yedegi basa eklenir
        String header = "attachment; filename=\"" + ascii + "\"; " + utf8.substring(utf8.indexOf("filename*"));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, header)
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf.content());
    }

    static String asciiSafe(String s) {
        String t = s.replace('ı', 'i').replace('İ', 'I').replace('ğ', 'g').replace('Ğ', 'G')
            .replace('ş', 's').replace('Ş', 'S').replace('ç', 'c').replace('Ç', 'C')
            .replace('ö', 'o').replace('Ö', 'O').replace('ü', 'u').replace('Ü', 'U');
        t = t.replaceAll("\\s+", "-").replaceAll("[^A-Za-z0-9._-]", "");
        return t.isEmpty() ? "form" : t;
    }
}
