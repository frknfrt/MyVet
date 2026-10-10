package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilDisease;
import com.vetos.modules.integration.tarbil.domain.TarbilDiseaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Component
@RequiredArgsConstructor
class TarbilDiseaseRepositoryAdapter implements TarbilDiseaseRepository {

    private final TarbilDiseaseJpaRepository jpaRepository;

    @Override
    public List<TarbilDisease> findAllOrdered() {
        return jpaRepository.findAllByOrderBySortOrderAsc();
    }

    @Override
    public Optional<TarbilDisease> findById(UUID id) {
        return jpaRepository.findById(id);
    }
}
