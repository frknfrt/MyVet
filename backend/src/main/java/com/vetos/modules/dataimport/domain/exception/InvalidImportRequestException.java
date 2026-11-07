package com.vetos.modules.dataimport.domain.exception;

/** Gecersiz aktarim istegi (ornegin bos kaynak sistem adi): API'de 400. */
public class InvalidImportRequestException extends IllegalArgumentException {
    public InvalidImportRequestException(String message) {
        super(message);
    }
}
