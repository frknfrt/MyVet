package com.vetos.modules.integration.tarbil.domain;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface TarbilDiseaseRepository {
    /** sort_order sirasiyla (kategori, ardindan yapraklari). */
    List<TarbilDisease> findAllOrdered();

    Optional<TarbilDisease> findById(UUID id);
}
