package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.StaffUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffUserLookupAdapterTest {

    @Mock private StaffUserJpaRepository jpaRepository;

    @Test
    void should_returnActiveFlag_when_staffExists() {
        UUID id = UUID.randomUUID();
        StaffUser staffUser = mock(StaffUser.class);
        when(staffUser.isActive()).thenReturn(false);
        when(jpaRepository.findById(id)).thenReturn(Optional.of(staffUser));

        assertThat(new StaffUserLookupAdapter(jpaRepository).isActive(id)).isFalse();
    }

    @Test
    void should_returnFalse_when_staffMissing() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(new StaffUserLookupAdapter(jpaRepository).isActive(id)).isFalse();
    }
}
