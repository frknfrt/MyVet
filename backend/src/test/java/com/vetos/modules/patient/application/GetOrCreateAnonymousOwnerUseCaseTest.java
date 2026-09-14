package com.vetos.modules.patient.application;

import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOrCreateAnonymousOwnerUseCaseTest {

    @Mock private OwnerRepository ownerRepository;

    private GetOrCreateAnonymousOwnerUseCase useCase;

    @Test
    void should_returnExistingId_when_placeholderAlreadyExists() {
        useCase = new GetOrCreateAnonymousOwnerUseCase(ownerRepository);
        UUID tenantId = UUID.randomUUID();
        Owner existing = Owner.createAnonymousPlaceholder(tenantId);
        when(ownerRepository.findAnonymousPlaceholder(tenantId)).thenReturn(Optional.of(existing));

        UUID result = useCase.execute(tenantId);

        assertThat(result).isEqualTo(existing.getId());
        verify(ownerRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void should_createAndReturnNewId_when_placeholderMissing() {
        useCase = new GetOrCreateAnonymousOwnerUseCase(ownerRepository);
        UUID tenantId = UUID.randomUUID();
        when(ownerRepository.findAnonymousPlaceholder(tenantId)).thenReturn(Optional.empty());
        when(ownerRepository.save(org.mockito.ArgumentMatchers.any(Owner.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        UUID result = useCase.execute(tenantId);

        assertThat(result).isNotNull();
        verify(ownerRepository).save(org.mockito.ArgumentMatchers.any(Owner.class));
    }
}
