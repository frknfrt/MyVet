package com.vetos.modules.ai.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

/** Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29). Suffix "Exception" -> GlobalExceptionHandler 422 doner. */
public class UnsupportedOcrFileTypeException extends DomainException {
    public UnsupportedOcrFileTypeException(UUID fileId) {
        super("UNSUPPORTED_OCR_FILE_TYPE", "Sadece gorsel (image/*) dosyalar fotograftan okunabilir: " + fileId);
    }
}
