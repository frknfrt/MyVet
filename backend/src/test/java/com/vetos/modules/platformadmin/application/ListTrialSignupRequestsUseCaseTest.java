package com.vetos.modules.platformadmin.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vetos.modules.platformadmin.api.dto.TrialSignupRequestResponse;
import com.vetos.modules.platformadmin.domain.TrialSignupRequest;
import com.vetos.modules.platformadmin.domain.TrialSignupRequestRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.vetos.modules.platformadmin.application.TrialTestSupport.NOW;
import static com.vetos.modules.platformadmin.application.TrialTestSupport.pendingRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListTrialSignupRequestsUseCaseTest {

    private final TrialSignupRequestRepository repository = mock(TrialSignupRequestRepository.class);
    private final ListTrialSignupRequestsUseCase useCase = new ListTrialSignupRequestsUseCase(repository);

    @Test
    void should_listNewestFirst_when_trialRequestsExist() {
        TrialSignupRequest newer = pendingRequest(UUID.randomUUID());
        TrialSignupRequest older = pendingRequest(UUID.randomUUID());
        when(repository.findAllNewestFirst()).thenReturn(List.of(newer, older));

        assertThat(useCase.execute()).containsExactly(newer, older);
    }

    @Test
    void should_exposeContactAndChamberNumber_when_mappedToResponse() {
        UUID tenantId = UUID.randomUUID();
        TrialSignupRequest request = pendingRequest(UUID.randomUUID());
        request.complete(tenantId, NOW.plusSeconds(40));

        TrialSignupRequestResponse response = TrialSignupRequestResponse.from(request);

        assertThat(response.id()).isEqualTo(request.getId());
        assertThat(response.fullName()).isEqualTo("Ayşe Yılmaz");
        assertThat(response.email()).isEqualTo("ayse@ornek.com");
        assertThat(response.phone()).isEqualTo("+905321112233");
        assertThat(response.clinicName()).isEqualTo("Pati Klinik");
        assertThat(response.city()).isEqualTo("İzmir");
        assertThat(response.chamberRegistrationNo()).isEqualTo("35-1234");
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.completedAt()).isEqualTo(NOW.plusSeconds(40));
    }

    @Test
    void should_notExposeHashes_when_mappedToResponse() throws Exception {
        TrialSignupRequest request = pendingRequest(UUID.randomUUID());

        String json = new ObjectMapper().registerModule(new JavaTimeModule())
            .writeValueAsString(TrialSignupRequestResponse.from(request));

        assertThat(json).doesNotContain("hash", "Hash", "otp", "password", "consentIp");
    }
}
