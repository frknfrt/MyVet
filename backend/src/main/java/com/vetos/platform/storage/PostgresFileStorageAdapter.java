package com.vetos.platform.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class PostgresFileStorageAdapter implements FileStoragePort {
    private final StoredFileJpaRepository repository;

    @Override
    public String store(byte[] content, String contentType) {
        UUID id = UUID.randomUUID();
        repository.save(StoredFile.create(id, content, contentType));
        return id.toString();
    }

    @Override
    public byte[] retrieve(String storageRef) {
        return repository.findById(UUID.fromString(storageRef))
            .map(StoredFile::getContent)
            .orElseThrow(() -> new StoredFileNotFoundException(storageRef));
    }
}
