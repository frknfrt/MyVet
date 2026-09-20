package com.vetos.platform.storage;

import com.vetos.platform.exception.DomainException;

public class StoredFileNotFoundException extends DomainException {
    public StoredFileNotFoundException(String storageRef) {
        super("STORED_FILE_NOT_FOUND", "Depolanan dosya bulunamadi: " + storageRef);
    }
}
