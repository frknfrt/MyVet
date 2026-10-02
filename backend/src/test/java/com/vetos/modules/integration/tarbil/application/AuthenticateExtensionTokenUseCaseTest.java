package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.ExtensionIdentity;
import com.vetos.modules.integration.tarbil.domain.ExtensionSecrets;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffSummary;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticateExtensionTokenUseCaseTest {

    @Mock private TarbilExtensionTokenRepository repository;
    @Mock private StaffUserLookupPort staffUserLookupPort;
    @Mock private TenantLookupPort tenantLookupPort;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();

    private TarbilExtensionToken paired(String raw) {
        TarbilExtensionToken t = TarbilExtensionToken.issuePairing(
            tenantId, staffId, "c", Instant.now().plus(Duration.ofMinutes(10)), Instant.now());
        t.pair(ExtensionSecrets.sha256Hex(raw), "x", Instant.now());
        return t;
    }

    private AuthenticateExtensionTokenUseCase useCase() {
        return new AuthenticateExtensionTokenUseCase(repository, staffUserLookupPort, tenantLookupPort);
    }

    private void staffIs(StaffRole role, boolean tenantOperational) {
        when(staffUserLookupPort.isActive(staffId)).thenReturn(true);
        org.mockito.Mockito.lenient().when(staffUserLookupPort.findSummaryById(staffId))
            .thenReturn(new StaffSummary(staffId, "Dr. X", role, null));
        org.mockito.Mockito.lenient().when(tenantLookupPort.isOperational(tenantId)).thenReturn(tenantOperational);
    }

    @Test
    void should_returnIdentity_when_tokenValidAndStaffActive() {
        TarbilExtensionToken token = paired("vtx_abc");
        when(repository.findByTokenHash(ExtensionSecrets.sha256Hex("vtx_abc"))).thenReturn(Optional.of(token));
        staffIs(StaffRole.VET, true);

        Optional<ExtensionIdentity> identity = useCase().execute("vtx_abc");

        assertThat(identity).isPresent();
        assertThat(identity.get().tenantId()).isEqualTo(tenantId);
        assertThat(identity.get().staffUserId()).isEqualTo(staffId);
    }

    @Test
    void should_reject_when_tokenRevoked() {
        TarbilExtensionToken token = paired("vtx_abc");
        token.revoke(Instant.now());
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThat(useCase().execute("vtx_abc")).isEmpty();
    }

    @Test
    void should_reject_when_staffInactive() {
        TarbilExtensionToken token = paired("vtx_abc");
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(staffUserLookupPort.isActive(staffId)).thenReturn(false);

        assertThat(useCase().execute("vtx_abc")).isEmpty();
    }

    @Test
    void should_reject_when_tenantSuspended() {
        TarbilExtensionToken token = paired("vtx_abc");
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        staffIs(StaffRole.VET, false);

        assertThat(useCase().execute("vtx_abc")).isEmpty();
    }

    @Test
    void should_reject_when_staffNoLongerVetOrAdmin() {
        TarbilExtensionToken token = paired("vtx_abc");
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        staffIs(StaffRole.RECEPTIONIST, true);

        assertThat(useCase().execute("vtx_abc")).isEmpty();
    }

    @Test
    void should_reject_when_tokenUnknownOrMalformed() {
        assertThat(useCase().execute("not-a-token")).isEmpty();
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThat(useCase().execute("vtx_unknown")).isEmpty();
    }
}
