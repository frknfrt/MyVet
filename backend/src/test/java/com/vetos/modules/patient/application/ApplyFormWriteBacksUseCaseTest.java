package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.FormWriteBackCommand;
import com.vetos.modules.patient.application.dto.FormWriteBackCommand.Item;
import com.vetos.modules.patient.application.dto.FormWriteBackResult;
import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import com.vetos.modules.patient.domain.Patient;
import com.vetos.modules.patient.domain.PatientRepository;
import com.vetos.modules.patient.domain.Sex;
import com.vetos.modules.patient.domain.event.PatientIdentificationUpdatedEvent;
import com.vetos.modules.patient.domain.exception.PatientOwnerMismatchException;
import com.vetos.platform.event.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplyFormWriteBacksUseCaseTest {

    @Mock private OwnerRepository ownerRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private DomainEventPublisher eventPublisher;

    private ApplyFormWriteBacksUseCase useCase;
    private UUID tenantId;
    private Owner owner;
    private Patient patient;
    private UUID ownerId;
    private UUID patientId;

    @BeforeEach
    void setUp() {
        useCase = new ApplyFormWriteBacksUseCase(ownerRepository, patientRepository, eventPublisher);
        tenantId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        owner = Owner.register(tenantId, "Hale Yilmaz", "5550000000", "eski@example.com", "eski adres");
        patient = Patient.register(tenantId, ownerId, UUID.randomUUID(), null, "Pamuk", Sex.FEMALE, null);
        org.springframework.test.util.ReflectionTestUtils.setField(patient, "id", patientId);
        when(ownerRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
    }

    private FormWriteBackResult run(Item... items) {
        return useCase.execute(new FormWriteBackCommand(ownerId, patientId, UUID.randomUUID(), List.of(items)));
    }

    @Test
    void should_updateOwnerPhoneAndPatientColor() {
        FormWriteBackResult result = run(new Item("owner.phone", " 5551112233 "), new Item("patient.color", "Sarı"));

        assertThat(result.applied()).containsExactly("owner.phone", "patient.color");
        assertThat(result.skipped()).isEmpty();
        assertThat(owner.getPhone()).isEqualTo("5551112233");
        assertThat(owner.getEmail()).isEqualTo("eski@example.com"); // diger alanlar ezilmez
        assertThat(owner.getAddress()).isEqualTo("eski adres");
        assertThat(patient.getColor()).isEqualTo("Sarı");
        verify(ownerRepository).save(owner);
        verify(patientRepository).save(patient);
    }

    @Test
    void should_applyRemainingBindings() {
        FormWriteBackResult result = run(
            new Item("owner.secondaryPhone", "5552223344"), new Item("owner.nationalId", "10000000146"),
            new Item("owner.birthDate", "1990-05-17"), new Item("owner.address", "yeni adres"),
            new Item("owner.email", "yeni@example.com"), new Item("patient.weightKg", "12.5"),
            new Item("patient.microchipNumber", "900123456789012")
        );

        assertThat(result.skipped()).isEmpty();
        assertThat(result.applied()).hasSize(7);
        assertThat(owner.getSecondaryPhone()).isEqualTo("5552223344");
        assertThat(owner.getNationalId()).isEqualTo("10000000146");
        assertThat(owner.getBirthDate()).isEqualTo(LocalDate.of(1990, 5, 17));
        assertThat(owner.getAddress()).isEqualTo("yeni adres");
        assertThat(owner.getEmail()).isEqualTo("yeni@example.com");
        assertThat(patient.getWeightKg()).isEqualByComparingTo(new BigDecimal("12.5"));
        assertThat(patient.getMicrochipNumber()).isEqualTo("900123456789012");
        verify(eventPublisher).publish(any(PatientIdentificationUpdatedEvent.class));
    }

    @Test
    void should_skipInvalidNationalId() {
        FormWriteBackResult result = run(new Item("owner.nationalId", "12345678901"));

        assertThat(result.applied()).isEmpty();
        assertThat(result.skipped()).extracting(FormWriteBackResult.Skipped::reason)
            .containsExactly("geçersiz TC kimlik no");
        assertThat(owner.getNationalId()).isNull();
        verify(ownerRepository, never()).save(any());
    }

    @Test
    void should_skipNonWritableBinding() {
        FormWriteBackResult result = run(new Item("owner.fullName", "Baska Ad"));

        assertThat(result.applied()).isEmpty();
        assertThat(result.skipped()).extracting(FormWriteBackResult.Skipped::binding, FormWriteBackResult.Skipped::reason)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("owner.fullName", "yazılamaz alan"));
        assertThat(owner.getFullName()).isEqualTo("Hale Yilmaz");
    }

    @Test
    void should_reject_whenPatientNotOwnedByOwner() {
        org.springframework.test.util.ReflectionTestUtils.setField(patient, "ownerId", UUID.randomUUID());

        assertThatThrownBy(() -> useCase.execute(
            new FormWriteBackCommand(ownerId, patientId, null, List.of(new Item("owner.phone", "5551112233")))))
            .isInstanceOf(PatientOwnerMismatchException.class);
        verify(ownerRepository, never()).save(any());
    }

    @Test
    void should_skipBlankValues() {
        FormWriteBackResult result = run(new Item("owner.phone", "  "), new Item("patient.color", null));

        assertThat(result.applied()).isEmpty();
        assertThat(result.skipped()).hasSize(2);
        assertThat(owner.getPhone()).isEqualTo("5550000000");
    }

    @Test
    void should_skipInvalidEmailDateAndWeight() {
        FormWriteBackResult result = run(
            new Item("owner.email", "bozuk"), new Item("owner.birthDate", "17.05.1990"),
            new Item("owner.birthDate", LocalDate.now().plusDays(1).toString()),
            new Item("patient.weightKg", "0"), new Item("patient.weightKg", "abc")
        );

        assertThat(result.applied()).isEmpty();
        assertThat(result.skipped()).hasSize(5);
        assertThat(owner.getEmail()).isEqualTo("eski@example.com");
        assertThat(patient.getWeightKg()).isNull();
    }
}
