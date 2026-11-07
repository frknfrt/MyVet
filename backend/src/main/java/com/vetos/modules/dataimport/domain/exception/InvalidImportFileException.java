package com.vetos.modules.dataimport.domain.exception;

/** Yuklenen dosya gecerli bir .xlsx degil ya da okunamadi. */
public class InvalidImportFileException extends RuntimeException {

    public InvalidImportFileException(String message) {
        super(message);
    }

    public InvalidImportFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
