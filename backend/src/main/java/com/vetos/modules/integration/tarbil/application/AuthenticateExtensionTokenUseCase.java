package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.ExtensionIdentity;
import com.vetos.modules.integration.tarbil.domain.ExtensionSecrets;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthenticateExtensionTokenUseCase {

    private static final Set<StaffRole> ALLOWED_ROLES = EnumSet.of(StaffRole.VET, StaffRole.ADMIN);

    private final TarbilExtensionTokenRepository repository;
    private final StaffUserLookupPort staffUserLookupPort;
    private final TenantLookupPort tenantLookupPort;

    @Transactional
    public Optional<ExtensionIdentity> execute(String rawToken) {
        if (rawToken == null || !rawToken.startsWith("vtx_")) {
            return Optional.empty();
        }
        Optional<TarbilExtensionToken> found = repository.findByTokenHash(ExtensionSecrets.sha256Hex(rawToken))
            .filter(TarbilExtensionToken::isUsable)
            .filter(t -> staffUserLookupPort.isActive(t.getStaffUserId()))
            // Anahtar suresiz: rol degisikligi ve kiraci askiya alma her istekte yeniden denetlenir.
            .filter(t -> ALLOWED_ROLES.contains(staffUserLookupPort.findSummaryById(t.getStaffUserId()).role()))
            .filter(t -> tenantLookupPort.isOperational(t.getTenantId()));
        found.ifPresent(t -> {
            t.touch(Instant.now());
            repository.save(t);
        });
        return found.map(t -> new ExtensionIdentity(t.getId(), t.getTenantId(), t.getStaffUserId()));
    }
}
