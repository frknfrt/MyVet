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
    void should_returnExistingId_when_placeholderAlreadyExists() throws Exception {
        useCase = new GetOrCreateAnonymousOwnerUseCase(ownerRepository);
        UUID tenantId = UUID.randomUUID();
        Owner existing = Owner.createAnonymousPlaceholder(tenantId);
        UUID existingId = UUID.randomUUID();
        java.lang.reflect.Field idField = Owner.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(existing, existingId);
        when(ownerRepository.findAnonymousPlaceholder(tenantId)).thenReturn(Optional.of(existing));

        UUID result = useCase.execute(tenantId);

        assertThat(result).isEqualTo(existingId);
        verify(ownerRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void should_createAndReturnNewId_when_placeholderMissing() throws Exception {
        useCase = new GetOrCreateAnonymousOwnerUseCase(ownerRepository);
        UUID tenantId = UUID.randomUUID();
        when(ownerRepository.findAnonymousPlaceholder(tenantId)).thenReturn(Optional.empty());
        when(ownerRepository.save(org.mockito.ArgumentMatchers.any(Owner.class)))
            .thenAnswer(invocation -> {
                Owner owner = invocation.getArgument(0);
                UUID generatedId = UUID.randomUUID();
                java.lang.reflect.Field idField = Owner.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(owner, generatedId);
                return owner;
            });

        UUID result = useCase.execute(tenantId);

        assertThat(result).isNotNull();
        verify(ownerRepository).save(org.mockito.ArgumentMatchers.any(Owner.class));
    }
}
