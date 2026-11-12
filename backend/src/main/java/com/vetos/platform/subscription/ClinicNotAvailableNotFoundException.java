package com.vetos.platform.subscription;

import com.vetos.platform.exception.DomainException;

/** Deneme suresi dolmus klinigin herkese acik uclari (site, online randevu) kapali. */
public class ClinicNotAvailableNotFoundException extends DomainException {
    public ClinicNotAvailableNotFoundException() {
        super("CLINIC_NOT_AVAILABLE", "Bu klinik şu anda çevrimiçi hizmet vermiyor.");
    }
}
