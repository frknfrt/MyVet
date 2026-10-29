package com.vetos.modules.ai.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29). Suffix "Exception" -> GlobalExceptionHandler 422 doner. */
public class NoFileSelectedForOcrException extends DomainException {
    public NoFileSelectedForOcrException() {
        super("NO_FILE_SELECTED_FOR_OCR", "Fotograftan okuma icin en az bir dosya secilmelidir.");
    }
}
