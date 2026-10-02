package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import com.vetos.platform.exception.DomainException;

public class TarbilSubmissionStateConflictException extends DomainException {
    public TarbilSubmissionStateConflictException(TarbilSyncStatus current, String action) {
        super("TARBIL_SUBMISSION_STATE_CONFLICT", "TARBIL kaydi '" + current + "' durumundayken " + action + " yapilamaz");
    }
}
