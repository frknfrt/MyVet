package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

import java.util.UUID;

public class TenantSignupRequestNotFoundException extends DomainException {
    public TenantSignupRequestNotFoundException(UUID id) {
        super("TENANT_SIGNUP_REQUEST_NOT_FOUND", "Kayit talebi bulunamadi: " + id);
    }
}
