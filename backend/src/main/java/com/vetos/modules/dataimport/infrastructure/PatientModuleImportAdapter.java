package com.vetos.modules.dataimport.infrastructure;

import com.vetos.modules.dataimport.application.excel.PhoneNormalizer;
import com.vetos.modules.dataimport.domain.ExistingDataLookup;
import com.vetos.modules.dataimport.domain.ImportEntityType;
import com.vetos.modules.dataimport.domain.ImportRecordLinkRepository;
import com.vetos.modules.dataimport.domain.ImportTargetWriter;
import com.vetos.modules.patient.domain.Breed;
import com.vetos.modules.patient.domain.BreedRepository;
import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import com.vetos.modules.patient.domain.Patient;
import com.vetos.modules.patient.domain.PatientRepository;
import com.vetos.modules.patient.domain.Species;
import com.vetos.modules.patient.domain.SpeciesRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * ExistingDataLookup'in patient modulu (turler/irklar) ve owners tablosu uzerindeki uygulamasi.
 * Telefonla sahip arama patient modulune eklenmez: tablo kiraci kosuluyla dogrudan okunur.
 *
 * ImportTargetWriter: kayitlar patient domain fabrika metotlari + repository save ile yazilir.
 * Register*UseCase BILEREK cagrilmaz (PatientRegisteredEvent ve yan etkileri tetiklenmesin).
 */
@Component
@RequiredArgsConstructor
class PatientModuleImportAdapter implements ExistingDataLookup, ImportTargetWriter {

    private final SpeciesRepository speciesRepository;
    private final BreedRepository breedRepository;
    private final OwnerRepository ownerRepository;
    private final PatientRepository patientRepository;
    private final ImportRecordLinkRepository linkRepository;
    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<SpeciesRef> species() {
        return speciesRepository.findAll().stream().map(s -> new SpeciesRef(s.getId(), s.getName())).toList();
    }

    @Override
    public List<BreedRef> breeds(UUID speciesId) {
        return breedRepository.findBySpeciesId(speciesId).stream()
            .map(b -> new BreedRef(b.getId(), b.getSpeciesId(), b.getName())).toList();
    }

    @Override
    public List<OwnerRef> ownersByNormalizedPhones(UUID tenantId, Collection<String> normalizedPhones) {
        if (normalizedPhones == null || normalizedPhones.isEmpty()) {
            return List.of();
        }
        Set<String> wanted = new HashSet<>(normalizedPhones);
        return jdbc.query(
                "SELECT id, full_name, phone FROM owners WHERE tenant_id = :tenantId AND is_anonymous_placeholder = false"
                    + " ORDER BY created_at, id",
                Map.of("tenantId", tenantId),
                (rs, i) -> new OwnerRef(rs.getObject("id", UUID.class), rs.getString("full_name"),
                    rs.getString("phone"), PhoneNormalizer.normalize(rs.getString("phone"))))
            .stream().filter(o -> wanted.contains(o.normalizedPhone())).toList();
    }

    @Override
    public Set<String> alreadyImported(UUID tenantId, String sourceSystem, ImportEntityType type,
                                       Collection<String> sourceIds) {
        return linkRepository.existingSourceIds(tenantId, sourceSystem, type, sourceIds);
    }

    // ---- ImportTargetWriter ----

    @Override
    public UUID createSpecies(String name) {
        return speciesRepository.save(Species.create(name)).getId();
    }

    @Override
    public UUID createBreed(UUID speciesId, String name) {
        return breedRepository.save(Breed.create(speciesId, name)).getId();
    }

    @Override
    public UUID createOwner(UUID tenantId, NewOwner o) {
        Owner owner = Owner.register(tenantId, o.fullName(), o.phone(), o.email(), o.address());
        // updateDetails, TC kimligi updateNationalId ile dogrular; rizalar false (register varsayilani true).
        owner.updateDetails(
            null, o.secondaryPhone(), o.city(), o.district(), null,
            null, BigDecimal.ZERO, null, o.notes(),
            false, false, false, null,
            null, o.nationalId());
        return ownerRepository.save(owner).getId();
    }

    @Override
    public UUID createPatient(UUID tenantId, NewPatient p) {
        Patient patient = Patient.register(
            tenantId, p.ownerId(), p.speciesId(), p.breedId(), p.name(), p.sex(), p.birthDate());
        patient.updateProfile(p.name(), p.breedId(), p.sex(), p.birthDate(), p.neutered());
        patient.updateMicrochip(p.microchipNumber());
        patient.updatePassportNumber(p.passportNumber());
        patient.updateDetails(p.color(), null, null, false, null, null, null, p.notes(), null, null);
        return patientRepository.save(patient).getId();
    }

    /**
     * TcKimlikValidator patient modulunde paket-ozel ve hatasi (InvalidNationalIdException) acik arayuzde degil;
     * kural kopyalanmasin diye kaydedilmeyen bir deneme nesnesi uzerinde Owner'in kendi dogrulamasi calistirilir.
     */
    @Override
    public boolean isValidNationalId(String nationalId) {
        try {
            Owner.register(null, "-", "-", null, null).updateNationalId(nationalId);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
