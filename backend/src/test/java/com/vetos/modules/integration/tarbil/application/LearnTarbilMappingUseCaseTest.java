package com.vetos.modules.integration.tarbil.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.integration.tarbil.domain.TarbilMappingKind;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMapping;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import com.vetos.modules.integration.tarbil.domain.exception.InvalidTarbilMappingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearnTarbilMappingUseCaseTest {

    @Mock private TarbilValueMappingRepository repository;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();

    private LearnTarbilMappingUseCase useCase() {
        return new LearnTarbilMappingUseCase(repository, new ObjectMapper());
    }

    @Test
    void should_createWithNormalizedKey_when_vaccineMappingNew() {
        when(repository.findByTenantIdAndKindAndVetlyKey(tenantId, TarbilMappingKind.VACCINE, "kuduz aşisi"))
            .thenReturn(Optional.empty());

        useCase().execute(tenantId, staffId, TarbilMappingKind.VACCINE, " Kuduz  Aşısı", "{\"vaccine\":{\"value\":\"g1\",\"text\":\"Rabisin\"}}");

        ArgumentCaptor<TarbilValueMapping> captor = ArgumentCaptor.forClass(TarbilValueMapping.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getVetlyKey()).isEqualTo("kuduz aşisi");
        assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(captor.getValue().getLearnedByStaffId()).isEqualTo(staffId);
    }

    @Test
    void should_overwriteFields_when_mappingExists() {
        TarbilValueMapping existing = TarbilValueMapping.create(
            tenantId, TarbilMappingKind.VACCINE, "kuduz aşisi", "{\"old\":1}", UUID.randomUUID(), Instant.now());
        when(repository.findByTenantIdAndKindAndVetlyKey(tenantId, TarbilMappingKind.VACCINE, "kuduz aşisi"))
            .thenReturn(Optional.of(existing));

        useCase().execute(tenantId, staffId, TarbilMappingKind.VACCINE, "Kuduz Aşısı", "{\"new\":2}");

        assertThat(existing.getTarbilFields()).isEqualTo("{\"new\":2}");
        assertThat(existing.getLearnedByStaffId()).isEqualTo(staffId);
        verify(repository).save(existing);
    }

    @Test
    void should_reject_when_fieldsNotJsonObject() {
        assertThatThrownBy(() -> useCase().execute(tenantId, staffId, TarbilMappingKind.VACCINE, "Kuduz", "[1,2]"))
            .isInstanceOf(InvalidTarbilMappingException.class);
        assertThatThrownBy(() -> useCase().execute(tenantId, staffId, TarbilMappingKind.VACCINE, "Kuduz", "not json"))
            .isInstanceOf(InvalidTarbilMappingException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void should_reject_when_fieldsTooLarge() {
        String big = "{\"x\":\"" + "a".repeat(5000) + "\"}";
        assertThatThrownBy(() -> useCase().execute(tenantId, staffId, TarbilMappingKind.VACCINE, "Kuduz", big))
            .isInstanceOf(InvalidTarbilMappingException.class);
    }

    @Test
    void should_reject_when_keyBlank() {
        assertThatThrownBy(() -> useCase().execute(tenantId, staffId, TarbilMappingKind.VACCINE, "   ", "{}"))
            .isInstanceOf(InvalidTarbilMappingException.class);
    }

    @Test
    void should_storeTrimmedKey_when_diseaseMappingLearned() {
        when(repository.findByTenantIdAndKindAndVetlyKey(tenantId, TarbilMappingKind.DISEASE, "iç parazit"))
            .thenReturn(Optional.empty());

        useCase().execute(tenantId, staffId, TarbilMappingKind.DISEASE, "  iç parazit ", "{\"diseaseId\":\"95860589-0057-42cc-8209-663e1e459594\"}");

        ArgumentCaptor<TarbilValueMapping> captor = ArgumentCaptor.forClass(TarbilValueMapping.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo(TarbilMappingKind.DISEASE);
        assertThat(captor.getValue().getVetlyKey()).isEqualTo("iç parazit");
    }
}
