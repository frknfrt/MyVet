package com.vetos.modules.platformadmin.application;

import com.vetos.modules.integration.efatura.domain.EInvoiceHealthPort;
import com.vetos.modules.integration.tarbil.domain.TarbilHealthPort;
import com.vetos.modules.platformadmin.application.dto.TenantIntegrationHealth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTenantIntegrationHealthUseCaseTest {

    @Mock private EInvoiceHealthPort eInvoiceHealthPort;
    @Mock private TarbilHealthPort tarbilHealthPort;

    @Test
    void should_combineCountsFromBothPorts() {
        GetTenantIntegrationHealthUseCase useCase = new GetTenantIntegrationHealthUseCase(eInvoiceHealthPort, tarbilHealthPort);
        UUID tenantId = UUID.randomUUID();
        when(eInvoiceHealthPort.countFailedForTenant(tenantId)).thenReturn(3L);
        when(tarbilHealthPort.countFailedForTenant(tenantId)).thenReturn(2L);

        TenantIntegrationHealth result = useCase.execute(tenantId);

        assertThat(result.failedEInvoiceCount()).isEqualTo(3L);
        assertThat(result.failedTarbilSyncCount()).isEqualTo(2L);
    }

    @Test
    void should_returnZeroes_when_noFailures() {
        GetTenantIntegrationHealthUseCase useCase = new GetTenantIntegrationHealthUseCase(eInvoiceHealthPort, tarbilHealthPort);
        UUID tenantId = UUID.randomUUID();
        when(eInvoiceHealthPort.countFailedForTenant(tenantId)).thenReturn(0L);
        when(tarbilHealthPort.countFailedForTenant(tenantId)).thenReturn(0L);

        TenantIntegrationHealth result = useCase.execute(tenantId);

        assertThat(result.failedEInvoiceCount()).isZero();
        assertThat(result.failedTarbilSyncCount()).isZero();
    }
}
