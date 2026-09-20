package com.vetos.platform.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface StoredFileJpaRepository extends JpaRepository<StoredFile, UUID> {
}
