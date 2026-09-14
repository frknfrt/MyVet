package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.UpdateOwnerCommand;
import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import com.vetos.modules.patient.domain.exception.InvalidNationalIdException;
import com.vetos.modules.patient.domain.exception.OwnerNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * fullName daha once hicbir yerden guncellenemiyordu -- personel kayit
 * sirasinda soyadsiz/hatali girdiyse duzeltecek bir yol yoktu (bkz.
 * e-Fatura "hale" -> soyad eksik bug'i). Bu test o duzeltmenin gercekten
 * calistigini dogrular.
 */
@ExtendWith(MockitoExtension.class)
class UpdateOwnerUseCaseTest {

    @Mock private OwnerRepository ownerRepository;

    private UpdateOwnerUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateOwnerUseCase(ownerRepository);
    }

    private UpdateOwnerCommand aCommand(UUID ownerId, String fullName) {
        return aCommand(ownerId, fullName, null);
    }

    private UpdateOwnerCommand aCommand(UUID ownerId, String fullName, String nationalId) {
        return new UpdateOwnerCommand(
            ownerId, fullName, "5551234567", "hale@example.com", "acik adres", null, null,
            "İstanbul", "Güngören", null, null, BigDecimal.ZERO, null, null, true, true, true, null,
            LocalDate.of(1990, 1, 1), nationalId
        );
    }

    @Test
    void should_updateFullName_when_ownerExists() {
        UUID ownerId = UUID.randomUUID();
        Owner owner = Owner.register(UUID.randomUUID(), "hale", "5551234567", null, null);
        when(ownerRepository.findById(ownerId)).thenReturn(Optional.of(owner));

        useCase.execute(aCommand(ownerId, "hale Yılmaz"));

        assertThat(owner.getFullName()).isEqualTo("hale Yılmaz");
        verify(ownerRepository).save(owner);
    }

    @Test
    void should_throwNotFound_when_ownerMissing() {
        UUID ownerId = UUID.randomUUID();
        when(ownerRepository.findById(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(aCommand(ownerId, "hale Yılmaz")))
            .isInstanceOf(OwnerNotFoundException.class);
    }

    @Test
    void should_updateBirthDateAndNationalId_when_nationalIdValid() {
        UUID ownerId = UUID.randomUUID();
        Owner owner = Owner.register(UUID.randomUUID(), "hale", "5551234567", null, null);
        when(ownerRepository.findById(ownerId)).thenReturn(Optional.of(owner));

        useCase.execute(aCommand(ownerId, "hale Yılmaz", "10000000146"));

        assertThat(owner.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(owner.getNationalId()).isEqualTo("10000000146");
    }

    @Test
    void should_throwInvalidNationalId_when_checksumWrong() {
        UUID ownerId = UUID.randomUUID();
        Owner owner = Owner.register(UUID.randomUUID(), "hale", "5551234567", null, null);
        when(ownerRepository.findById(ownerId)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> useCase.execute(aCommand(ownerId, "hale Yılmaz", "12345678901")))
            .isInstanceOf(InvalidNationalIdException.class);
    }
}
