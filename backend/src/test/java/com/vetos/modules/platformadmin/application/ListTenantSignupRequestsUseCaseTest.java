package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.TenantSignupRequest;
import com.vetos.modules.platformadmin.domain.TenantSignupRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListTenantSignupRequestsUseCaseTest {

    @Mock private TenantSignupRequestRepository tenantSignupRequestRepository;

    @Test
    void should_returnAllRequests_orderedNewestFirst_asRepositoryProvides() {
        TenantSignupRequest r1 = TenantSignupRequest.create("Klinik A", "Ahmet", "ahmet@x.com", "0500", "PRO");
        TenantSignupRequest r2 = TenantSignupRequest.create("Klinik B", "Beste", "beste@x.com", "0501", "BASIC");
        r2.complete();
        when(tenantSignupRequestRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(r2, r1));

        ListTenantSignupRequestsUseCase useCase = new ListTenantSignupRequestsUseCase(tenantSignupRequestRepository);
        List<TenantSignupRequest> result = useCase.execute();

        assertThat(result).containsExactly(r2, r1);
        assertThat(result.get(0).isCompleted()).isTrue();
        assertThat(result.get(1).isCompleted()).isFalse();
    }
}
