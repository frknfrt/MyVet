package com.vetos.modules.tenant.api.dto;

import com.vetos.modules.tenant.application.dto.StaffUserOverview;
import com.vetos.modules.tenant.domain.StaffRole;

import java.util.UUID;

/**
 * StaffUserResponse'un aksine PII tasimaz (e-posta/telefon/lisans/uzmanlik/bio
 * yok) -- randevu atama gibi tum rollerin erisebildigi cross-module secim
 * listeleri icin. Tam detay (StaffUserResponse) sadece ADMIN'e acik.
 */
public record StaffDirectoryResponse(UUID id, String fullName, StaffRole role, boolean active) {
    public static StaffDirectoryResponse from(StaffUserOverview overview) {
        return new StaffDirectoryResponse(overview.id(), overview.fullName(), overview.role(), overview.active());
    }
}
