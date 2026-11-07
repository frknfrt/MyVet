package com.vetos.platform.storage;

import com.vetos.platform.exception.DomainException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

public final class ClinicalAttachment {
    private ClinicalAttachment() {}

    private static final byte[] PNG = HexFormat.of().parseHex("89504e470d0a1a0a");
    private static final byte[] JPEG = HexFormat.of().parseHex("ffd8ff");

    private static boolean starts(byte[] data, int offset, byte[] prefix) {
        return data.length >= offset + prefix.length && Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

    private static boolean signature(byte[] data, int offset, String text) {
        return starts(data, offset, text.getBytes(StandardCharsets.US_ASCII));
    }

    private static String detect(byte[] data) {
        if (starts(data, 0, PNG)) return "image/png";
        if (starts(data, 0, JPEG)) return "image/jpeg";
        if (signature(data, 0, "RIFF") && signature(data, 8, "WEBP")) return "image/webp";
        if (signature(data, 0, "GIF87a") || signature(data, 0, "GIF89a")) return "image/gif";
        if (signature(data, 0, "%PDF-")) return "application/pdf";
        if (signature(data, 128, "DICM")) return "application/dicom";
        return null;
    }

    public static String validate(String declared, byte[] data) {
        String type = detect(data);
        if (type == null || (declared != null && !declared.isBlank()
            && !declared.equalsIgnoreCase("application/octet-stream") && !type.equalsIgnoreCase(declared))) {
            throw new InvalidAttachmentException();
        }
        return type;
    }

    public static ResponseEntity<ByteArrayResource> download(String name, byte[] data) {
        String type = detect(data);
        boolean image = type != null && type.startsWith("image/");
        var disposition = image ? ContentDisposition.inline() : ContentDisposition.attachment();
        return ResponseEntity.ok().contentType(type == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(type))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.filename(name == null ? "dosya" : name, StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "sandbox; default-src 'none'")
            .body(new ByteArrayResource(data));
    }

    static class InvalidAttachmentException extends DomainException {
        InvalidAttachmentException() {
            super("INVALID_ATTACHMENT", "Dosya türü desteklenmiyor veya içerikle uyuşmuyor");
        }
    }
}
