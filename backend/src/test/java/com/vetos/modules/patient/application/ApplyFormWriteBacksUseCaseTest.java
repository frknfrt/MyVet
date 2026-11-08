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

    @Mock private com.vetos.modules.patient.domain.SignedFormWriteBackLookupPort signedFormLookup;
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
        useCase = new ApplyFormWriteBacksUseCase(signedFormLookup, ownerRepository, patientRepository, eventPublisher);
        tenantId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        owner = Owner.register(tenantId, "Hale Yilmaz", "5550000000", "eski@example.com", "eski adres");
        patient = Patient.register(tenantId, ownerId, UUID.randomUUID(), null, "Pamuk", Sex.FEMALE, null);
        org.springframework.test.util.ReflectionTestUtils.setField(patient, "id", patientId);
        org.mockito.Mockito.lenient().when(ownerRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        org.mockito.Mockito.lenient().when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
    }

    private FormWriteBackResult run(Item... items) {
        UUID formId = UUID.randomUUID();
        java.util.Map<String, java.util.Set<String>> allowed = new java.util.HashMap<>();
        for (Item item : items) {
            if (item.value() != null) {
                allowed.computeIfAbsent(item.binding(), key -> new java.util.HashSet<>()).add(item.value().trim());
            }
        }
        when(signedFormLookup.allowedValues(formId, patientId, ownerId)).thenReturn(allowed);
        return useCase.execute(new FormWriteBackCommand(ownerId, patientId, formId, List.of(items)));
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
            new FormWriteBackCommand(ownerId, patientId, UUID.randomUUID(), List.of(new Item("owner.phone", "5551112233")))))
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

    @Test
    void missingSignedFormCannotUpdateAnything() {
        assertThatThrownBy(() -> useCase.execute(new FormWriteBackCommand(ownerId, patientId, null,
            List.of(new Item("owner.phone", "5551112233")))))
            .isInstanceOf(com.vetos.modules.patient.domain.SignedFormWriteBackLookupPort.InvalidSourceException.class);
        org.mockito.Mockito.verifyNoInteractions(signedFormLookup, eventPublisher);
        verify(ownerRepository, never()).save(any());
        verify(patientRepository, never()).save(any());
    }

    @Test
    void onlySignedAndAllowedValuesCanBeWritten() {
        UUID formId = UUID.randomUUID();
        when(signedFormLookup.allowedValues(formId, patientId, ownerId))
            .thenReturn(java.util.Map.of("patient.color", java.util.Set.of("Sarı"), "owner.phone", java.util.Set.of("signed-phone")));
        var result = useCase.execute(new FormWriteBackCommand(ownerId, patientId, formId, List.of(
            new Item("owner.phone", "different-phone"), new Item("owner.email", "unsigned@example.com"),
            new Item("patient.color", "Sarı"))));
        assertThat(result.applied()).containsExactly("patient.color");
        assertThat(result.skipped()).hasSize(2);
        assertThat(owner.getPhone()).isEqualTo("5550000000");
        assertThat(owner.getEmail()).isEqualTo("eski@example.com");
        verify(ownerRepository, never()).save(any());
        verify(patientRepository).save(patient);
    }

    @Test
    void invalidSourceCannotSaveOrPublish() {
        UUID formId = UUID.randomUUID();
        when(signedFormLookup.allowedValues(formId, patientId, ownerId))
            .thenThrow(new com.vetos.modules.patient.domain.SignedFormWriteBackLookupPort.InvalidSourceException());
        assertThatThrownBy(() -> useCase.execute(new FormWriteBackCommand(ownerId, patientId, formId,
            List.of(new Item("patient.microchipNumber", "900123456789012")))))
            .isInstanceOf(com.vetos.modules.patient.domain.SignedFormWriteBackLookupPort.InvalidSourceException.class);
        verify(ownerRepository, never()).save(any());
        verify(patientRepository, never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(eventPublisher);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "10000", "9999.995", "999999999999999999999", "1e1000000", "1e-1000000", "0.001"
    })
    void unrepresentableWeightIsSkippedWhileOtherFieldsAreApplied(String weight) {
        patient.recordWeight(new BigDecimal("12.50"));
        var result = run(new Item("owner.phone", "5551112233"), new Item("patient.weightKg", weight),
            new Item("patient.color", "Sarı"));
        assertThat(result.applied()).containsExactly("owner.phone", "patient.color");
        assertThat(result.skipped()).extracting(FormWriteBackResult.Skipped::binding).containsExactly("patient.weightKg");
        assertThat(patient.getWeightKg()).isEqualByComparingTo("12.50");
        assertThat(patient.getColor()).isEqualTo("Sarı");
        assertThat(owner.getPhone()).isEqualTo("5551112233");
        verify(ownerRepository).save(owner);
        verify(patientRepository).save(patient);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(value = {
        "9999.99;9999.99", "9999,99;9999.99", "4.555;4.56", "4.5500;4.55", "0.005;0.01"
    }, delimiter = ';')
    void weightIsRoundedToDatabasePrecision(String value, String expected) {
        var result = run(new Item("patient.weightKg", value));
        assertThat(result.skipped()).isEmpty();
        assertThat(patient.getWeightKg()).isEqualByComparingTo(expected);
        verify(patientRepository).save(patient);
    }

    @Test
    void repeatedBindingIsAppliedOnce() {
        var result = run(new Item("owner.phone", "5551112233"), new Item("owner.phone", "5551112233"));
        assertThat(result.applied()).containsExactly("owner.phone");
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().get(0).reason()).isEqualTo("tekrarlanan alan");
        verify(ownerRepository).save(owner);
    }

    @Test
    void conflictingRepeatedBindingCannotOverwriteFirstAcceptedValue() {
        var result = run(new Item("patient.color", "Sarı"), new Item("patient.color", "Siyah"));
        assertThat(patient.getColor()).isEqualTo("Sarı");
        assertThat(result.applied()).containsExactly("patient.color");
        assertThat(result.skipped()).hasSize(1);
        verify(patientRepository).save(patient);
    }

    @Test
    void invalidFirstValueDoesNotBlockLaterValidValue() {
        var result = run(new Item("owner.email", "bozuk"), new Item("owner.email", "yeni@example.com"));
        assertThat(owner.getEmail()).isEqualTo("yeni@example.com");
        assertThat(result.applied()).containsExactly("owner.email");
        assertThat(result.skipped()).hasSize(1);
    }

    @Test
    void missingOwnerIsNotFoundBeforeAnyWrite() {
        when(ownerRepository.findById(ownerId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(new FormWriteBackCommand(ownerId, patientId, UUID.randomUUID(), List.of())))
            .isInstanceOf(com.vetos.modules.patient.domain.exception.OwnerNotFoundException.class);
        org.mockito.Mockito.verifyNoInteractions(signedFormLookup, eventPublisher);
        verify(ownerRepository, never()).save(any());
        verify(patientRepository, never()).save(any());
    }

    @Test
    void missingPatientIsNotFoundBeforeAnyWrite() {
        when(patientRepository.findById(patientId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(new FormWriteBackCommand(ownerId, patientId, UUID.randomUUID(), List.of())))
            .isInstanceOf(com.vetos.modules.patient.domain.exception.PatientNotFoundException.class);
        org.mockito.Mockito.verifyNoInteractions(signedFormLookup, eventPublisher);
        verify(ownerRepository, never()).save(any());
        verify(patientRepository, never()).save(any());
    }
}
