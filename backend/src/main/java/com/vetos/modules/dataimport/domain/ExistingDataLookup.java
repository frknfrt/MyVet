package com.vetos.modules.dataimport.domain;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Dogrulayicinin klinikteki mevcut veriyi okudugu port. */
public interface ExistingDataLookup {

    List<SpeciesRef> species();

    List<BreedRef> breeds(UUID speciesId);

    /** Telefonu eslesen klinik sahipleri; deterministik sira: created_at, id (ilk kayit once). */
    List<OwnerRef> ownersByNormalizedPhones(UUID tenantId, Collection<String> normalizedPhones);

    Set<String> alreadyImported(UUID tenantId, String sourceSystem, ImportEntityType type, Collection<String> sourceIds);

    record SpeciesRef(UUID id, String name) {
    }

    record BreedRef(UUID id, UUID speciesId, String name) {
    }

    record OwnerRef(UUID id, String fullName, String phone, String normalizedPhone) {
    }
}
