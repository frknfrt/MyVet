package com.vetos.modules.imaging.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class ImagingRecordNotFoundException extends DomainException {
    public ImagingRecordNotFoundException(UUID id) {
        super("IMAGING_RECORD_NOT_FOUND", "Goruntuleme kaydi bulunamadi: " + id);
    }
}
