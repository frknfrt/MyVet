package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.domain.Breed;
import com.vetos.modules.patient.domain.Patient;
import com.vetos.modules.patient.domain.PatientTarbilProfile;
import com.vetos.modules.patient.domain.Sex;
import com.vetos.modules.patient.domain.Species;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientLookupAdapterTest {

    @Mock private PatientJpaRepository patientJpaRepository;
    @Mock private SpeciesJpaRepository speciesJpaRepository;
    @Mock private BreedJpaRepository breedJpaRepository;

    private PatientLookupAdapter adapter() {
        return new PatientLookupAdapter(patientJpaRepository, speciesJpaRepository, breedJpaRepository);
    }

    @Test
    void should_buildProfileWithSpeciesAndBreedNames_when_patientExists() {
        UUID patientId = UUID.randomUUID();
        UUID speciesId = UUID.randomUUID();
        UUID breedId = UUID.randomUUID();
        Patient patient = mock(Patient.class);
        when(patient.getId()).thenReturn(patientId);
        when(patient.getName()).thenReturn("Pamuk");
        when(patient.getMicrochipNumber()).thenReturn("900123456789012");
        when(patient.getSpeciesId()).thenReturn(speciesId);
        when(patient.getBreedId()).thenReturn(breedId);
        when(patient.getSex()).thenReturn(Sex.FEMALE);
        when(patient.getBirthDate()).thenReturn(LocalDate.of(2023, 5, 1));
        Species species = mock(Species.class);
        when(species.getName()).thenReturn("Kedi");
        Breed breed = mock(Breed.class);
        when(breed.getName()).thenReturn("Van Kedisi");
        when(patientJpaRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(speciesJpaRepository.findById(speciesId)).thenReturn(Optional.of(species));
        when(breedJpaRepository.findById(breedId)).thenReturn(Optional.of(breed));

        Optional<PatientTarbilProfile> profile = adapter().findTarbilProfile(patientId);

        assertThat(profile).contains(new PatientTarbilProfile(
            patientId, "Pamuk", "900123456789012", speciesId, "Kedi", "Van Kedisi", Sex.FEMALE, LocalDate.of(2023, 5, 1)));
    }

    @Test
    void should_returnEmpty_when_patientMissing() {
        UUID patientId = UUID.randomUUID();
        when(patientJpaRepository.findById(patientId)).thenReturn(Optional.empty());

        assertThat(adapter().findTarbilProfile(patientId)).isEmpty();
    }
}
