package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.TarbilDiseaseSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilDisease;
import com.vetos.modules.integration.tarbil.domain.TarbilDiseaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** TARBIL hastalik agaci; recete formu ve eklenti eslestirmesi icin "KATEGORI > AD" yolu ile. */
@Service
@RequiredArgsConstructor
public class ListTarbilDiseasesUseCase {

    private final TarbilDiseaseRepository repository;

    @Transactional(readOnly = true)
    public List<TarbilDiseaseSummary> execute() {
        List<TarbilDisease> all = repository.findAllOrdered();
        Map<UUID, TarbilDisease> byId = all.stream().collect(Collectors.toMap(TarbilDisease::getId, Function.identity()));
        return all.stream().map(d -> {
            TarbilDisease parent = d.getParentId() == null ? null : byId.get(d.getParentId());
            String path = parent == null ? d.getName() : parent.getName() + " > " + d.getName();
            return new TarbilDiseaseSummary(d.getId(), d.getParentId(), d.getName(), path, d.isSelectable());
        }).toList();
    }
}
