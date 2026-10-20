package com.vetos.platform.feature;

import com.vetos.platform.exception.DomainException;

/** Sinif adi "ForbiddenException" ile bittigi icin GlobalExceptionHandler 403 dondurur. */
public class FeatureNotInPlanForbiddenException extends DomainException {

    public FeatureNotInPlanForbiddenException() {
        super("FEATURE_NOT_IN_PLAN", "Bu ozellik planinizda yok");
    }
}
