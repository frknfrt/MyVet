package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilMappingKind;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMapping;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilValueMappingNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteTarbilMappingUseCaseTest {

    @Mock private TarbilValueMappingRepository repository;

    @Test
    void should_throwNotFound_when_mappingBelongsToAnotherTenant() {
        UUID id = UUID.randomUUID();
        TarbilValueMapping foreign = TarbilValueMapping.create(
            UUID.randomUUID(), TarbilMappingKind.VACCINE, "kuduz", "{}", UUID.randomUUID(), Instant.now());
        when(repository.findById(id)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> new DeleteTarbilMappingUseCase(repository).execute(UUID.randomUUID(), id))
            .isInstanceOf(TarbilValueMappingNotFoundException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void should_delete_when_mappingBelongsToTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        TarbilValueMapping own = TarbilValueMapping.create(
            tenantId, TarbilMappingKind.VACCINE, "kuduz", "{}", UUID.randomUUID(), Instant.now());
        when(repository.findById(id)).thenReturn(Optional.of(own));

        new DeleteTarbilMappingUseCase(repository).execute(tenantId, id);

        verify(repository).delete(own);
    }
}
