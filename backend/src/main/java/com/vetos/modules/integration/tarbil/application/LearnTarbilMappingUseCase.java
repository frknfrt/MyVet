package com.vetos.modules.integration.tarbil.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.integration.tarbil.domain.TarbilMappingKind;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMapping;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import com.vetos.modules.integration.tarbil.domain.VaccineKeyNormalizer;
import com.vetos.modules.integration.tarbil.domain.exception.InvalidTarbilMappingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LearnTarbilMappingUseCase {

    static final int MAX_FIELDS_JSON_LENGTH = 4096;

    private final TarbilValueMappingRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void execute(UUID tenantId, UUID staffId, TarbilMappingKind kind, String rawKey, String tarbilFieldsJson) {
        String key = kind == TarbilMappingKind.VACCINE
            ? VaccineKeyNormalizer.normalize(rawKey)
            : (rawKey == null ? "" : rawKey.trim());
        if (key.isEmpty()) {
            throw new InvalidTarbilMappingException("anahtar bos");
        }
        if (tarbilFieldsJson == null || tarbilFieldsJson.length() > MAX_FIELDS_JSON_LENGTH) {
            throw new InvalidTarbilMappingException("alanlar bos ya da cok buyuk");
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(tarbilFieldsJson);
        } catch (JsonProcessingException e) {
            throw new InvalidTarbilMappingException("alanlar gecerli JSON degil");
        }
        if (node == null || !node.isObject()) {
            throw new InvalidTarbilMappingException("alanlar bir JSON nesnesi olmali");
        }
        Instant now = Instant.now();
        TarbilValueMapping mapping = repository.findByTenantIdAndKindAndVetlyKey(tenantId, kind, key)
            .map(existing -> {
                existing.update(tarbilFieldsJson, staffId, now);
                return existing;
            })
            .orElseGet(() -> TarbilValueMapping.create(tenantId, kind, key, tarbilFieldsJson, staffId, now));
        repository.save(mapping);
    }
}
